package com.jarvis.sidecar.filter;

import com.jarvis.sidecar.condition.ConditionalOnEnvironment;
import com.jarvis.sidecar.service.SecurityResourceService;
import com.jarvis.sidecar.util.ContextUtil;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.PathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.*;

@Component
@ConditionalOnEnvironment(key = "ENABLE_SERVICE_SCOPE", havingValue = "true")
public class ServiceAccessFilter implements WebFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger(ServiceAccessFilter.class);

    public static final List<String> DEFAULT_EXCLUDE_ANT_PATH = List.of(
            "/actuator/**", "/index.html", "/index", "/favicon.ico",
            "/swagger/**", "/swagger-ui/**", "/swagger-ui.html", "/swagger-ui/index.html",
            "/swagger-resources/**", "/api-docs/service/**",
            "/v2/api-docs/**", "/v3/api-docs/**", "/webjars/**",
            "/configuration/security/**", "/configuration/ui/**",
            "/image/**", "/scss/**", "/css/**", "/js/**", "/zipkin/**", "/k8s/registry/list");

    private static final Authentication ANONYMOUS = new AnonymousAuthenticationToken("key", "anonymous",
            AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));
    private static final PathMatcher MATCHER = new AntPathMatcher();

    private final String serviceId;
    private final String serviceClaim;
    private final SecurityResourceService securityResource;

    public ServiceAccessFilter(SecurityResourceService securityResource) {
        final String envServiceId = System.getenv("SERVICE_ID");
        this.serviceId = StringUtils.hasText(envServiceId) ? envServiceId : ContextUtil.getAppName();
        this.securityResource = securityResource;
        this.serviceClaim = Optional.ofNullable(System.getenv("SERVICE_CLAIM")).orElse("services");
        LOGGER.info("service claim name '{}'", serviceClaim);
        LOGGER.info("secure access scope service '{}'", serviceId);
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (securityResource.isDeny(exchange))
            return accessDenied(exchange);
        if (securityResource.isPermit(exchange) || DEFAULT_EXCLUDE_ANT_PATH.parallelStream().anyMatch(v ->
                MATCHER.match(v, exchange.getRequest().getPath().value())))
            return chain.filter(exchange);
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .defaultIfEmpty(ANONYMOUS)
                .flatMap(v -> check(exchange, chain, v));
    }

    @SuppressWarnings("unchecked")
    private Mono<Void> check(ServerWebExchange exchange, WebFilterChain chain, Authentication auth) {
        Map<String, Object> tokenAttributes = new HashMap<>();
        if (auth instanceof AnonymousAuthenticationToken)
            return unauthorized(exchange);
        else if (auth instanceof BearerTokenAuthentication)
            tokenAttributes = ((BearerTokenAuthentication) auth).getTokenAttributes();
        else if (auth instanceof JwtAuthenticationToken)
            tokenAttributes = ((JwtAuthenticationToken) auth).getTokenAttributes();
        Object claimValue = tokenAttributes.getOrDefault(serviceClaim, new HashSet<>());
        LOGGER.debug("service claim name '{}', {}", serviceClaim, claimValue);
        Collection<String> services;
        if (claimValue instanceof Collection)
            services = (Collection<String>) claimValue;
        else
            services = StringUtils.commaDelimitedListToSet(String.valueOf(claimValue));
        LOGGER.info("services {}", services);
        return services.contains(serviceId) ? chain.filter(exchange) : accessDenied(exchange);
    }

    private Mono<Void> accessDenied(ServerWebExchange exchange) {
        return rejectRequest(exchange, "access denied " + serviceId, "A403", HttpStatus.FORBIDDEN);
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        return rejectRequest(exchange, "unauthorized access " + serviceId, "A401", HttpStatus.UNAUTHORIZED);
    }

    private Mono<Void> rejectRequest(ServerWebExchange exchange, String message, String code, HttpStatus status) {
        LOGGER.debug("{}", message);
        JSONObject body = new JSONObject();
        body.put("result", false);
        body.put("result_code", code);
        body.put("result_message", message);
        body.put("trace_id", ContextUtil.getTraceId());
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        DataBuffer buffer = response.bufferFactory().wrap(body.toString().getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }
}
