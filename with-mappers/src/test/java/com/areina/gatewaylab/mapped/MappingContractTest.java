package com.areina.gatewaylab.mapped;

import static org.junit.jupiter.api.Assertions.*;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

class MappingContractTest {
    @Test void snapshotDefensivelyCopiesListsAndKeepsCaseInsensitiveLookup() {
        var values = new ArrayList<>(List.of("one", "two"));
        var headers = new HashMap<String, List<String>>(); headers.put("X-Note", values);
        var snapshot = new RequestSnapshot("GET", URI.create("/inspect"), headers, 0);
        values.add("three"); headers.clear();
        assertEquals(List.of("one", "two"), snapshot.values("x-note"));
        assertThrows(UnsupportedOperationException.class, () -> snapshot.values("X-Note").add("four"));
        assertThrows(UnsupportedOperationException.class, () -> snapshot.headers().clear());
    }
    @Test void updateIsAlsoImmutable() {
        var replacements = new HashMap<String, List<String>>(); replacements.put("X-Note", new ArrayList<>(List.of("one")));
        var removals = new ArrayList<>(List.of("Authorization"));
        var update = new HeaderUpdate(replacements, removals); replacements.clear(); removals.clear();
        assertEquals(List.of("one"), update.replacements().get("x-note"));
        assertEquals(List.of("Authorization"), update.removals());
    }
    @Test void applyingAPatchKeepsUriUnrelatedHeadersAndExchangeAttributes() {
        var uri = URI.create("http://localhost/inspect/a%2Fb?tag=a&tag=b");
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.method(org.springframework.http.HttpMethod.GET, uri)
                .header("X-Note", "one", "two").header("Authorization", "Bearer demo-alpha-reader").build());
        Object marker = new Object(); exchange.getAttributes().put("marker", marker);
        var updated = RequestMapper.apply(exchange, new HeaderUpdate(Map.of("X-Workspace", List.of("alpha")), List.of("Authorization")));
        assertEquals(uri, updated.getRequest().getURI());
        assertEquals(List.of("one", "two"), updated.getRequest().getHeaders().get("X-Note"));
        assertNull(updated.getRequest().getHeaders().getFirst("Authorization"));
        assertSame(marker, updated.getAttribute("marker"));
    }
    @Test void processorsCanUseOwnTypesWithoutAnExchange() {
        var snapshot = new RequestSnapshot("GET", URI.create("/inspect"), Map.of("X-Workspace", List.of(" AlPhA ")), 0);
        assertEquals(List.of("alpha"), MappedRules.normalize(snapshot).replacements().get("X-Workspace"));
    }
}
