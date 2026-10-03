package su.nuv.radio.menu.art;

import su.nuv.radio.util.Http;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Downloads and prepares covers once per URL; the results stay in a small LRU cache. */
public final class CoverService {

    private static final int MAX_BYTES = 4 * 1024 * 1024;

    private final Logger logger;
    private final Map<String, CompletableFuture<Optional<CoverArt>>> cache = new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, CompletableFuture<Optional<CoverArt>>> eldest) {
            return this.size() > 256;
        }
    };

    public CoverService(Logger logger) {
        this.logger = logger;
    }

    /** The prepared cover, or empty when there is no URL or it cannot be read. Never fails. */
    public synchronized CompletableFuture<Optional<CoverArt>> get(String url) {
        if (url == null || url.isBlank()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        return this.cache.computeIfAbsent(url, key -> CompletableFuture.supplyAsync(() -> this.load(key), Http.executor()));
    }

    /** The cover if it is already prepared, without waiting. */
    public synchronized Optional<CoverArt> now(String url) {
        if (url == null) {
            return Optional.empty();
        }
        final CompletableFuture<Optional<CoverArt>> future = this.cache.get(url);
        return future != null && future.isDone() && !future.isCompletedExceptionally() ? future.join() : Optional.empty();
    }

    private Optional<CoverArt> load(String url) {
        try {
            final byte[] bytes = Http.downloadBytes(url, MAX_BYTES);
            final BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image == null) {
                this.logger.fine(() -> "Unreadable cover format: " + url);
                return Optional.empty();
            }
            return Optional.of(CoverArt.from(image));
        } catch (IOException | RuntimeException error) {
            this.logger.log(Level.FINE, "Cover download failed: " + url, error);
            return Optional.empty();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }
}
