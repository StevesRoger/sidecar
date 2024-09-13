package com.wingbank.sidecar.config;

import com.wingbank.sidecar.filter.ServiceAccessFilter;
import com.wingbank.sidecar.security.CustomAccessDeniedHandler;
import com.wingbank.sidecar.security.CustomAuthenticationEntryPoint;
import com.wingbank.sidecar.security.CustomAuthenticationManagerResolver;
import com.wingbank.sidecar.util.SecurityUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Value("${SERVICE_ID:${spring.application.name}}")
    private String serviceId;

    @Value("${JWK_SET_URI:https://localhost:9443/oauth2/jwks}")
    private String jwkSetUri;

    @Value("${INTROSPECT_TOKEN_URI:https://localhost:9443/oauth2/introspect}")
    private String introspectionUri;

    @Value("${CLIENT_ID:admin}")
    private String clientId;

    @Value("${CLIENT_SECRET:admin}")
    private String clientSecret;

    @Bean
    public SecurityWebFilterChain filterChain(ServerHttpSecurity http) {
        return http.csrf(ServerHttpSecurity.CsrfSpec::disable)
                .anonymous(ServerHttpSecurity.AnonymousSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .logout(ServerHttpSecurity.LogoutSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .addFilterAfter(new ServiceAccessFilter(serviceId), SecurityWebFiltersOrder.AUTHENTICATION)
                .authorizeExchange(spec ->
                        SecurityUtil.applyAntPatterns(spec)
                                .pathMatchers(HttpMethod.DELETE).denyAll()
                                .pathMatchers(HttpMethod.PUT).denyAll()
                                .pathMatchers(HttpMethod.PATCH).denyAll()
                                .pathMatchers(HttpMethod.HEAD).denyAll()
                                .pathMatchers(HttpMethod.TRACE).denyAll()
                                .pathMatchers(ServiceAccessFilter.DEFAULT_EXCLUDE_ANT_PATH.toArray(new String[]{})).permitAll()
                                .anyExchange().denyAll()
                ).oauth2ResourceServer(spec -> spec.accessDeniedHandler(new CustomAccessDeniedHandler())
                        .authenticationEntryPoint(new CustomAuthenticationEntryPoint())
                        .authenticationManagerResolver(new CustomAuthenticationManagerResolver(jwkSetUri, introspectionUri, clientId, clientSecret)))
                .requestCache(ServerHttpSecurity.RequestCacheSpec::disable).build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", new CorsConfiguration().applyPermitDefaultValues());
        return source;
    }
}
