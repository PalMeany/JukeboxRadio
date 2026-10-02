package su.nuv.radio.audio;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * The folder yt-dlp downloads tracks into. Kept under a size cap by dropping the least recently
 * used files; a file touched in the last {@link #IN_USE_MS} may be playing and is never dropped.
 */
public final class AudioCache {

    private static final long IN_USE_MS = 30 * 60_000L;

    private final Path dir;
    private final long maxBytes;
    private final Logger logger;

    public AudioCache(Path dir, long maxBytes, Logger logger) {
        this.dir = dir.toAbsolutePath().normalize();
        this.maxBytes = maxBytes;
        this.logger = logger;
    }

    public Path dir() {
        return this.dir;
    }

    /** Marks a file as just used, so trimming keeps it. */
    public void touch(Path file) {
        try {
            Files.setLastModifiedTime(file, FileTime.fromMillis(System.currentTimeMillis()));
        } catch (IOException ignored) {
            // trimming may then drop it a little early; the next play downloads it again
        }
    }

    /** Deletes the oldest files until the folder fits the cap. Leftover partial downloads go too. */
    public synchronized void trim() {
        final List<Path> files = new ArrayList<>();
        try (Stream<Path> listing = Files.list(this.dir)) {
            listing.filter(Files::isRegularFile).forEach(files::add);
        } catch (IOException missing) {
            return;
        }
        final long now = System.currentTimeMillis();
        files.sort(Comparator.comparingLong(AudioCache::modified));
        long total = files.stream().mapToLong(AudioCache::size).sum();
        for (Path file : files) {
            final boolean idle = now - modified(file) > IN_USE_MS;
            final boolean partial = file.getFileName().toString().endsWith(".part");
            if (idle && (partial || total > this.maxBytes)) {
                final long size = size(file);
                try {
                    Files.deleteIfExists(file);
                    total -= size;
                } catch (IOException error) {
                    this.logger.fine(() -> "Could not delete cached " + file + ": " + error.getMessage());
                }
            }
        }
    }

    private static long modified(Path file) {
        try {
            return Files.getLastModifiedTime(file).toMillis();
        } catch (IOException gone) {
            return 0;
        }
    }

    private static long size(Path file) {
        try {
            return Files.size(file);
        } catch (IOException gone) {
            return 0;
        }
    }
}
