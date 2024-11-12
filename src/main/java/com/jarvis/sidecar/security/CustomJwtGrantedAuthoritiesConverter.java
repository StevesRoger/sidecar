package com.jarvis.sidecar.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.util.Collection;
import java.util.stream.Collectors;

public class CustomJwtGrantedAuthoritiesConverter implements Converter<Jwt, Flux<GrantedAuthority>> {

    private static final Logger LOG = LoggerFactory.getLogger(CustomJwtGrantedAuthoritiesConverter.class);

    private final String roleClaim;

    public CustomJwtGrantedAuthoritiesConverter() {
        this("roles");
    }

    public CustomJwtGrantedAuthoritiesConverter(String roleClaim) {
        Assert.hasText(roleClaim, "role claim cannot be null or empty");
        this.roleClaim = roleClaim;
        LOG.info("role claim name '{}'", roleClaim);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Flux<GrantedAuthority> convert(Jwt jwt) {
        Collection<GrantedAuthority> roles;
        Object claimValue = jwt.getClaim(roleClaim);
        LOG.debug("role claim name '{}', {}", roleClaim, claimValue);
        if (claimValue instanceof Collection)
            roles = ((Collection<String>) claimValue).parallelStream()
                    .map(SimpleGrantedAuthority::new).collect(Collectors.toSet());
        else
            roles = StringUtils.commaDelimitedListToSet(String.valueOf(claimValue)).parallelStream()
                    .map(SimpleGrantedAuthority::new).collect(Collectors.toSet());
        LOG.info("roles {}", roles);
        return Flux.fromIterable(roles);
    }
}
