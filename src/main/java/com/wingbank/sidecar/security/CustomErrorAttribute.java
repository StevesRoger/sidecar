package com.wingbank.sidecar.security;

import com.wingbank.sidecar.exception.AccessTokenFailedException;
import com.wingbank.sidecar.exception.ProxyFailedException;
import com.wingbank.sidecar.exception.ProxyResponseFailedException;
import com.wingbank.sidecar.util.ContextUtil;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.web.reactive.error.DefaultErrorAttributes;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.server.ServerRequest;

import java.net.ConnectException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class CustomErrorAttribute extends DefaultErrorAttributes {

    private static final Logger LOG = LoggerFactory.getLogger(CustomErrorAttribute.class);

    @Override
    public Map<String, Object> getErrorAttributes(ServerRequest request, ErrorAttributeOptions options) {
        String serviceId = Optional.ofNullable(System.getenv("SERVICE_ID")).orElse(ContextUtil.getAppName());
        Map<String, Object> attributes = super.getErrorAttributes(request, options);
        Throwable error = getError(request);
        Throwable cause = ExceptionUtils.getRootCause(error);
        if (cause instanceof ProxyResponseFailedException) {
            String response = ((ProxyResponseFailedException) cause).getResponse();
            if (StringUtils.hasText(response) && response.startsWith("{"))
                return new JSONObject(response).toMap();
        }
        HttpStatus status = HttpStatus.valueOf((Integer) attributes.getOrDefault("status", 400));
        Object message = error.getMessage();
        if (cause instanceof ConnectException) {
            if (error instanceof ProxyFailedException)
                message = "connection refused to " + serviceId;
            else if (error instanceof AccessTokenFailedException)
                message = "connection refused cannot validate access token, " + serviceId;
        } else if (HttpStatus.NOT_FOUND == status)
            message = String.format("API %s not found", attributes.get("path"));
        if (cause instanceof JwtValidationException && cause.getMessage().contains("expired"))
            message = "token expired";
        LOG.info("{}", message);
        Map<String, Object> map = new HashMap<>();
        map.put("trace_id", ContextUtil.getTraceId());
        map.put("result", false);
        map.put("result_code", String.valueOf(status.value()));
        map.put("result_message", message);
        return map;
    }


}
