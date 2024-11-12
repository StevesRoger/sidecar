package com.jarvis.sidecar.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarvis.sidecar.model.SecurityEntity;
import com.jarvis.sidecar.util.ContextUtil;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.stereotype.Service;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.CollectionUtils;
import org.springframework.util.PathMatcher;
import org.springframework.web.server.ServerWebExchange;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.*;
import java.util.function.Predicate;

@Service
public class SecurityResourceService {

    private static final Logger LOG = LoggerFactory.getLogger(SecurityResourceService.class);
    private static final String CONFIG_ENV = "SECURITY_CONFIG";
    private static final PathMatcher MATCHER = new AntPathMatcher();

    private final Collection<SecurityEntity> securities = new HashSet<>();

    public SecurityResourceService() {
        add(loadSecurityEntity());
    }

    public void add(SecurityEntity security) {
        if (security != null) this.securities.add(security);
    }

    public void add(Collection<SecurityEntity> securities) {
        if (securities != null && !securities.isEmpty())
            this.securities.addAll(securities);
    }

    public Collection<SecurityEntity> getSecurities() {
        return new HashSet<>(securities);
    }

    public boolean isPermit(ServerWebExchange exchange) {
        return isPermit(exchange.getRequest().getPath().value(), exchange.getRequest().getMethod().name());
    }

    public boolean isPermit(String url, String method) {
        return match(url, method, e -> Boolean.TRUE.equals(e.getPermitAll()));
    }

    public boolean isDeny(ServerWebExchange exchange) {
        return isDeny(exchange.getRequest().getPath().value(), exchange.getRequest().getMethod().name());
    }

    public boolean isDeny(String url, String method) {
        return match(url, method, e -> Boolean.TRUE.equals(e.getDenyAll()));
    }

    private boolean match(String url, String method, Predicate<SecurityEntity> predicate) {
        for (SecurityEntity entity : securities) {
            for (String endpoint : entity.getEndpoints()) {
                if (MATCHER.match(endpoint, url)) {
                    if (CollectionUtils.isEmpty(entity.getMethods()) || StringUtils.isEmpty(method))
                        return predicate.test(entity);
                    return predicate.test(entity) && entity.getMethods().parallelStream()
                            .anyMatch(v -> v.equalsIgnoreCase(method));
                }
            }
        }
        return false;
    }

    public ServerHttpSecurity.AuthorizeExchangeSpec applyAntPatterns(ServerHttpSecurity.AuthorizeExchangeSpec spec) {
        if (securities.isEmpty()) return spec.pathMatchers("/**").authenticated();
        for (SecurityEntity entity : securities) {
            String[] endpoints = entity.getEndpoints().toArray(new String[]{});
            if (CollectionUtils.isEmpty(entity.getMethods())) {
                if (Boolean.TRUE.equals(entity.getDenyAll())) {
                    LOG.info("deny all {}", endpoints);
                    spec.pathMatchers(endpoints).denyAll();
                } else if (Boolean.TRUE.equals(entity.getPermitAll())) {
                    LOG.info("permit all {}", endpoints);
                    spec.pathMatchers(endpoints).permitAll();
                } else if (Boolean.TRUE.equals(entity.getAuthenticated())) {
                    LOG.info("secure {} required authenticated", endpoints);
                    spec.pathMatchers(endpoints).authenticated();
                } else {
                    String[] roles = entity.getRoles().toArray(new String[]{});
                    LOG.info("secure {}, has access role {}", endpoints, roles);
                    spec.pathMatchers(endpoints).hasAnyAuthority(roles);
                }
            } else {
                for (String method : entity.getMethods()) {
                    HttpMethod httpMethod = HttpMethod.valueOf(method);
                    if (Boolean.TRUE.equals(entity.getDenyAll())) {
                        LOG.info("deny all {} {}", method, endpoints);
                        spec.pathMatchers(httpMethod, endpoints).denyAll();
                    } else if (Boolean.TRUE.equals(entity.getPermitAll())) {
                        LOG.info("permit all {} {}", method, endpoints);
                        spec.pathMatchers(httpMethod, endpoints).permitAll();
                    } else if (Boolean.TRUE.equals(entity.getAuthenticated())) {
                        LOG.info("secure {} {} required authenticated", method, endpoints);
                        spec.pathMatchers(httpMethod, endpoints).authenticated();
                    } else {
                        String[] roles = entity.getRoles().toArray(new String[]{});
                        LOG.info("secure {} {}, has access role {}", method, endpoints, roles);
                        spec.pathMatchers(httpMethod, endpoints).hasAnyAuthority(roles);
                    }
                }
            }
        }
        return spec;
    }

    private Collection<SecurityEntity> loadSecurityEntity() {
        try {
            List<SecurityEntity> list = new ArrayList<>();
            String configPath = System.getenv(CONFIG_ENV);
            if (StringUtils.isNotEmpty(configPath)) {
                LOG.info("security config {}={}", CONFIG_ENV, configPath);
                File file = new File(configPath);
                if (!file.exists() || file.isDirectory())
                    throw new FileNotFoundException("file '" + configPath + "' not exist");
                LOG.info("load security config file {}", configPath);
                list = ContextUtil.getBean(ObjectMapper.class).readValue(new FileInputStream(file), new TypeReference<>() {
                });
            }
            list.sort(Comparator.comparingInt(SecurityEntity::getOrder));
            return list;
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
