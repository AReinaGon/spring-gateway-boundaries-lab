package com.areina.gatewaylab.mapped.acceptance;

import static org.junit.jupiter.api.Assertions.*;
import java.net.URI;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.MountableFile;
import reactor.core.publisher.Flux;

public abstract class AbstractGatewayIT {
    private static final GenericContainer<?> BACKEND = new GenericContainer<>("python:3.12-alpine")
            .withCopyFileToContainer(MountableFile.forClasspathResource("echo.py"), "/srv/echo.py")
            .withCommand("python", "/srv/echo.py").withExposedPorts(8080)
            .waitingFor(Wait.forHttp("/health").forStatusCode(200));
    static { BACKEND.start(); }
    @LocalServerPort int port;
    protected abstract boolean mapped();
    @DynamicPropertySource static void upstream(DynamicPropertyRegistry registry) {
        registry.add("lab.upstream-url", () -> "http://" + BACKEND.getHost() + ":" + BACKEND.getMappedPort(8080));
        registry.add("spring.main.banner-mode", () -> "off");
        registry.add("logging.level.root", () -> "WARN");
    }
    @AfterAll static void stopBackend() { BACKEND.stop(); }

    @TestFactory
    Stream<DynamicTest> realHttpChainContract() {
        return Scenarios.all().stream().map(test -> DynamicTest.dynamicTest(test.name(), () -> {
            long before = calls();
            var client = WebTestClient.bindToServer().responseTimeout(Duration.ofSeconds(10)).build();
            var request = client.method(HttpMethod.valueOf(test.method()))
                    .uri(URI.create("http://localhost:" + port + Scenarios.PATH)).contentType(MediaType.APPLICATION_OCTET_STREAM);
            if (!test.workspace().isEmpty()) request.header("X-Workspace", test.workspace().toArray(String[]::new));
            if (!test.authorization().isEmpty()) request.header("Authorization", test.authorization().toArray(String[]::new));
            request.header("X-Note", "first", "second").header("X-Lab-Subject", "mallory")
                    .header("X-Lab-Role", "writer").header("X-Lab-Token-Workspace", "beta").header("X-Lab-Untrusted", "remove-me");
            var prepared = test.chunked()
                    ? request.body(BodyInserters.fromDataBuffers(Flux.just(DefaultDataBufferFactory.sharedInstance.wrap(test.body()))))
                    : request.bodyValue(test.body());
            var response = prepared.exchange().expectStatus().isEqualTo(test.status())
                    .expectHeader().valueEquals("X-Lab-Trace", test.trace())
                    .expectHeader().valueEquals("X-Lab-Mapping", Scenarios.mappingHeader(test, mapped()));
            if (test.chunked()) response.expectHeader().valueEquals("X-Lab-Input-Length", "-1");
            if (test.status() == 200) {
                Map<?, ?> body = response.expectBody(Map.class).returnResult().getResponseBody();
                assertNotNull(body);
                assertEquals(test.method(), body.get("method"));
                assertEquals(Scenarios.PATH, body.get("path"));
                assertEquals(Base64.getEncoder().encodeToString(test.body()), body.get("bodyBase64"));
                assertEquals(test.body().length, ((Number) body.get("bodyBytes")).intValue());
                Map<?, ?> headers = (Map<?, ?>) body.get("headers");
                assertEquals(List.of(test.workspace().getFirst().strip().toLowerCase(java.util.Locale.ROOT)), headers.get("x-workspace"));
                assertEquals(List.of(test.subject()), headers.get("x-lab-subject"));
                assertEquals(List.of(test.action()), headers.get("x-lab-action"));
                assertEquals(List.of("first", "second"), headers.get("x-note"));
                for (String removed : List.of("authorization", "x-lab-role", "x-lab-token-workspace", "x-lab-untrusted"))
                    assertFalse(headers.containsKey(removed));
            } else { response.expectBody(String.class).returnResult(); }
            assertEquals(before + (test.status() == 200 ? 1 : 0), calls());
        }));
    }

    private long calls() {
        Map<?, ?> stats = WebTestClient.bindToServer().baseUrl("http://" + BACKEND.getHost() + ":" + BACKEND.getMappedPort(8080))
                .build().get().uri("/stats").exchange().expectStatus().isOk().expectBody(Map.class).returnResult().getResponseBody();
        return ((Number) stats.get("calls")).longValue();
    }
}
