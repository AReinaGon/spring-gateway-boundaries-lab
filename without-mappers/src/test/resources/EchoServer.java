import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/** Disposable Java-only HTTP destination for contract tests and local Compose. */
public final class EchoServer {
    private static final AtomicLong CALLS = new AtomicLong();

    public static void main(String[] args) throws Exception {
        if (args.length == 1 && args[0].equals("health")) {
            try (var client = HttpClient.newHttpClient()) {
                var request = HttpRequest.newBuilder(URI.create("http://localhost:8080/health")).GET().build();
                if (client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode() != 200)
                    throw new IOException("Echo server is not ready");
            }
            return;
        }
        var server = HttpServer.create(new InetSocketAddress("0.0.0.0", 8080), 0);
        server.createContext("/", EchoServer::respond);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.start();
    }

    private static void respond(HttpExchange exchange) throws IOException {
        try (exchange) {
            String payload;
            if (exchange.getRequestURI().getRawPath().equals("/health")) {
                payload = "{\"status\":\"ready\"}";
            } else if (exchange.getRequestURI().getRawPath().equals("/stats")) {
                payload = "{\"calls\":" + CALLS.get() + "}";
            } else {
                byte[] body = exchange.getRequestBody().readAllBytes();
                CALLS.incrementAndGet();
                String headers = exchange.getRequestHeaders().entrySet().stream()
                        .map(entry -> quote(entry.getKey().toLowerCase(Locale.ROOT)) + ":"
                                + entry.getValue().stream().map(EchoServer::quote).collect(Collectors.joining(",", "[", "]")))
                        .collect(Collectors.joining(",", "{", "}"));
                payload = "{\"method\":" + quote(exchange.getRequestMethod())
                        + ",\"path\":" + quote(exchange.getRequestURI().toASCIIString())
                        + ",\"headers\":" + headers + ",\"bodyBytes\":" + body.length
                        + ",\"bodyBase64\":" + quote(Base64.getEncoder().encodeToString(body)) + "}";
            }
            byte[] encoded = payload.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, encoded.length);
            exchange.getResponseBody().write(encoded);
        }
    }

    private static String quote(String value) {
        var json = new StringBuilder("\"");
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '"' -> json.append("\\\"");
                case '\\' -> json.append("\\\\");
                default -> {
                    if (character < 0x20) json.append(String.format(Locale.ROOT, "\\u%04x", (int) character));
                    else json.append(character);
                }
            }
        }
        return json.append('"').toString();
    }
}
