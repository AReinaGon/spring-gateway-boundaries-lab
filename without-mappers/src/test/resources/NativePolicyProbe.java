import com.areina.gatewaylab.nativeimpl.infrastructure.Rules;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** A second, synchronous Java host: reuses rules, owns header orchestration. */
public class NativePolicyProbe {
    private static Map<String, List<String>> transform(String method, Map<String, List<String>> input, long bytes) {
        Rules.checkSize(bytes);
        var headers = new TreeMap<String, List<String>>(String.CASE_INSENSITIVE_ORDER);
        headers.putAll(input);
        String workspace = Rules.workspace(headers.get(Rules.WORKSPACE));
        headers.keySet().removeIf(key -> key.toLowerCase(java.util.Locale.ROOT).startsWith("x-lab-"));
        headers.put(Rules.WORKSPACE, List.of(workspace));
        var identity = Rules.authenticate(headers.get("Authorization"));
        headers.put(Rules.SUBJECT, List.of(identity.subject()));
        headers.put(Rules.ROLE, List.of(identity.role()));
        headers.put(Rules.TOKEN_WORKSPACE, List.of(identity.workspace()));
        String action = Rules.authorize(method, headers.get(Rules.WORKSPACE).getFirst(),
                headers.get(Rules.TOKEN_WORKSPACE).getFirst(), headers.get(Rules.ROLE).getFirst());
        headers.put(Rules.ACTION, List.of(action));
        for (String name : List.of("Authorization", Rules.ROLE, Rules.TOKEN_WORKSPACE)) headers.remove(name);
        return headers;
    }

    public static void main(String[] args) {
        int[] statuses = {200, 400, 401, 403, 413, Boolean.getBoolean("lab.allowDelete") ? 200 : 403};
        for (int index = 0; index < statuses.length; index++) {
            var headers = new TreeMap<String, List<String>>(String.CASE_INSENSITIVE_ORDER);
            headers.put("X-Workspace", index == 1 ? List.of("alpha", "alpha") : List.of(" AlPhA "));
            headers.put("Authorization", List.of(index == 2 ? "Bearer invalid" : index == 3 ? "Bearer demo-beta-reader" : "Bearer demo-alpha-writer"));
            headers.put("X-Note", List.of("one", "two"));
            headers.put("X-Lab-Subject", List.of("forged"));
            URI uri = URI.create("/inspect/catalog?tag=a&tag=b");
            byte[] body = new byte[index == 4 ? 1025 : 64];
            int status = 200;
            Map<String, List<String>> result = null;
            try { result = transform(index == 5 ? "DELETE" : "POST", headers, body.length); }
            catch (Rules.Violation violation) { status = violation.status(); }
            if (status != statuses[index]) throw new AssertionError("Unexpected status for case " + index);
            if (status == 200 && (!result.get("X-Workspace").equals(List.of("alpha"))
                    || !result.get("X-Lab-Subject").equals(List.of("bob"))
                    || !result.get("X-Lab-Action").equals(List.of(index == 5 ? "delete" : "write"))
                    || !result.get("X-Note").equals(List.of("one", "two"))
                    || result.containsKey("Authorization") || result.containsKey(Rules.ROLE))) throw new AssertionError("Unexpected transformation");
            System.out.println(index + ":" + status + ":" + uri + ":" + (result == null ? "rejected" : result));
        }
    }
}
