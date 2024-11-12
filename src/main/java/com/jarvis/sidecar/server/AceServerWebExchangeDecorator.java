package com.jarvis.sidecar.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ResolvableType;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.http.codec.HttpMessageReader;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.http.codec.multipart.Part;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.util.CollectionUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.ServerWebExchangeDecorator;
import reactor.core.publisher.Mono;

import java.util.Collections;

public class AceServerWebExchangeDecorator extends ServerWebExchangeDecorator {

    private static final Logger LOGGER = LoggerFactory.getLogger(AceServerWebExchangeDecorator.class);

    private static final Mono<MultiValueMap<String, String>> EMPTY_FORM_DATA = Mono.just(CollectionUtils.unmodifiableMultiValueMap(new LinkedMultiValueMap<String, String>(0))).cache();
    private static final ResolvableType FORM_DATA_TYPE = ResolvableType.forClassWithGenerics(MultiValueMap.class, String.class, String.class);
    private static final Mono<MultiValueMap<String, Part>> EMPTY_MULTIPART_DATA = Mono.just(CollectionUtils.unmodifiableMultiValueMap(new LinkedMultiValueMap<String, Part>(0))).cache();
    private static final ResolvableType MULTIPART_DATA_TYPE = ResolvableType.forClassWithGenerics(MultiValueMap.class, String.class, Part.class);

    private final ServerCodecConfigurer configurer;
    private final ServerHttpRequestDecorator request;
    private final ServerHttpResponseDecorator response;

    public AceServerWebExchangeDecorator(ServerWebExchange delegate, ServerCodecConfigurer configurer, byte[] bytes) {
        super(delegate);
        this.configurer = configurer;
        this.request = new AceServerHttpRequestDecorator(delegate, bytes);
        this.response = new AceServerHttpResponseDecorator(delegate);
    }

    @Override
    public ServerHttpRequest getRequest() {
        return request;
    }

    @Override
    public ServerHttpResponse getResponse() {
        return response;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Mono<MultiValueMap<String, String>> getFormData() {
        try {
            MediaType contentType = request.getHeaders().getContentType();
            if (MediaType.APPLICATION_FORM_URLENCODED.isCompatibleWith(contentType)) {
                return ((HttpMessageReader<MultiValueMap<String, String>>) configurer.getReaders().stream()
                        .filter(reader -> reader.canRead(FORM_DATA_TYPE, MediaType.APPLICATION_FORM_URLENCODED)).findFirst()
                        .orElseThrow(() -> new IllegalStateException("No form data HttpMessageReader.")))
                        .readMono(FORM_DATA_TYPE, request, Collections.emptyMap())
                        .switchIfEmpty(EMPTY_FORM_DATA)
                        .cache();
            }
        } catch (InvalidMediaTypeException ex) {
            LOGGER.warn("exception occurred when read form data {}, {}", ex.getMessage(), ex);
        }
        return EMPTY_FORM_DATA;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Mono<MultiValueMap<String, Part>> getMultipartData() {
        try {
            MediaType contentType = request.getHeaders().getContentType();
            if (MediaType.MULTIPART_FORM_DATA.isCompatibleWith(contentType)) {
                return ((HttpMessageReader<MultiValueMap<String, Part>>) configurer.getReaders().stream()
                        .filter(reader -> reader.canRead(MULTIPART_DATA_TYPE, MediaType.MULTIPART_FORM_DATA)).findFirst()
                        .orElseThrow(() -> new IllegalStateException("No multipart HttpMessageReader.")))
                        .readMono(MULTIPART_DATA_TYPE, request, Collections.emptyMap())
                        .switchIfEmpty(EMPTY_MULTIPART_DATA)
                        .cache();
            }
        } catch (InvalidMediaTypeException ex) {
            LOGGER.warn("exception occurred when read multipart form data {}, {}", ex.getMessage(), ex);
        }
        return EMPTY_MULTIPART_DATA;
    }
}
