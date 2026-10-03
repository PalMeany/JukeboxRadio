package su.nuv.radio.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import su.nuv.radio.catalog.CatalogException;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/** One shared HTTP client and worker pool for every network call the plugin makes. */
public final class Http {

    public static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0 Safari/537.36";

    private static final ExecutorService EXECUTOR = Executors.newThreadPerTaskExecutor(
            Thread.ofVirtual().name("jukeboxradio-io-", 0).factory());

    private static volatile HttpClient CLIENT = build(null);

    private Http() {
    }

    private static HttpClient build(NetProxy proxy) {
        final HttpClient.Builder builder = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(8))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .executor(EXECUTOR);
        if (proxy != null) {
            builder.proxy(ProxySelector.of(new InetSocketAddress(proxy.host(), proxy.port())));
        }
        return builder.build();
    }

    /** Routes every later request through {@code proxy}, or directly when null. Call before any request. */
    public static void useProxy(NetProxy proxy) {
        CLIENT = build(proxy);
    }

    public static ExecutorService executor() {
        return EXECUTOR;
    }

    public static HttpClient client() {
        return CLIENT;
    }

    public static HttpRequest.Builder request(String url) {
        return HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .header("User-Agent", USER_AGENT)
                .header("Accept-Language", "en-US,en;q=0.8,ru;q=0.6");
    }

    public static CompletableFuture<HttpResponse<String>> send(HttpRequest request) {
        return CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    /** GET returning parsed JSON; non-2xx statuses fail with {@link HttpStatusException}. */
    public static CompletableFuture<JsonElement> getJson(HttpRequest request) {
        return send(request).thenApply(response -> {
            if (response.statusCode() / 100 != 2) {
                throw new HttpStatusException(response.statusCode(), response.body());
            }
            return JsonParser.parseString(response.body());
        });
    }

    public static CompletableFuture<String> getText(HttpRequest request) {
        return send(request).thenApply(response -> {
            if (response.statusCode() / 100 != 2) {
                throw new HttpStatusException(response.statusCode(), response.body());
            }
            return response.body();
        });
    }

    /** Blocking byte download with a size cap, for cover art. Call off the main thread. */
    public static byte[] downloadBytes(String url, int maxBytes) throws IOException, InterruptedException {
        final HttpResponse<InputStream> response = CLIENT.send(request(url).GET().build(),
                HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() / 100 != 2) {
            response.body().close();
            throw new IOException("HTTP " + response.statusCode() + " for " + url);
        }
        try (InputStream in = response.body()) {
            final byte[] data = in.readNBytes(maxBytes + 1);
            if (data.length > maxBytes) {
                throw new IOException("Image larger than " + maxBytes + " bytes: " + url);
            }
            return data;
        }
    }

    public static String form(Map<String, String> fields) {
        return fields.entrySet().stream()
                .map(e -> encode(e.getKey()) + "=" + encode(e.getValue()))
                .collect(Collectors.joining("&"));
    }

    public static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /** Unwraps CompletionException layers to the real cause. */
    public static Throwable unwrap(Throwable error) {
        Throwable current = error;
        while ((current instanceof java.util.concurrent.CompletionException
                || current instanceof java.util.concurrent.ExecutionException) && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    /** A message fit for players: catalog messages pass through, anything else is generic. */
    public static String playerMessage(Throwable error, String fallback) {
        final Throwable cause = unwrap(error);
        if (cause instanceof CatalogException) {
            return cause.getMessage();
        }
        if (cause instanceof java.net.http.HttpTimeoutException || cause instanceof java.net.ConnectException) {
            return "Сервис не отвечает. Попробуйте ещё раз.";
        }
        return fallback;
    }

    public static final class HttpStatusException extends RuntimeException {

        private final int status;

        public HttpStatusException(int status, String body) {
            super("HTTP " + status + (body == null ? "" : ": " + body.substring(0, Math.min(300, body.length()))));
            this.status = status;
        }

        public int status() {
            return this.status;
        }
    }
}
