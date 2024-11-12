package com.jarvis.sidecar.security;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.proc.DefaultJOSEObjectTypeVerifier;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.ReactiveAuthenticationManagerResolver;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtReactiveAuthenticationManager;
import org.springframework.security.oauth2.server.resource.authentication.OpaqueTokenReactiveAuthenticationManager;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.introspection.SpringReactiveOpaqueTokenIntrospector;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import javax.net.ssl.SSLException;
import java.util.Optional;
import java.util.regex.Pattern;

public class CustomAuthenticationManagerResolver implements ReactiveAuthenticationManagerResolver<ServerWebExchange> {

    private static final String JWT_PATTERN = "^[A-Za-z0-9-_=]+\\.[A-Za-z0-9-_=]+\\.[A-Za-z0-9-_.+/=]*$";

    private static final Logger LOG = LoggerFactory.getLogger(CustomAuthenticationManagerResolver.class);

    private String jwkSetUri;
    private String introspectionUri;
    private String clientId;
    private String clientSecret;
    private ReactiveAuthenticationManager jwtAuthenticationManager;
    private ReactiveAuthenticationManager defaultAuthenticationManager;

    public CustomAuthenticationManagerResolver(String jwkSetUri, String introspectionUri, String clientId, String clientSecret) {
        this.jwkSetUri = jwkSetUri;
        Assert.hasText(jwkSetUri, "Jwk set uri cannot be empty or null");
        this.introspectionUri = introspectionUri;
        Assert.hasText(introspectionUri, "Introspection uri cannot be empty or null");
        this.clientId = clientId;
        Assert.hasText(clientId, "Client id cannot be empty or null");
        this.clientSecret = clientSecret;
        Assert.hasText(clientSecret, "Client secret cannot be empty or null");
        setJwtWebClient(webClient(false));
        setOpaqueWebClient(webClient(true));
        LOG.debug("jwkSetUri '{}', introspectionUri '{}', client id '{}', client secret '{}'", jwkSetUri, introspectionUri, clientId, clientSecret);
    }

    public void setJwtWebClient(WebClient jwtWebClient) {
        Assert.notNull(jwtWebClient, "Web client cannot be null");
        this.jwtAuthenticationManager = jwt(jwtWebClient);
    }

    public void setOpaqueWebClient(WebClient opaqueWebClient) {
        Assert.notNull(opaqueWebClient, "Web client cannot be null");
        this.defaultAuthenticationManager = opaque(opaqueWebClient);
    }

    @Override
    public Mono<ReactiveAuthenticationManager> resolve(ServerWebExchange exchange) {
        if (isJwtToken(exchange.getRequest())) {
            LOG.info("invoke jwt authentication manager");
            return Mono.just(jwtAuthenticationManager);
        }
        LOG.info("invoke opaque authentication manager");
        return Mono.just(defaultAuthenticationManager);
    }

    private boolean isJwtToken(ServerHttpRequest request) {
        String token = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (StringUtils.hasText(token) && token.startsWith("Bearer "))
            token = token.substring(7);
        return StringUtils.hasText(token) && Pattern.matches(JWT_PATTERN, token);
    }

    private ReactiveAuthenticationManager jwt(WebClient jwtWebClient) {
        String roleClaim = Optional.ofNullable(System.getenv("ROLE_CLAIM")).orElse("roles");
        NimbusReactiveJwtDecoder.JwkSetUriReactiveJwtDecoderBuilder decoderBuilder = NimbusReactiveJwtDecoder.withJwkSetUri(jwkSetUri)
                .jwtProcessorCustomizer(customizer -> customizer
                        .setJWSTypeVerifier(new DefaultJOSEObjectTypeVerifier<>(new JOSEObjectType("at+jwt"))));
        if (jwtWebClient != null) decoderBuilder.webClient(jwtWebClient);
        ReactiveJwtAuthenticationConverter converter = new ReactiveJwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new CustomJwtGrantedAuthoritiesConverter(roleClaim));
        JwtReactiveAuthenticationManager manager = new JwtReactiveAuthenticationManager(decoderBuilder.build());
        manager.setJwtAuthenticationConverter(converter);
        return new DelegateReactiveAuthenticationManager(manager);
    }

    private ReactiveAuthenticationManager opaque(WebClient opaqueWebClient) {
        String roleClaim = Optional.ofNullable(System.getenv("ROLE_CLAIM")).orElse("roles");
        SpringReactiveOpaqueTokenIntrospector introspector = new SpringReactiveOpaqueTokenIntrospector(introspectionUri, clientId, clientSecret);
        if (opaqueWebClient != null)
            introspector = new SpringReactiveOpaqueTokenIntrospector(introspectionUri, opaqueWebClient);
        introspector.setAuthenticationConverter(new CustomOpaqueTokenAuthenticationConverter(roleClaim));
        return new DelegateReactiveAuthenticationManager(new OpaqueTokenReactiveAuthenticationManager(introspector));
    }

    private WebClient webClient(boolean opaque) {
        try {
            SslContext sslContext = SslContextBuilder.forClient().trustManager(InsecureTrustManagerFactory.INSTANCE).build();
            HttpClient httpClient = HttpClient.create().secure(t -> t.sslContext(sslContext));
            WebClient.Builder builder = WebClient.builder();
            if (opaque) builder.defaultHeaders(h -> h.setBasicAuth(clientId, clientSecret));
            return builder.clientConnector(new ReactorClientHttpConnector(httpClient)).build();
        } catch (SSLException e) {
            throw new IllegalStateException(e);
        }
    }
}
