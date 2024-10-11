package com.wingbank.sidecar.model;

import com.wingbank.sidecar.filter.LoggingFilter;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.util.Assert;
import org.springframework.util.MultiValueMap;
import org.springframework.web.server.ServerWebExchange;

import java.net.InetSocketAddress;
import java.util.*;

public class ServerContext {

    private static final Logger LOGGER = LoggerFactory.getLogger(ServerContext.class);

    private static final List<String> IP_HEADER_CANDIDATES = Collections.unmodifiableList(Arrays.asList(
            "X-Forwarded-For", "Proxy-Client-IP", "WL-Proxy-Client-IP",
            "HTTP_X_FORWARDED_FOR", "HTTP_X_FORWARDED",
            "HTTP_X_CLUSTER_CLIENT_IP", "HTTP_CLIENT_IP",
            "HTTP_FORWARDED_FOR", "HTTP_FORWARDED", "HTTP_VIA",
            "REMOTE_ADDR"));

    private final String uuid;
    private String ip;
    private String endpoint;
    private String method;
    private String requestBody;
    private String responseBody;
    private String queryParam;
    private String parameter;
    private String file;
    private HttpHeaders requestHeader;
    private HttpHeaders responseHeader;
    private boolean shouldLog = false;
    private String traceId;

    public ServerContext(ServerWebExchange exchange) {
        Assert.notNull(exchange, "Server web exchange must not be null");
        this.uuid = UUID.randomUUID().toString();
        ServerHttpRequest request = exchange.getRequest();
        exchange.getAttributes().put(LoggingFilter.SERVER_CONTEXT_ATTR, this);
        this.setRequestHeader(request);
        this.setMethod(request.getMethod().name());
        this.setEndpoint(request.getPath().value());
    }

    public String getUuid() {
        return uuid;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public String getFile() {
        return file;
    }

    public void setFile(String file) {
        this.file = file;
    }

    public void setRequestHeader(ServerHttpRequest request) {
        this.requestHeader = request.getHeaders();
        for (String header : IP_HEADER_CANDIDATES) {
            String requestIp = requestHeader.getFirst(header);
            if (StringUtils.isNotEmpty(requestIp) && !"unknown".equalsIgnoreCase(requestIp)) {
                this.ip = requestIp;
                break;
            }
        }
        if (StringUtils.isEmpty(ip) && request.getRemoteAddress() != null)
            this.ip = Optional.of(request).map(ServerHttpRequest::getRemoteAddress)
                    .map(InetSocketAddress::getHostString)
                    .orElse(StringUtils.EMPTY);
    }

    public HttpHeaders getRequestHeader() {
        return requestHeader;
    }

    public void setResponseHeader(ServerHttpResponse response) {
        this.responseHeader = response.getHeaders();
    }

    public HttpHeaders getResponseHeader() {
        return responseHeader;
    }

    public String getEndpoint() {
        return StringUtils.isEmpty(endpoint) ? "unknown endpoint" : endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getQueryParam() {
        return queryParam;
    }

    public void setQueryParam(String queryParam) {
        this.queryParam = queryParam;
    }

    public void appendParameter(String parameter) {
        if (StringUtils.isEmpty(this.parameter))
            this.parameter = parameter;
        else if (StringUtils.isNotEmpty(parameter))
            this.parameter = this.parameter + "&" + parameter;
    }

    public String getRequestBody() {
        try {
            if (StringUtils.isEmpty(requestBody))
                return "";
            else if (requestBody.startsWith("{"))
                return new JSONObject(requestBody).toString();
            else if (requestBody.startsWith("["))
                return new JSONArray(requestBody).toString();
        } catch (Exception e) {
            LOGGER.debug("exception occurred while build log request body method {} {}", method, e.getMessage());
        }
        return requestBody;
    }

    public void setRequestBody(String requestBody) {
        this.requestBody = requestBody;
    }

    public String getResponseBody() {
        try {
            if (StringUtils.isEmpty(responseBody))
                return "";
            else if (responseBody.startsWith("{"))
                return new JSONObject(responseBody).toString();
            else if (responseBody.startsWith("["))
                return new JSONArray(responseBody).toString();
        } catch (Exception e) {
            LOGGER.debug("exception occurred while build log response body {}", e.getMessage());
        }
        return responseBody;
    }

    public void setResponseBody(String responseBody) {
        this.responseBody = responseBody;
    }

    public boolean isShouldLog() {
        return shouldLog;
    }

    public void setShouldLog(boolean shouldLog) {
        this.shouldLog = shouldLog;
    }

    public String buildLogRequest() {
        StringBuilder log = new StringBuilder("REQUEST ");
        log.append(getMethod()).append(" ");
        log.append(getUuid()).append(", ");
        log.append(getEndpoint()).append(", ");
        log.append(getIp());
        String header = buildHeader(requestHeader);
        if (StringUtils.isNotEmpty(header))
            log.append(", ").append(header);
        if (StringUtils.isNotEmpty(file))
            log.append(", files:").append(file);
        if (StringUtils.isNotEmpty(parameter))
            log.append(", parameter:").append(parameter);
        String body = truncateBody(getRequestBody(), -1);
        if (StringUtils.isNotEmpty(body))
            log.append(", body:").append(body);
        return log.toString();
    }

    public String buildLogResponse(HttpStatusCode status) {
        StringBuilder log = new StringBuilder("RESPONSE ");
        log.append(Optional.ofNullable(status).map(HttpStatusCode::value).orElse(-1)).append(" ");
        log.append(getUuid()).append(", ");
        log.append(getEndpoint()).append(", ");
        log.append(getIp());
        String header = buildHeader(responseHeader);
        if (StringUtils.isNotEmpty(header))
            log.append(", ").append(header);
        String body = truncateBody(getResponseBody(), -1);
        if (StringUtils.isNotEmpty(body))
            log.append(", body:").append(body);
        return log.toString();
    }

    public static String buildHeader(MultiValueMap<String, String> headers) {
        if (headers == null || headers.isEmpty()) return null;
        StringBuilder builder = new StringBuilder("headers:");
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            StringBuilder values = new StringBuilder(entry.getKey().toLowerCase()).append("[");
            for (String value : entry.getValue())
                values.append(value).append(",");
            builder.append(values.length() > 0 ? values.substring(0, values.length() - 1) : "").append("]");
        }
        return builder.toString();
    }

    public static String truncateBody(String body, int maxLength) {
        if (StringUtils.isNotEmpty(body) && maxLength > 0 && body.length() > maxLength)
            return body.substring(0, maxLength);
        return body;
    }
}
