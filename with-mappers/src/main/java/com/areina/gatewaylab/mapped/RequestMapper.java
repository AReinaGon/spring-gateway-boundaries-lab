package com.areina.gatewaylab.mapped;

import com.areina.gatewaylab.mapped.infrastructure.BodySupport;
import com.areina.gatewaylab.mapped.infrastructure.FilterRuntime;
import org.springframework.web.server.ServerWebExchange;

public final class RequestMapper {
    private RequestMapper() {}
    public static RequestSnapshot read(ServerWebExchange exchange) {
        FilterRuntime.mappedRead(exchange);
        var request = exchange.getRequest();
        return new RequestSnapshot(request.getMethod().name(), request.getURI(), request.getHeaders(),
                exchange.getAttributeOrDefault(BodySupport.SIZE, 0L));
    }
    public static ServerWebExchange apply(ServerWebExchange exchange, HeaderUpdate changes) {
        FilterRuntime.mappedApply(exchange);
        if (changes.replacements().isEmpty() && changes.removals().isEmpty()) return exchange;
        var request = exchange.getRequest().mutate().headers(headers -> {
            changes.removals().forEach(headers::remove);
            changes.replacements().forEach((key, values) -> headers.put(key, values));
        }).build();
        return exchange.mutate().request(request).build();
    }
}
