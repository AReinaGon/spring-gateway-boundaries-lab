package com.areina.gatewaylab.benchmark;

import com.areina.gatewaylab.nativeimpl.infrastructure.FilterRuntime;
import java.net.URI;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.ServerWebExchangeDecorator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Threads(1)
@Fork(3)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 2)
public class ChainBenchmark {
    @Param({"64", "1024"}) public int bodyBytes;
    @Param({"8", "32", "96"}) public int extraHeaders;
    private byte[] payload;
    private List<GatewayFilter> nativeFilters;
    private List<GatewayFilter> mappedFilters;
    private ServerWebExchange template;

    @Setup(Level.Trial) public void setup() {
        if (FilterRuntime.instrumentationEnabled() || com.areina.gatewaylab.mapped.infrastructure.FilterRuntime.instrumentationEnabled()) throw new IllegalStateException("Run with -Dlab.instrumentation=false");
        payload = new byte[bodyBytes];
        Arrays.fill(payload, (byte) 'x');
        nativeFilters = List.of(new com.areina.gatewaylab.nativeimpl.filters.BodyLimitGatewayFilterFactory().apply(new com.areina.gatewaylab.nativeimpl.filters.BodyLimitGatewayFilterFactory.Config()),
                new com.areina.gatewaylab.nativeimpl.filters.NormalizeWorkspaceGatewayFilterFactory().apply(new com.areina.gatewaylab.nativeimpl.filters.NormalizeWorkspaceGatewayFilterFactory.Config()),
                new com.areina.gatewaylab.nativeimpl.filters.TokenIdentityGatewayFilterFactory().apply(new com.areina.gatewaylab.nativeimpl.filters.TokenIdentityGatewayFilterFactory.Config()),
                new com.areina.gatewaylab.nativeimpl.filters.AuthorizeOperationGatewayFilterFactory().apply(new com.areina.gatewaylab.nativeimpl.filters.AuthorizeOperationGatewayFilterFactory.Config()));
        mappedFilters = List.of(new com.areina.gatewaylab.mapped.filters.BodyLimitGatewayFilterFactory().apply(new com.areina.gatewaylab.mapped.filters.BodyLimitGatewayFilterFactory.Config()),
                new com.areina.gatewaylab.mapped.filters.NormalizeWorkspaceGatewayFilterFactory().apply(new com.areina.gatewaylab.mapped.filters.NormalizeWorkspaceGatewayFilterFactory.Config()),
                new com.areina.gatewaylab.mapped.filters.TokenIdentityGatewayFilterFactory().apply(new com.areina.gatewaylab.mapped.filters.TokenIdentityGatewayFilterFactory.Config()),
                new com.areina.gatewaylab.mapped.filters.AuthorizeOperationGatewayFilterFactory().apply(new com.areina.gatewaylab.mapped.filters.AuthorizeOperationGatewayFilterFactory.Config()));
        template = MockServerWebExchange.from(MockServerHttpRequest.get("/template").build());
    }
    @Benchmark public void nativeChain(Blackhole sink) { run(nativeFilters, sink); }
    @Benchmark public void mappedChain(Blackhole sink) { run(mappedFilters, sink); }

    private void run(List<GatewayFilter> filters, Blackhole sink) {
        var builder = MockServerHttpRequest.method(HttpMethod.POST, URI.create("http://localhost/inspect/catalog?tag=a&tag=b"))
                .header("X-Workspace", "AlPhA").header("Authorization", "Bearer demo-alpha-writer")
                .header("X-Note", "one", "two").header("Content-Length", Integer.toString(payload.length));
        for (int i = 0; i < extraHeaders; i++) builder.header("X-Extra-" + i, "value-" + i);
        var input = builder.body(Flux.just(DefaultDataBufferFactory.sharedInstance.wrap(payload)));
        var exchange = new ServerWebExchangeDecorator(template) {
            private final Map<String, Object> attributes = new HashMap<>();
            @Override public ServerHttpRequest getRequest() { return input; }
            @Override public Map<String, Object> getAttributes() { return attributes; }
        };
        invoke(filters, 0, exchange, sink).block();
        if (exchange.getResponse().getStatusCode() != null) throw new IllegalStateException("Unexpected rejection");
    }

    private Mono<Void> invoke(List<GatewayFilter> filters, int index, ServerWebExchange exchange, Blackhole sink) {
        if (index < filters.size()) return filters.get(index).filter(exchange,
                next -> invoke(filters, index + 1, next, sink));
        var request = exchange.getRequest();
        if (!"alpha".equals(request.getHeaders().getFirst("X-Workspace"))
                || !"bob".equals(request.getHeaders().getFirst("X-Lab-Subject"))
                || !"write".equals(request.getHeaders().getFirst("X-Lab-Action"))
                || request.getHeaders().containsKey("Authorization")) throw new IllegalStateException("Incorrect transformation");
        sink.consume(request.getURI());
        sink.consume(request.getHeaders());
        return DataBufferUtils.join(request.getBody()).doOnNext(buffer -> {
            try {
                byte[] bytes = new byte[buffer.readableByteCount()]; buffer.read(bytes);
                if (!Arrays.equals(payload, bytes)) throw new IllegalStateException("Body changed");
                sink.consume(bytes);
            } finally { DataBufferUtils.release(buffer); }
        }).then();
    }
}
