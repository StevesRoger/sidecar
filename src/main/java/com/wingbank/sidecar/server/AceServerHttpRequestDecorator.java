package com.wingbank.sidecar.server;

import io.netty.buffer.ByteBufAllocator;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.NettyDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;

import java.nio.charset.StandardCharsets;

public class AceServerHttpRequestDecorator extends ServerHttpRequestDecorator {

    private final byte[] rawBody;
    private final ServerWebExchange exchange;

    public AceServerHttpRequestDecorator(ServerWebExchange exchange, byte[] rawBody) {
        super(exchange.getRequest());
        this.exchange = exchange;
        this.rawBody = rawBody;
    }

    @Override
    public HttpHeaders getHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.putAll(super.getHeaders());
        MediaType contentType = headers.getContentType();
        if (MediaType.APPLICATION_JSON.isCompatibleWith(contentType) || MediaType.MULTIPART_FORM_DATA.isCompatibleWith(contentType))
            return headers;
        if (rawBody != null && rawBody.length > 0)
            headers.setContentLength(rawBody.length);
        else
            headers.set(HttpHeaders.TRANSFER_ENCODING, "chunked");
        return headers;
    }

    @Override
    public Flux<DataBuffer> getBody() {
        if (rawBody == null || rawBody.length <= 0) return super.getBody();
        return DataBufferUtils.read(new ByteArrayResource(rawBody),
                new NettyDataBufferFactory(ByteBufAllocator.DEFAULT), rawBody.length);
    }

    public String getBodyString() {
        return new String(rawBody, StandardCharsets.UTF_8);
    }

    public ServerWebExchange getExchange() {
        return exchange;
    }
}
