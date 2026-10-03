package su.nuv.radio.audio;

import com.sedmelluq.discord.lavaplayer.track.AudioItem;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import su.nuv.radio.catalog.CatalogException;
import su.nuv.radio.catalog.model.TrackMeta;

import java.text.Normalizer;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds playable audio for any {@link TrackMeta} in two steps.
 *
 * <ol>
 *   <li>Which video: tracks with a {@code streamRef} already name one; everything else (Spotify,
 *       future providers) is matched on YouTube Music by artist, title and duration.</li>
 *   <li>Which stream: yt-dlp extracts the direct audio URL (the reliable path), lavaplayer decodes
 *       it. Without yt-dlp, lavaplayer's own YouTube extractor is used.</li>
 * </ol>
 * Both steps are cached, and {@link #prefetch} warms them for the next track.
 */
public final class AudioResolver {

    public enum Mode {
        /** yt-dlp when installed, otherwise the built-in extractor. */
        AUTO,
        YT_DLP,
        LAVAPLAYER
    }

    private static final List<String> SUSPECT_WORDS = List.of(
            "live", "cover", "remix", "karaoke", "instrumental", "8d", "slowed", "sped up", "speed up",
            "nightcore", "reverb", "1 hour", "loop", "bass boosted", "reaction", "tutorial", "перевод", "кавер");
    private static final Pattern YOUTUBE = Pattern.compile("^https?://(?:www\\.|m\\.|music\\.)?(?:youtube\\.com|youtu\\.be)/",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern EXPIRE = Pattern.compile("[?&]expire=(\\d+)");

    private final AudioEngine engine;
    private final YtDlp ytDlp;
    private final Mode mode;
    private final java.util.logging.Logger logger;
    private final Map<String, String> videos = lru(1024);
    private final Map<String, CachedUrl> streams = lru(256);
    /** Downloads in flight or done, by video URL, so a prefetch and the play share one download. */
    private final Map<String, CompletableFuture<Path>> files = lru(256);
    private final AudioCache cache;

    private record CachedUrl(String url, long validUntil) {
    }

    public AudioResolver(AudioEngine engine, YtDlp ytDlp, Mode mode, java.util.logging.Logger logger) {
        this(engine, ytDlp, mode, null, logger);
    }

    /** @param cache where yt-dlp downloads tracks to play them from disk, or null to stream URLs */
    public AudioResolver(AudioEngine engine, YtDlp ytDlp, Mode mode, AudioCache cache, java.util.logging.Logger logger) {
        this.engine = engine;
        this.ytDlp = ytDlp;
        this.mode = mode;
        this.cache = cache;
        this.logger = logger;
    }

    private static <V> Map<String, V> lru(int size) {
        return Collections.synchronizedMap(new LinkedHashMap<>(64, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, V> eldest) {
                return this.size() > size;
            }
        });
    }

    public CompletableFuture<AudioTrack> resolve(TrackMeta meta) {
        if (meta.streamRef() != null && !YOUTUBE.matcher(meta.streamRef()).find()) {
            return this.loadDirect(meta.streamRef());
        }
        return this.video(meta).thenCompose(video -> this.playable(video, downloadable(meta)));
    }

    /** Long videos (mixes, streams) would make players wait for tens of megabytes: stream those. */
    private static boolean downloadable(TrackMeta meta) {
        return meta.durationMs() > 0 && meta.durationMs() <= 20 * 60_000L;
    }

    /** Resolves video and stream URL ahead of time; failures are ignored. */
    public void prefetch(TrackMeta meta) {
        if (meta.streamRef() != null && !YOUTUBE.matcher(meta.streamRef()).find()) {
            return;
        }
        this.video(meta).thenCompose(video -> {
            if (!this.useYtDlp()) {
                return CompletableFuture.completedFuture(null);
            }
            return this.cache != null && downloadable(meta) ? this.file(video).thenApply(file -> null)
                    : this.streamUrl(video).thenApply(url -> null);
        }).exceptionally(error -> null);
    }

    private boolean useYtDlp() {
        return this.mode != Mode.LAVAPLAYER && this.ytDlp != null && this.ytDlp.available();
    }

    // ------------------------------------------------------------------ step 1: the video

    private CompletableFuture<String> video(TrackMeta meta) {
        if (meta.streamRef() != null) {
            return CompletableFuture.completedFuture(meta.streamRef());
        }
        final String cached = this.videos.get(meta.key());
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }
        final String query = (meta.mainArtist() + " " + meta.title()).strip();
        return this.engine.load("ytmsearch:" + query)
                .thenCompose(first -> {
                    final Optional<AudioTrack> best = first.flatMap(item -> pick(item, meta));
                    if (best.isPresent()) {
                        return CompletableFuture.completedFuture(best);
                    }
                    return this.engine.load("ytsearch:" + meta.mainArtist() + " - " + meta.title() + " audio")
                            .thenApply(second -> second.flatMap(item -> pick(item, meta)));
                })
                .thenApply(found -> {
                    final AudioTrack track = found.orElseThrow(
                            () -> new CatalogException("Не нашлось аудио для «" + meta.title() + "»."));
                    this.videos.put(meta.key(), track.getInfo().uri);
                    return track.getInfo().uri;
                });
    }

    // ------------------------------------------------------------------ step 2: the stream

    private CompletableFuture<AudioTrack> playable(String videoUrl, boolean download) {
        if (!this.useYtDlp()) {
            if (this.mode == Mode.YT_DLP) {
                return CompletableFuture.failedFuture(new CatalogException("yt-dlp не установлен — см. config.yml."));
            }
            return this.loadDirect(videoUrl);
        }
        if (this.cache != null && download) {
            return this.file(videoUrl)
                    .thenCompose(file -> this.loadDirect(file.toString()))
                    .exceptionallyCompose(error -> {
                        this.files.remove(videoUrl);
                        this.logger.warning("yt-dlp download failed for " + videoUrl + ", streaming instead: "
                                + su.nuv.radio.util.Http.unwrap(error));
                        return this.streamed(videoUrl);
                    });
        }
        return this.streamed(videoUrl);
    }

    /** The whole track on disk; lavaplayer then reads it at any speed. */
    private CompletableFuture<Path> file(String videoUrl) {
        final CompletableFuture<Path> known = this.files.get(videoUrl);
        if (known != null && !known.isCompletedExceptionally()
                && (!known.isDone() || java.nio.file.Files.isRegularFile(known.join()))) {
            return known.thenApply(file -> {
                this.cache.touch(file);
                return file;
            });
        }
        final CompletableFuture<Path> download = this.ytDlp.downloadAudio(videoUrl, this.cache.dir())
                .whenComplete((file, error) -> this.cache.trim());
        this.files.put(videoUrl, download);
        return download;
    }

    private CompletableFuture<AudioTrack> streamed(String videoUrl) {
        return this.streamUrl(videoUrl)
                .thenCompose(this::loadDirect)
                .exceptionallyCompose(error -> {
                    this.streams.remove(videoUrl);
                    this.logger.warning("yt-dlp stream failed for " + videoUrl + ": "
                            + su.nuv.radio.util.Http.unwrap(error));
                    return this.mode == Mode.AUTO ? this.loadDirect(videoUrl) : CompletableFuture.failedFuture(error);
                });
    }

    private CompletableFuture<String> streamUrl(String videoUrl) {
        final CachedUrl cached = this.streams.get(videoUrl);
        if (cached != null && cached.validUntil() > System.currentTimeMillis()) {
            return CompletableFuture.completedFuture(cached.url());
        }
        return this.ytDlp.audioUrl(videoUrl).thenApply(url -> {
            this.streams.put(videoUrl, new CachedUrl(url, validUntil(url)));
            return url;
        });
    }

    static long validUntil(String url) {
        final Matcher matcher = EXPIRE.matcher(url);
        final long now = System.currentTimeMillis();
        if (matcher.find()) {
            final long expire = Long.parseLong(matcher.group(1)) * 1000L;
            return Math.max(now, expire - 10 * 60_000L);
        }
        return now + 30 * 60_000L;
    }

    private CompletableFuture<AudioTrack> loadDirect(String identifier) {
        return this.engine.load(identifier).thenApply(item -> item.flatMap(AudioResolver::firstTrack)
                .orElseThrow(() -> new CatalogException("Не удалось загрузить аудио.")));
    }

    private static Optional<AudioTrack> firstTrack(AudioItem item) {
        if (item instanceof AudioTrack track) {
            return Optional.of(track);
        }
        if (item instanceof AudioPlaylist playlist) {
            if (playlist.getSelectedTrack() != null) {
                return Optional.of(playlist.getSelectedTrack());
            }
            return playlist.getTracks().stream().findFirst();
        }
        return Optional.empty();
    }

    // ------------------------------------------------------------------ matching

    static Optional<AudioTrack> pick(AudioItem item, TrackMeta meta) {
        if (!(item instanceof AudioPlaylist playlist)) {
            return firstTrack(item);
        }
        AudioTrack best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        final List<AudioTrack> candidates = playlist.getTracks();
        for (int i = 0; i < Math.min(10, candidates.size()); i++) {
            final AudioTrack candidate = candidates.get(i);
            if (candidate.getInfo().isStream) {
                continue;
            }
            final double score = score(candidate.getInfo(), meta, i);
            if (score > bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        return bestScore < -40 ? Optional.empty() : Optional.ofNullable(best);
    }

    /** Higher is better. Public for tests. */
    public static double score(AudioTrackInfo info, TrackMeta meta, int rank) {
        double score = -rank * 2.0;
        if (meta.durationMs() > 0 && info.length > 0) {
            final long diff = Math.abs(info.length - meta.durationMs());
            if (diff <= 3_000) {
                score += 50;
            } else if (diff <= 10_000) {
                score += 30;
            } else if (diff <= 25_000) {
                score += 10;
            } else if (diff > 60_000) {
                score -= 40;
            }
        }
        final String title = normalize(info.title);
        final String author = normalize(info.author);
        final String wantedTitle = normalize(meta.title());
        final String wantedArtist = normalize(meta.mainArtist());
        if (!wantedTitle.isEmpty() && title.contains(wantedTitle)) {
            score += 20;
        }
        if (!wantedArtist.isEmpty() && (author.contains(wantedArtist) || title.contains(wantedArtist))) {
            score += 15;
        }
        if (info.author != null && info.author.endsWith(" - Topic")) {
            score += 10;
        }
        final String paddedTitle = " " + title + " ";
        final String paddedWanted = " " + wantedTitle + " ";
        for (String word : SUSPECT_WORDS) {
            if (paddedTitle.contains(" " + word + " ") && !paddedWanted.contains(" " + word + " ")) {
                score -= 30;
            }
        }
        return score;
    }

    static String normalize(String text) {
        if (text == null) {
            return "";
        }
        final String decomposed = Normalizer.normalize(text, Normalizer.Form.NFKD).replaceAll("\\p{M}", "");
        return decomposed.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N} ]", " ").replaceAll("\\s+", " ").strip();
    }
}
