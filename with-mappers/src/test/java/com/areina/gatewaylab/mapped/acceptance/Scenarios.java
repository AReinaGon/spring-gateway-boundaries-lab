package com.areina.gatewaylab.mapped.acceptance;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

public final class Scenarios {
    public static final String PATH = "/inspect/catalog?tag=a&tag=b&encoded=a%2Fb";
    public static final String FULL = "size,normalize,token,authorize";
    public record Case(String name, String method, List<String> workspace, List<String> authorization,
                       byte[] body, boolean chunked, int status, String trace, String subject, String action) {}
    private Scenarios() {}
    public static List<Case> all() {
        byte[] boundary = new byte[1024]; Arrays.fill(boundary, (byte) 'x');
        byte[] oversized = new byte[1025]; Arrays.fill(oversized, (byte) 'x');
        return List.of(
            ok("normalization feeds authorization", "GET", "AlPhA", "demo-alpha-reader", new byte[0], false, "alice", "read"),
            ok("second workspace", "GET", "BETA", "demo-beta-reader", new byte[0], false, "carol", "read"),
            ok("writer preserves UTF-8 body", "POST", "alpha", "demo-alpha-writer", "árbol\n文".getBytes(StandardCharsets.UTF_8), false, "bob", "write"),
            ok("writer preserves binary body", "POST", "alpha", "demo-alpha-writer", new byte[]{0, (byte)255, 10, 13, 66}, false, "bob", "write"),
            ok("exact body limit", "POST", "alpha", "demo-alpha-writer", boundary, false, "bob", "write"),
            ok("chunked body at limit", "POST", "alpha", "demo-alpha-writer", boundary, true, "bob", "write"),
            bad("body too large with length", "POST", List.of("alpha"), List.of("Bearer demo-alpha-writer"), oversized, false, 413, "size"),
            bad("body too large without length", "POST", List.of("alpha"), List.of("Bearer demo-alpha-writer"), oversized, true, 413, "size"),
            bad("missing workspace", "GET", List.of(), List.of("Bearer demo-alpha-reader"), new byte[0], false, 400, "size,normalize"),
            bad("invalid workspace", "GET", List.of("../alpha"), List.of("Bearer demo-alpha-reader"), new byte[0], false, 400, "size,normalize"),
            bad("duplicate workspace", "GET", List.of("alpha", "beta"), List.of("Bearer demo-alpha-reader"), new byte[0], false, 400, "size,normalize"),
            bad("missing token with forged identity", "GET", List.of("alpha"), List.of(), new byte[0], false, 401, "size,normalize,token"),
            bad("unknown token", "GET", List.of("alpha"), List.of("Bearer unknown"), new byte[0], false, 401, "size,normalize,token"),
            bad("wrong token scheme", "GET", List.of("alpha"), List.of("Basic demo-alpha-reader"), new byte[0], false, 401, "size,normalize,token"),
            bad("duplicate token", "GET", List.of("alpha"), List.of("Bearer demo-alpha-reader", "Bearer demo-alpha-writer"), new byte[0], false, 401, "size,normalize,token"),
            bad("reader cannot write despite forged role", "POST", List.of("alpha"), List.of("Bearer demo-alpha-reader"), new byte[0], false, 403, FULL),
            bad("wrong workspace", "GET", List.of("beta"), List.of("Bearer demo-alpha-reader"), new byte[0], false, 403, FULL),
            bad("unsupported operation", "DELETE", List.of("alpha"), List.of("Bearer demo-alpha-writer"), new byte[0], false, 403, FULL));
    }
    private static Case ok(String name, String method, String workspace, String token, byte[] body,
                           boolean chunked, String subject, String action) {
        return new Case(name, method, List.of(workspace), List.of("Bearer " + token), body,
                chunked, 200, FULL, subject, action);
    }
    private static Case bad(String name, String method, List<String> workspace, List<String> tokens,
                            byte[] body, boolean chunked, int status, String trace) {
        return new Case(name, method, workspace, tokens, body, chunked, status, trace, null, null);
    }
    public static String mappingHeader(Case test, boolean mapped) {
        if (!mapped || test.status() == 413) return "reads=0; applies=0";
        int stages = test.trace().split(",").length;
        return "reads=" + stages + "; applies=" + (test.status() == 200 ? stages : stages - 1);
    }
}
