package com.areina.gatewaylab.mapped.acceptance;

import static org.junit.jupiter.api.Assertions.*;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

public abstract class AbstractCompositionTest {
    protected abstract List<GatewayFilter> filters();
    protected abstract boolean mapped();

    @TestFactory
    Stream<DynamicTest> sameChainContract() {
        return Scenarios.all().stream().map(test -> DynamicTest.dynamicTest(test.name(), () -> {
            var builder = MockServerHttpRequest.method(HttpMethod.valueOf(test.method()), URI.create("http://localhost" + Scenarios.PATH));
            if (!test.workspace().isEmpty()) builder.header("X-Workspace", test.workspace().toArray(String[]::new));
            if (!test.authorization().isEmpty()) builder.header("Authorization", test.authorization().toArray(String[]::new));
            builder.header("X-Note", "first", "second").header("X-Lab-Role", "writer")
                    .header("X-Lab-Subject", "mallory").header("X-Lab-Token-Workspace", "beta")
                    .header("X-Lab-Untrusted", "remove-me");
            if (!test.chunked()) builder.header("Content-Length", Integer.toString(test.body().length));
            var request = builder.body(test.body().length == 0 ? Flux.empty()
                    : Flux.just(DefaultDataBufferFactory.sharedInstance.wrap(test.body())));
            var exchange = MockServerWebExchange.from(request);
            Object marker = new Object(); exchange.getAttributes().put("lab.acceptance.marker", marker);
            var finalExchange = new AtomicReference<ServerWebExchange>();
            var finalBody = new AtomicReference<byte[]>();
            var calls = new AtomicInteger();
            StepVerifier.create(invoke(filters(), 0, exchange, finalExchange, finalBody, calls))
                    .expectComplete().verify(Duration.ofSeconds(5));
            assertEquals(test.status(), exchange.getResponse().getStatusCode().value());
            assertEquals(test.trace(), exchange.getResponse().getHeaders().getFirst("X-Lab-Trace"));
            assertEquals(Scenarios.mappingHeader(test, mapped()), exchange.getResponse().getHeaders().getFirst("X-Lab-Mapping"));
            assertEquals(test.status() == 200 ? 1 : 0, calls.get());
            if (test.status() == 200) {
                var outgoing = finalExchange.get().getRequest();
                assertSame(marker, finalExchange.get().getAttribute("lab.acceptance.marker"));
                assertEquals(request.getURI(), outgoing.getURI());
                assertEquals(request.getMethod(), outgoing.getMethod());
                assertEquals(List.of("first", "second"), outgoing.getHeaders().get("X-Note"));
                assertEquals(test.workspace().getFirst().strip().toLowerCase(java.util.Locale.ROOT), outgoing.getHeaders().getFirst("X-Workspace"));
                assertEquals(test.subject(), outgoing.getHeaders().getFirst("X-Lab-Subject"));
                assertEquals(test.action(), outgoing.getHeaders().getFirst("X-Lab-Action"));
                for (String removed : List.of("Authorization", "X-Lab-Role", "X-Lab-Token-Workspace", "X-Lab-Untrusted"))
                    assertNull(outgoing.getHeaders().getFirst(removed));
                assertArrayEquals(test.body(), finalBody.get());
            }
        }));
    }

    private Mono<Void> invoke(List<GatewayFilter> filters, int index, ServerWebExchange exchange,
                               AtomicReference<ServerWebExchange> captured, AtomicReference<byte[]> body,
                               AtomicInteger calls) {
        if (index < filters.size()) return filters.get(index).filter(exchange,
                next -> invoke(filters, index + 1, next, captured, body, calls));
        calls.incrementAndGet(); captured.set(exchange);
        exchange.getResponse().setStatusCode(HttpStatus.OK);
        return DataBufferUtils.join(exchange.getRequest().getBody()).map(buffer -> {
            try { byte[] bytes = new byte[buffer.readableByteCount()]; buffer.read(bytes); return bytes; }
            finally { DataBufferUtils.release(buffer); }
        }).defaultIfEmpty(new byte[0]).doOnNext(body::set).then();
    }
}
