package com.areina.gatewaylab.mapped;

import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public record RequestSnapshot(String method, URI uri, Map<String, List<String>> headers, long bodyBytes) {
    public RequestSnapshot { headers = freeze(headers); }
    public List<String> values(String name) { return headers.get(name); }
    public String first(String name) {
        List<String> values = values(name);
        return values == null || values.isEmpty() ? null : values.getFirst();
    }
    static Map<String, List<String>> freeze(Map<String, List<String>> input) {
        var copied = new TreeMap<String, List<String>>(String.CASE_INSENSITIVE_ORDER);
        input.forEach((key, values) -> copied.put(key, List.copyOf(values)));
        return Collections.unmodifiableMap(copied);
    }
}
