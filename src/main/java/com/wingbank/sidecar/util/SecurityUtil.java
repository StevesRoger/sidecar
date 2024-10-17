package com.wingbank.sidecar.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wingbank.sidecar.model.SecurityEntity;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.util.CollectionUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

public final class SecurityUtil {

    private static final Logger LOG = LoggerFactory.getLogger(SecurityUtil.class);

    private static final String CONFIG_ENV = "SECURITY_CONFIG";

    private SecurityUtil() {
    }

    public static ServerHttpSecurity.AuthorizeExchangeSpec applyAntPatterns(ServerHttpSecurity.AuthorizeExchangeSpec spec) {
        Collection<SecurityEntity> securityEntities = loadSecurityEntity();
        if (securityEntities.isEmpty()) return spec.pathMatchers("/**").authenticated();
        for (SecurityEntity entity : securityEntities) {
            if (CollectionUtils.isEmpty(entity.getMethods())) {
                String[] endpoints = entity.getEndpoints().toArray(new String[]{});
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
                    String[] endpoints = entity.getEndpoints().toArray(new String[]{});
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

    private static Collection<SecurityEntity> loadSecurityEntity() {
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
