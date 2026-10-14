package com.areina.gatewaylab.mapped.infrastructure;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

public final class FilterRuntime {
    private static final String TRACE = "lab.trace";
    private static final String READS = "lab.mapping.reads";
    private static final String APPLIES = "lab.mapping.applies";
    private static final boolean INSTRUMENT = Boolean.parseBoolean(System.getProperty("lab.instrumentation", "true"));
    private FilterRuntime() {}
    public static boolean instrumentationEnabled() { return INSTRUMENT; }

    public static Mono<Void> run(ServerWebExchange exchange, String stage,
                                 Function<ServerWebExchange, Mono<Void>> operation) {
        return Mono.defer(() -> {
            if (INSTRUMENT) {
                List<String> trace = exchange.getAttribute(TRACE);
                if (trace == null) { trace = new ArrayList<>(); exchange.getAttributes().put(TRACE, trace); }
                trace.add(stage);
                exchange.getResponse().getHeaders().set("X-Lab-Trace", String.join(",", trace));
                updateMappingHeader(exchange);
            }
            return operation.apply(exchange);
        }).onErrorResume(Rules.Violation.class, error -> reject(exchange, error));
    }

    private static Mono<Void> reject(ServerWebExchange exchange, Rules.Violation error) {
        var response = exchange.getResponse();
        response.setStatusCode(HttpStatusCode.valueOf(error.status()));
        response.getHeaders().setContentType(MediaType.TEXT_PLAIN);
        byte[] bytes = error.getMessage().getBytes(StandardCharsets.UTF_8);
        return response.writeWith(Mono.just(response.bufferFactory().wrap(bytes)));
    }

    public static void mappedRead(ServerWebExchange exchange) { if (INSTRUMENT) increment(exchange, READS); }
    public static void mappedApply(ServerWebExchange exchange) { if (INSTRUMENT) increment(exchange, APPLIES); }
    private static void increment(ServerWebExchange exchange, String key) {
        int previous = exchange.getAttributeOrDefault(key, 0);
        exchange.getAttributes().put(key, previous + 1);
        updateMappingHeader(exchange);
    }
    private static void updateMappingHeader(ServerWebExchange exchange) {
        exchange.getResponse().getHeaders().set("X-Lab-Mapping",
                "reads=" + exchange.getAttributeOrDefault(READS, 0)
                + "; applies=" + exchange.getAttributeOrDefault(APPLIES, 0));
    }
}
