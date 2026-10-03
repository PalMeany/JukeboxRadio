package su.nuv.radio.audio;

import su.nuv.radio.catalog.CatalogException;
import su.nuv.radio.util.Http;
import su.nuv.radio.util.NetProxy;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * yt-dlp as the YouTube stream extractor. YouTube regularly breaks in-process extractors; yt-dlp
 * is updated within days, so the radio asks it for the direct audio URL and lets lavaplayer
 * decode that URL. The binary is taken from config, or downloaded from the official GitHub
 * release and verified against the release's SHA2-256SUMS.
 */
public final class YtDlp {

    private static final String RELEASES = "https://github.com/yt-dlp/yt-dlp/releases/latest/download/";

    private final Logger logger;
    private final List<String> extraArgs;
    private final List<String> proxyArgs;
    private final Semaphore slots = new Semaphore(3);
    private volatile Path binary;

    /** @param proxy where yt-dlp sends its requests, or null for a direct connection */
    public YtDlp(Logger logger, List<String> extraArgs, NetProxy proxy) {
        this.logger = logger;
        this.extraArgs = List.copyOf(extraArgs);
        this.proxyArgs = proxy == null ? List.of() : List.of("--proxy", proxy.url());
    }

    public boolean available() {
        return this.binary != null;
    }

