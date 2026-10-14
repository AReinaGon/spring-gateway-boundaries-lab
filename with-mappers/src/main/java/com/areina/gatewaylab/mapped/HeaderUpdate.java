package com.areina.gatewaylab.mapped;

import java.util.List;
import java.util.Map;

public record HeaderUpdate(Map<String, List<String>> replacements, List<String> removals) {
    public HeaderUpdate { replacements = RequestSnapshot.freeze(replacements); removals = List.copyOf(removals); }
    public static HeaderUpdate empty() { return new HeaderUpdate(Map.of(), List.of()); }
}
