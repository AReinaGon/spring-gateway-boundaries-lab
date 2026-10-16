package com.areina.gatewaylab.mapped;

import com.areina.gatewaylab.mapped.infrastructure.Rules;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class MappedRules {
    private MappedRules() {}
    public static HeaderUpdate size(RequestSnapshot input) {
        Rules.checkSize(input.bodyBytes());
        return HeaderUpdate.empty();
    }
    public static HeaderUpdate normalize(RequestSnapshot input) {
        String workspace = Rules.workspace(input.values(Rules.WORKSPACE));
        var removed = input.headers().keySet().stream()
                .filter(key -> key.toLowerCase(Locale.ROOT).startsWith("x-lab-")).toList();
        return new HeaderUpdate(Map.of(Rules.WORKSPACE, List.of(workspace)), removed);
    }
    public static HeaderUpdate token(RequestSnapshot input) {
        var identity = Rules.authenticate(input.values("Authorization"));
        return new HeaderUpdate(Map.of(Rules.SUBJECT, List.of(identity.subject()),
                Rules.ROLE, List.of(identity.role()), Rules.TOKEN_WORKSPACE, List.of(identity.workspace())), List.of());
    }
    public static HeaderUpdate authorize(RequestSnapshot input) {
        String action = Rules.authorize(input.method(), input.first(Rules.WORKSPACE),
                input.first(Rules.TOKEN_WORKSPACE), input.first(Rules.ROLE));
        return new HeaderUpdate(Map.of(Rules.ACTION, List.of(action)),
                List.of("Authorization", Rules.ROLE, Rules.TOKEN_WORKSPACE));
    }
}
