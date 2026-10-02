package su.nuv.radio.catalog.model;

import java.util.List;
import java.util.Objects;

/**
 * Provider-agnostic description of one track. Everything the radio shows or plays is derived from
 * this record, so a new metadata provider only has to fill it in.
 *
 * @param provider   id of the {@link su.nuv.radio.catalog.MusicCatalog} that produced it ("spotify", "youtube", ...)
 * @param id         the provider's own id for the track
 * @param title      track title as the provider spells it
 * @param artists    performers, main artist first; may be empty
 * @param album      album or collection name, {@code null} when unknown
 * @param durationMs length in milliseconds, {@code 0} when unknown
 * @param coverUrl   cover art URL, {@code null} until known (see {@link su.nuv.radio.catalog.MusicCatalog#enrich})
 * @param url        canonical public link, {@code null} when the provider has none
 * @param streamRef  identifier the audio engine can load directly (e.g. a YouTube URL), or {@code null}
 *                   when the audio has to be found by searching for title and artist
 */
public record TrackMeta(
        String provider,
        String id,
        String title,
        List<String> artists,
        String album,
        long durationMs,
        String coverUrl,
        String url,
        String streamRef
) {

    public TrackMeta {
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(id, "id");
        title = title == null || title.isBlank() ? "Без названия" : title.strip();
        artists = artists == null ? List.of() : List.copyOf(artists);
        durationMs = Math.max(0, durationMs);
    }

    /** Stable identity across providers: {@code provider:id}. */
    public String key() {
        return this.provider + ":" + this.id;
    }

    /** Artists joined with commas, or an empty string. */
    public String artistLine() {
        return String.join(", ", this.artists);
    }

    /** The main artist, or an empty string. */
    public String mainArtist() {
        return this.artists.isEmpty() ? "" : this.artists.getFirst();
    }

    public TrackMeta withCover(String cover) {
        return new TrackMeta(this.provider, this.id, this.title, this.artists, this.album, this.durationMs,
                cover, this.url, this.streamRef);
    }

    public TrackMeta withAlbum(String albumName) {
        return new TrackMeta(this.provider, this.id, this.title, this.artists, albumName, this.durationMs,
                this.coverUrl, this.url, this.streamRef);
    }

    public TrackMeta withStreamRef(String ref) {
        return new TrackMeta(this.provider, this.id, this.title, this.artists, this.album, this.durationMs,
                this.coverUrl, this.url, ref);
    }

    public TrackMeta withDuration(long ms) {
        return new TrackMeta(this.provider, this.id, this.title, this.artists, this.album, ms,
                this.coverUrl, this.url, this.streamRef);
    }
}
