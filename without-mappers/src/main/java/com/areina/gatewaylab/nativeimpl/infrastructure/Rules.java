package com.areina.gatewaylab.nativeimpl.infrastructure;

import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class Rules {
    public static final int MAX_BODY_BYTES = 1024;
    public static final String WORKSPACE = "X-Workspace";
    public static final String SUBJECT = "X-Lab-Subject";
    public static final String ROLE = "X-Lab-Role";
    public static final String TOKEN_WORKSPACE = "X-Lab-Token-Workspace";
    public static final String ACTION = "X-Lab-Action";

    public record Identity(String subject, String role, String workspace) {}
    private static final Map<String, Identity> TOKENS = Map.of(
            "demo-alpha-reader", new Identity("alice", "reader", "alpha"),
            "demo-alpha-writer", new Identity("bob", "writer", "alpha"),
            "demo-beta-reader", new Identity("carol", "reader", "beta"));

    private Rules() {}

    public static final class Violation extends RuntimeException {
        private static final long serialVersionUID = 1L;
        private final int status;
        public Violation(int status, String message) { super(message); this.status = status; }
        public int status() { return status; }
    }

    public static void checkSize(long bytes) {
        if (bytes > MAX_BODY_BYTES) throw new Violation(413, "body_too_large");
    }

    public static String workspace(List<String> values) {
        String normalized = one(values, 400, "invalid_workspace").strip().toLowerCase(Locale.ROOT);
        if (!normalized.matches("[a-z][a-z0-9-]{0,31}")) throw new Violation(400, "invalid_workspace");
        return normalized;
    }

    public static Identity authenticate(List<String> values) {
        String authorization = one(values, 401, "invalid_token");
        if (!authorization.regionMatches(true, 0, "Bearer ", 0, 7)) throw new Violation(401, "invalid_token");
        Identity identity = TOKENS.get(authorization.substring(7));
        if (identity == null) throw new Violation(401, "invalid_token");
        return identity;
    }

    public static String authorize(String method, String workspace, String boundWorkspace, String role) {
        if (!workspace.equals(boundWorkspace)) throw new Violation(403, "workspace_forbidden");
        if (method.equals("GET") && (role.equals("reader") || role.equals("writer"))) return "read";
        if (method.equals("POST") && role.equals("writer")) return "write";
        throw new Violation(403, "operation_forbidden");
    }

    public static String one(List<String> values, int status, String error) {
        if (values == null || values.size() != 1 || values.getFirst().isBlank()) throw new Violation(status, error);
        return values.getFirst();
    }
}