    /**
     * Finds or installs the binary. {@code configured} wins when it points at a file; otherwise
     * {@code yt-dlp} on the PATH; otherwise, when allowed, a verified download into {@code binDir}.
     */
    public void install(String configured, Path binDir, boolean autoDownload, boolean autoUpdate) {
        if (configured != null && !configured.isBlank()) {
            final Path path = Path.of(configured);
            if (Files.isExecutable(path)) {
                this.binary = path;
                this.logger.info("yt-dlp: " + path);
                return;
            }
            this.logger.warning("yt-dlp path " + configured + " is not an executable file");
        }
        final Optional<Path> onPath = findOnPath();
        if (onPath.isPresent()) {
            this.binary = onPath.get();
            this.logger.info("yt-dlp found on PATH: " + this.binary);
            return;
        }
        final String asset = assetName();
        final Path local = binDir.resolve(asset.endsWith(".exe") ? "yt-dlp.exe" : "yt-dlp");
        if (Files.isExecutable(local)) {
            this.binary = local;
            if (autoUpdate) {
                this.selfUpdate();
            }
            return;
        }
        if (!autoDownload) {
            this.logger.warning("yt-dlp not found and auto-download is off: YouTube playback uses the built-in "
                    + "extractor, which YouTube often blocks.");
            return;
        }
        if (asset.isEmpty()) {
            this.logger.warning("No yt-dlp build for " + System.getProperty("os.name") + "/" + System.getProperty("os.arch")
                    + "; install yt-dlp and set audio.yt-dlp.path");
            return;
        }
        try {
            this.download(asset, local);
            this.binary = local;
            this.logger.info("yt-dlp downloaded to " + local);
        } catch (IOException | InterruptedException | RuntimeException error) {
            if (error instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            this.logger.log(Level.WARNING, "Could not download yt-dlp; set audio.yt-dlp.path to an installed copy", error);
        }
    }

    private static Optional<Path> findOnPath() {
        final String path = System.getenv("PATH");
        if (path == null) {
            return Optional.empty();
        }
        final boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
        for (String dir : path.split(File.pathSeparator)) {
            final Path candidate = Path.of(dir, windows ? "yt-dlp.exe" : "yt-dlp");
            if (Files.isExecutable(candidate)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    /** The release asset for this machine, or empty when yt-dlp ships none. */
    static String assetName() {
        final String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        final String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        final boolean arm = arch.contains("aarch64") || arch.contains("arm64");
        if (os.contains("win")) {
            return arm ? "yt-dlp_arm64.exe" : "yt-dlp.exe";
        }
        if (os.contains("mac")) {
            return "yt-dlp_macos";
        }
        if (os.contains("linux")) {
            final boolean musl = Files.exists(Path.of("/etc/alpine-release"));
            if (arm) {
                return musl ? "yt-dlp_musllinux_aarch64" : "yt-dlp_linux_aarch64";
            }
            return musl ? "yt-dlp_musllinux" : "yt-dlp_linux";
        }
        return "";
    }

    private void download(String asset, Path target) throws IOException, InterruptedException {
        Files.createDirectories(target.getParent());
        final String sums = Http.client().send(Http.request(RELEASES + "SHA2-256SUMS").GET().build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)).body();
        String expected = null;
        for (String line : sums.split("\n")) {
            final String[] parts = line.trim().split("\\s+");
            if (parts.length == 2 && parts[1].equals(asset)) {
                expected = parts[0].toLowerCase(Locale.ROOT);
            }
        }
        if (expected == null) {
            throw new IOException("SHA2-256SUMS has no entry for " + asset);
        }
        final Path temp = Files.createTempFile(target.getParent(), "yt-dlp", ".part");
        try {
            final HttpResponse<InputStream> response = Http.client().send(Http.request(RELEASES + asset)
                    .timeout(java.time.Duration.ofMinutes(5)).GET().build(), HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() / 100 != 2) {
                throw new IOException("HTTP " + response.statusCode() + " downloading " + asset);
            }
            try (InputStream in = response.body()) {
                Files.copy(in, temp, StandardCopyOption.REPLACE_EXISTING);
            }
            final String actual = sha256(temp);
            if (!actual.equals(expected)) {
                throw new IOException("Checksum mismatch for " + asset + ": " + actual + " != " + expected);
            }
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            if (!target.toFile().setExecutable(true)) {
                this.logger.warning("Could not mark " + target + " executable");
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private static String sha256(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            final MessageDigest digest = MessageDigest.getInstance("SHA-256");
            final byte[] buffer = new byte[1 << 16];
            int read;
            while ((read = in.read(buffer)) > 0) {
                digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private void selfUpdate() {
        CompletableFuture.runAsync(() -> {
            try {
                final List<String> command = new ArrayList<>(List.of(this.binary.toString(), "-U"));
                command.addAll(this.proxyArgs);
                final Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
                final String out = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip();
                process.waitFor(2, TimeUnit.MINUTES);
                this.logger.fine(() -> "yt-dlp -U: " + out);
            } catch (IOException | InterruptedException error) {
                this.logger.fine(() -> "yt-dlp self-update failed: " + error.getMessage());
            }
        }, Http.executor());
    }

    /** The direct audio stream URL of a video; runs yt-dlp off the calling thread. */
    public CompletableFuture<String> audioUrl(String videoUrl) {
        final Path bin = this.binary;
        if (bin == null) {
            return CompletableFuture.failedFuture(new CatalogException("yt-dlp не установлен."));
        }
        return CompletableFuture.supplyAsync(() -> {
            try {
                this.slots.acquire();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new CatalogException("Прервано.");
            }
            try {
                return this.run(bin, videoUrl);
            } finally {
                this.slots.release();
            }
        }, Http.executor());
    }

    /**
     * Downloads the audio of a video into {@code dir} and completes with the file. YouTube throttles
     * one long open-ended download (how lavaplayer streams a URL) to around real time, which stutters;
     * yt-dlp fetches in ranged chunks at full speed. An already downloaded file is reused.
     */
    public CompletableFuture<Path> downloadAudio(String videoUrl, Path dir) {
        final Path bin = this.binary;
        if (bin == null) {
            return CompletableFuture.failedFuture(new CatalogException("yt-dlp не установлен."));
        }
        return CompletableFuture.supplyAsync(() -> {
            try {
                this.slots.acquire();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new CatalogException("Прервано.");
            }
            try {
                return this.fetch(bin, videoUrl, dir);
            } finally {
                this.slots.release();
            }
        }, Http.executor());
    }

    private Path fetch(Path bin, String videoUrl, Path dir) {
        final List<String> command = new ArrayList<>(List.of(bin.toString(),
                "-f", "bestaudio[acodec=opus]/bestaudio[ext=m4a]/bestaudio/best",
                "-o", dir.resolve("%(id)s.%(ext)s").toString(),
                "--http-chunk-size", "5M", "--max-filesize", "200M",
                "--print", "after_move:filepath", "--no-simulate",
                "--no-playlist", "--no-warnings", "--no-progress", "--quiet"));
        command.addAll(this.proxyArgs);
        command.addAll(this.extraArgs);
        command.add("--");
        command.add(videoUrl);
        try {
            Files.createDirectories(dir);
            final Process process = new ProcessBuilder(command).start();
            final CompletableFuture<String> stderr = CompletableFuture.supplyAsync(() -> readAll(process.getErrorStream()),
                    Http.executor());
            final String out = readAll(process.getInputStream());
            if (!process.waitFor(3, TimeUnit.MINUTES)) {
                process.destroyForcibly();
                throw new CatalogException("YouTube отдаёт трек слишком долго.");
            }
            final Path file = out.lines().map(String::strip).filter(line -> !line.isEmpty()).map(Path::of)
                    .filter(Files::isRegularFile).reduce((first, second) -> second).orElse(null);
            if (process.exitValue() != 0 || file == null) {
                final String error = stderr.completeOnTimeout("", 2, TimeUnit.SECONDS).join();
                this.logger.fine(() -> "yt-dlp download failed for " + videoUrl + ": " + error);
                throw new CatalogException(friendly(error));
            }
            return file;
        } catch (IOException error) {
            throw new CatalogException("Не удалось запустить yt-dlp.", error);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new CatalogException("Прервано.");
        }
    }

    private String run(Path bin, String videoUrl) {
        final List<String> command = new ArrayList<>(List.of(bin.toString(),
                "-f", "bestaudio[acodec=opus]/bestaudio[ext=m4a]/bestaudio/best",
                "-g", "--no-playlist", "--no-warnings", "--no-progress", "--quiet"));
        command.addAll(this.proxyArgs);
        command.addAll(this.extraArgs);
        command.add("--");
        command.add(videoUrl);
        try {
            final Process process = new ProcessBuilder(command).start();
            final CompletableFuture<String> stderr = CompletableFuture.supplyAsync(() -> readAll(process.getErrorStream()),
                    Http.executor());
            final String out = readAll(process.getInputStream());
            if (!process.waitFor(45, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new CatalogException("YouTube отвечает слишком долго.");
            }
            final String url = out.lines().map(String::strip).filter(line -> line.startsWith("http")).findFirst()
                    .orElse(null);
            if (process.exitValue() != 0 || url == null) {
                final String error = stderr.completeOnTimeout("", 2, TimeUnit.SECONDS).join();
                this.logger.fine(() -> "yt-dlp failed for " + videoUrl + ": " + error);
                throw new CatalogException(friendly(error));
            }
            return url;
        } catch (IOException error) {
            throw new CatalogException("Не удалось запустить yt-dlp.", error);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new CatalogException("Прервано.");
        }
    }

    private static String readAll(InputStream in) {
        try (in) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException error) {
            return "";
        }
    }

    static String friendly(String stderr) {
        final String text = stderr == null ? "" : stderr.toLowerCase(Locale.ROOT);
        if (text.contains("sign in") || text.contains("bot")) {
            return "YouTube просит вход: задайте cookies для yt-dlp в config.yml.";
        }
        if (text.contains("private") || text.contains("unavailable") || text.contains("removed")) {
            return "Видео недоступно.";
        }
        if (text.contains("age")) {
            return "Видео с возрастным ограничением.";
        }
        return "YouTube не отдал аудио.";
    }
}
