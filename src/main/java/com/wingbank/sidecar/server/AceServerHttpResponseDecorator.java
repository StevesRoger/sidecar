package com.wingbank.sidecar.server;

import com.wingbank.sidecar.filter.LoggingFilter;
import com.wingbank.sidecar.model.ServerContext;
import org.reactivestreams.Publisher;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

public class AceServerHttpResponseDecorator extends ServerHttpResponseDecorator {

    private final StringBuilder body = new StringBuilder();
    private final ServerWebExchange exchange;

    public AceServerHttpResponseDecorator(ServerWebExchange exchange) {
        super(exchange.getResponse());
        this.exchange = exchange;
    }

    @Override
    public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
        return super.writeWith(Flux.from(body).doOnNext(this::capture));
    }

    public String getBodyString() {
        return body.toString();
    }

    public ServerWebExchange getExchange() {
        return exchange;
    }

    private void capture(DataBuffer buffer) {
        if (MediaType.APPLICATION_JSON.isCompatibleWith(getHeaders().getContentType())) {
            this.body.append(buffer.toString(StandardCharsets.UTF_8));
            this.exchange.getAttributes().put(LoggingFilter.RESPONSE_BODY_ATTR, body.toString());
            Optional.ofNullable((ServerContext) exchange.getAttribute(LoggingFilter.SERVER_CONTEXT_ATTR)).ifPresent(v -> v.setResponseBody(body.toString()));
        }
    }
}
