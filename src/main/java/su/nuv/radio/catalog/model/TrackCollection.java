package su.nuv.radio.catalog.model;

import java.util.List;

/**
 * A group of tracks behind one link: an album, a playlist or an artist's top tracks.
 *
 * @param total number of tracks the provider reports, which may exceed {@code tracks.size()} when
 *              the list was capped by {@code radio.collection-limit}
 */
public record TrackCollection(
        String provider,
        String id,
        Kind kind,
        String title,
        String owner,
        String coverUrl,
        String url,
        List<TrackMeta> tracks,
        int total
) {

    public enum Kind {
        ALBUM("Альбом"),
        PLAYLIST("Плейлист"),
        ARTIST("Исполнитель");

        private final String label;

        Kind(String label) {
            this.label = label;
        }

        public String label() {
            return this.label;
        }
    }

    public TrackCollection {
        tracks = tracks == null ? List.of() : List.copyOf(tracks);
        total = Math.max(total, tracks.size());
    }
}
