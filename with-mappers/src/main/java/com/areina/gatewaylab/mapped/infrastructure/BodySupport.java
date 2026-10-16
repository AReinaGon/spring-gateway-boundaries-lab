package com.areina.gatewaylab.mapped.infrastructure;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public final class BodySupport {
    public static final String SIZE = "lab.body.bytes";
    private BodySupport() {}

    public static Mono<ServerWebExchange> cacheBounded(ServerWebExchange exchange) {
        if (FilterRuntime.instrumentationEnabled()) {
            exchange.getResponse().getHeaders().set("X-Lab-Input-Length",
                    Long.toString(exchange.getRequest().getHeaders().getContentLength()));
        }
        return DataBufferUtils.join(exchange.getRequest().getBody(), Rules.MAX_BODY_BYTES)
                .map(buffer -> {
                    try {
                        byte[] bytes = new byte[buffer.readableByteCount()];
                        buffer.read(bytes);
                        return bytes;
                    } finally { DataBufferUtils.release(buffer); }
                }).defaultIfEmpty(new byte[0])
                .map(bytes -> {
                    exchange.getAttributes().put(SIZE, (long) bytes.length);
                    var request = new ServerHttpRequestDecorator(exchange.getRequest()) {
                        @Override public Flux<DataBuffer> getBody() {
                            return Flux.defer(() -> bytes.length == 0 ? Flux.empty()
                                    : Flux.just(exchange.getResponse().bufferFactory().wrap(bytes)));
                        }
                    };
                    return exchange.mutate().request(request).build();
                }).onErrorMap(DataBufferLimitException.class,
                        error -> new Rules.Violation(413, "body_too_large"));
    }
}
