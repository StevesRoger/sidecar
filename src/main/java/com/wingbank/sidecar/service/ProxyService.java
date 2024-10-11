package com.wingbank.sidecar.service;

import com.wingbank.sidecar.exception.ProxyFailedException;
import com.wingbank.sidecar.exception.ProxyResponseFailedException;
import com.wingbank.sidecar.util.ContextUtil;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.Optional;
import java.util.Set;

@Service
public class ProxyService {

    private static final Logger LOG = LoggerFactory.getLogger(ProxyService.class);

    @Value("${PC-SCHEMA:http}")
    private String scheme;
    @Value("${PC-HOST:localhost}")
    private String host;
    @Value("${PC-PORT:80}")
    private String port;

    @RateLimiter(name = "sidecar", fallbackMethod = "rateLimit")
    @CircuitBreaker(name = "sidecar", fallbackMethod = "circuitBreaker")
    public Mono<ResponseEntity<String>> proxy(ServerHttpRequest request) {
        if (requiredBody(request.getMethod())) {
            return WebClient.create()
                    .method(request.getMethod())
                    .uri(buildUrl(request))
                    .headers(headers -> headers.addAll(extractHeader(request.getHeaders())))
                    .body(BodyInserters.fromDataBuffers(request.getBody()))
                    .retrieve().toEntity(String.class)
                    .onErrorMap(Exception.class, ex -> new ProxyFailedException(ex.getMessage()))
                    .onErrorMap(WebClientResponseException.class, ex ->
                            new ProxyResponseFailedException(ex.getStatusCode(), ex.getMessage(), ex.getResponseBodyAsString()));
        }
        return WebClient.create()
                .method(request.getMethod())
                .uri(buildUrl(request))
                .headers(headers -> headers.addAll(extractHeader(request.getHeaders())))
                .retrieve().toEntity(String.class)
                .onErrorMap(Exception.class, ex -> new ProxyFailedException(ex.getMessage()))
                .onErrorMap(WebClientResponseException.class, ex ->
                        new ProxyResponseFailedException(ex.getStatusCode(), ex.getMessage(), ex.getResponseBodyAsString()));
    }

    private URI buildUrl(ServerHttpRequest request) {
        URI uri = UriComponentsBuilder.newInstance()
                .scheme(scheme).host(host).port(port)
                .path(request.getPath().toString())
                .queryParams(request.getQueryParams())
                .fragment(request.getURI().getFragment())
                .build().toUri();
        LOG.info("forward to {} '{}'", request.getMethod(), uri);
        return uri;
    }

    private HttpHeaders extractHeader(HttpHeaders original) {
        final Set<String> headerClaims = StringUtils.commaDelimitedListToSet(System.getenv("HEADER_CLAIM"));
        final HttpHeaders headers = new HttpHeaders(original);
        final Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken) {
            final JwtAuthenticationToken jwtAuth = (JwtAuthenticationToken) auth;
            final Jwt token = jwtAuth.getToken();
            headers.add("user-id", Optional.ofNullable(token.getClaim("aud")).orElse("-1").toString());
            headers.add("client-id", Optional.ofNullable(token.getClaim("client_id")).orElse("-1").toString());
        } else if (auth instanceof BearerTokenAuthentication) {
            BearerTokenAuthentication opaqueAuth = (BearerTokenAuthentication) auth;
            headers.add("user-id", Optional.ofNullable(opaqueAuth.getTokenAttributes().get("aud")).orElse("-1").toString());
            headers.add("client-id", Optional.ofNullable(opaqueAuth.getTokenAttributes().get("client_id")).orElse("-1").toString());
        }
        LOG.debug("header claim, {}", headerClaims);
        return headers;
    }

    private boolean requiredBody(HttpMethod method) {
        return method == HttpMethod.DELETE || method == HttpMethod.POST || method == HttpMethod.PUT;
    }

    private Mono<ResponseEntity<String>> rateLimit(ServerHttpRequest request, RequestNotPermitted requestNotPermitted) {
        LOG.warn("reached API request limitation '{}', {}", request.getPath().value(), requestNotPermitted.getMessage());
        String serviceId = Optional.ofNullable(System.getenv("SERVICE_ID")).orElse(ContextUtil.getAppName());
        JSONObject json = new JSONObject();
        json.put("trace_id", ContextUtil.getTraceId());
        json.put("result", false);
        json.put("result_code", String.valueOf(HttpStatus.TOO_MANY_REQUESTS.value()));
        json.put("result_message", "you have reached your API request limitation, " + serviceId);
        return Mono.just(ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(json.toString()));
    }

    private Mono<ResponseEntity<String>> circuitBreaker(ServerHttpRequest request, Throwable ex) {
        LOG.warn("'{}', {}", request.getPath().value(), ex.getMessage());
        String serviceId = Optional.ofNullable(System.getenv("SERVICE_ID")).orElse(ContextUtil.getAppName());
        JSONObject json = new JSONObject();
        json.put("trace_id", ContextUtil.getTraceId());
        json.put("result", false);
        json.put("result_code", String.valueOf(HttpStatus.SERVICE_UNAVAILABLE.value()));
        json.put("result_message", "service " + serviceId + " unavailable");
        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(json.toString()));
    }
}
