package com.jarvis.sidecar.filter;

import com.jarvis.sidecar.condition.ConditionalOnEnvironment;
import com.jarvis.sidecar.model.ServerContext;
import com.jarvis.sidecar.server.AceServerWebExchangeDecorator;
import com.jarvis.sidecar.util.ContextUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.http.codec.multipart.FormFieldPart;
import org.springframework.http.codec.multipart.Part;
import org.springframework.stereotype.Component;
import org.springframework.util.*;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

@Component
@ConditionalOnEnvironment(key = "ENABLE_LOG", havingValue = "true")
public class LoggingFilter implements WebFilter, Ordered {

    private static final String PARAM_FORMAT = "%s=%s&";
    private static final String[] DEFAULT_EXCLUDE_ANT_PATH_MATCH = {
            "/swagger-ui.html", "/v2/api-docs/**", "/swagger/**",
            "/webjars/**", "/v3/api-docs/**", "/swagger-resources/**",
            "/configuration/security/**", "/swagger-ui/index.html",
            "/configuration/ui/**", "/swagger-ui/**",
            "/css/**", "/oauth/check_token", "/identity/oauth/check_token",
            "/js/**", "/favicon.ico", "/actuator/**",
            "/api-docs/service/**", "/image/**", "/scss/**", "/actuator/**", "/"
    };

    public static final String REQUEST_QUERY_PARAM_ATTR = "com.wingbank.sidecar.filter.requestQueryParam";
    public static final String REQUEST_QUERY_PARAM_ENCODE_ATTR = "com.wingbank.sidecar.filter.requestQueryParamEncode";
    public static final String REQUEST_QUERY_PARAM_RAW_ATTR = "com.wingbank.sidecar.filter.requestQueryParamRaw";
    public static final String REQUEST_BODY_ATTR = "com.wingbank.sidecar.filter.requestBody";
    public static final String RESPONSE_BODY_ATTR = "com.wingbank.sidecar.filter.responseBody";
    public static final String SERVER_CONTEXT_ATTR = "com.wingbank.sidecar.filter.serverContext";

    @Autowired
    private ServerCodecConfigurer configurer;

    private static final PathMatcher MATCHER = new AntPathMatcher();

