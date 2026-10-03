package su.nuv.radio.util;

import java.net.URI;
import java.util.Locale;
import java.util.Optional;

/**
 * The HTTP proxy every outgoing request goes through: the plugin's HTTP client, lavaplayer and
 * yt-dlp. For servers where YouTube is blocked. HTTP only: java.net.http cannot talk to SOCKS.
 */
public record NetProxy(String host, int port) {

    /** {@code http://host:port} or {@code host:port}; blank means no proxy. */
    public static Optional<NetProxy> parse(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String text = value.strip();
        if (!text.contains("://")) {
            text = "http://" + text;
        }
        final URI uri;
        try {
            uri = URI.create(text);
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("Not a proxy address: " + value);
        }
        final String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http")) {
            throw new IllegalArgumentException("Only http:// proxies are supported, got " + value);
        }
        if (uri.getHost() == null || uri.getPort() < 1 || uri.getPort() > 65535) {
            throw new IllegalArgumentException("Proxy needs a host and a port, e.g. http://127.0.0.1:3128, got " + value);
        }
        if (uri.getUserInfo() != null) {
            throw new IllegalArgumentException("Proxy logins are not supported: " + value);
        }
        return Optional.of(new NetProxy(uri.getHost(), uri.getPort()));
    }

    public String url() {
        return "http://" + this.host + ":" + this.port;
    }
}
