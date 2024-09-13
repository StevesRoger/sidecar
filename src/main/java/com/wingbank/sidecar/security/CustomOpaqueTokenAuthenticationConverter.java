package com.wingbank.sidecar.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.core.OAuth2TokenIntrospectionClaimAccessor;
import org.springframework.security.oauth2.server.resource.introspection.OAuth2IntrospectionAuthenticatedPrincipal;
import org.springframework.util.Assert;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.stream.Collectors;

public class CustomOpaqueTokenAuthenticationConverter implements Converter<OAuth2TokenIntrospectionClaimAccessor, Mono<? extends OAuth2AuthenticatedPrincipal>> {

    private static final Logger LOG = LoggerFactory.getLogger(CustomOpaqueTokenAuthenticationConverter.class);

    private final String roleClaim;

    public CustomOpaqueTokenAuthenticationConverter() {
        this("roles");
    }

    public CustomOpaqueTokenAuthenticationConverter(String roleClaim) {
        Assert.hasText(roleClaim, "role claim cannot be null or empty");
        this.roleClaim = roleClaim;
        LOG.info("role claim name '{}'", roleClaim);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Mono<? extends OAuth2AuthenticatedPrincipal> convert(OAuth2TokenIntrospectionClaimAccessor source) {
        Map<String, Object> claims = source.getClaims();
        Object claimValue = claims.getOrDefault(roleClaim, new HashSet<>());
        LOG.debug("role claim name '{}', {}", roleClaim, claimValue);
        Collection<GrantedAuthority> roles = new HashSet<>();
        if (claimValue instanceof Collection) {
            roles = ((Collection<String>) claimValue).parallelStream()
                    .map(SimpleGrantedAuthority::new).collect(Collectors.toSet());
        }
        LOG.info("roles {}", roles);
        return Mono.just(new OAuth2IntrospectionAuthenticatedPrincipal(claims, roles));
    }
}