    private static final Logger LOGGER = LoggerFactory.getLogger(LoggingFilter.class);

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerContext context = new ServerContext(exchange);
        context.setTraceId(ContextUtil.getTraceId());
        context.setShouldLog(shouldLog(exchange));
        context.setQueryParam(getQueryParam(exchange));
        exchange.getResponse().getHeaders().set("trace-id", context.getTraceId());
        return decorate(exchange, context).flatMap(v -> {
            if (context.isShouldLog())
                LOGGER.info(context.buildLogRequest());
            return chain.filter(buildQueryParamURI(v, context));
        }).doFinally(signal -> {
            context.setResponseHeader(exchange.getResponse());
            if (context.isShouldLog())
                LOGGER.info(context.buildLogResponse(exchange.getResponse().getStatusCode()));
            LOGGER.debug("SignalType {}", signal);
            if (SignalType.CANCEL.equals(signal))
                LOGGER.warn("client {} request '{}'", signal, context.getEndpoint());
        });
    }

    private Mono<ServerWebExchange> decorate(ServerWebExchange exchange, ServerContext context) {
        return DataBufferUtils.join(exchange.getRequest().getBody()).map(buffer -> {
            LOGGER.debug("read request body");
            byte[] bytes = new byte[buffer.readableByteCount()];
            buffer.read(bytes);
            DataBufferUtils.release(buffer);
            return bytes;
        }).defaultIfEmpty(new byte[]{}).flatMap(bytes -> {
            AceServerWebExchangeDecorator decorator = new AceServerWebExchangeDecorator(exchange, configurer, bytes);
            MediaType contentType = decorator.getRequest().getHeaders().getContentType();
            String body = new String(bytes, StandardCharsets.UTF_8);
            if (MediaType.APPLICATION_JSON.isCompatibleWith(contentType)) {
                context.setRequestBody(body);
                decorator.getAttributes().put(REQUEST_BODY_ATTR, body);
            } else if (MediaType.APPLICATION_FORM_URLENCODED.isCompatibleWith(contentType)) {
                context.appendParameter(body);
                decorator.getAttributes().put(REQUEST_QUERY_PARAM_ATTR, buildParameter(body));
            } else if (MediaType.MULTIPART_FORM_DATA.isCompatibleWith(contentType)) {
                LOGGER.debug("read multipart form data request");
                return decorateMultipartData(decorator, context);
            }
            return Mono.just(decorator);
        });
    }

    private Mono<ServerWebExchange> decorateMultipartData(AceServerWebExchangeDecorator decorator, ServerContext context) {
        return decorator.getMultipartData().doOnNext(v -> {
            List<Part> parts = v.values().parallelStream().flatMap(Collection::stream).collect(Collectors.toList());
            List<String> files = new ArrayList<>();
            StringBuilder builder = new StringBuilder();
            for (Part part : parts) {
                if (part instanceof FilePart)
                    files.add(((FilePart) part).filename());
                else if (part instanceof FormFieldPart)
                    builder.append(String.format(PARAM_FORMAT, part.name(), ((FormFieldPart) part).value()));
            }
            String string = builder.toString();
            string = string.substring(0, string.length() - 1);
            context.appendParameter(string);
            context.setFile(files.isEmpty() ? null : files.toString());
        }).thenReturn(decorator);
    }

    private ServerWebExchange buildQueryParamURI(ServerWebExchange exchange, ServerContext context) {
        if (StringUtils.hasText(exchange.getRequest().getURI().getRawQuery()) && StringUtils.hasText(context.getQueryParam())) {
            try {
                String originalURI = exchange.getRequest().getURI().toString();
                String url = originalURI.split("\\?")[0];
                String newUrl = String.format("%s?%s", url, context.getQueryParam());
                return exchange.mutate().request(exchange.getRequest().mutate().uri(new URI(newUrl)).build()).build();
            } catch (URISyntaxException e) {
                LOGGER.warn("exception occurred while rebuild query param URI {}", e.getMessage());
            }
        }
        return exchange;
    }

    private boolean shouldLog(ServerWebExchange exchange) {
        Set<String> excludes = new HashSet<>(Arrays.asList(DEFAULT_EXCLUDE_ANT_PATH_MATCH));
        return excludes.parallelStream().noneMatch(v -> MATCHER.match(v, exchange.getRequest().getPath().value()));
    }

    private String getQueryParam(ServerWebExchange exchange) {
        MultiValueMap<String, String> param = exchange.getRequest().getQueryParams();
        if (param.isEmpty()) return "";
        StringBuilder raw = new StringBuilder();
        StringBuilder encode = new StringBuilder();
        for (Map.Entry<String, List<String>> entry : param.entrySet()) {
            for (String value : entry.getValue()) {
                if (StringUtils.hasText(value)) {
                    raw.append(String.format(PARAM_FORMAT, entry.getKey(), value));
                    encode.append(String.format(PARAM_FORMAT, entry.getKey(),
                            URLEncoder.encode(value, StandardCharsets.UTF_8)));
                }
            }
        }
        String rawQueryParam = raw.toString().substring(0, raw.toString().length() - 1);
        String queryParam = encode.toString().substring(0, encode.toString().length() - 1);
        exchange.getAttributes().put(REQUEST_QUERY_PARAM_RAW_ATTR, rawQueryParam);
        exchange.getAttributes().put(REQUEST_QUERY_PARAM_ENCODE_ATTR, queryParam);
        exchange.getAttributes().put(REQUEST_QUERY_PARAM_ATTR, param);
        Optional.ofNullable((ServerContext) exchange.getAttribute(SERVER_CONTEXT_ATTR)).ifPresent(v -> v.appendParameter(rawQueryParam));
        return queryParam;
    }

    private MultiValueMap<String, String> buildParameter(String body) {
        MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
        if (StringUtils.hasText(body)) {
            String[] params = body.split("&");
            for (String param : params) {
                if (param.contains("=")) {
                    String[] values = param.split("=");
                    if (values.length <= 1) continue;
                    List<String> list = map.computeIfAbsent(values[0], p -> new ArrayList<>());
                    list.add(values[1]);
                }
            }
        }
        return map;
    }
}
