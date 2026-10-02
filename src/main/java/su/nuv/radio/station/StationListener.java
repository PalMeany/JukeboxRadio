package su.nuv.radio.station;

/** Notified on the main thread whenever a station's visible state changes. */
public interface StationListener {

    enum Change {
        /** A different track became current (or playback ended). */
        TRACK,
        /** Playing, paused, loading or failed. */
        STATE,
        /** Entries were added, removed or reordered. */
        QUEUE,
        /** A cover or other metadata finished loading. */
        META,
        /** Volume or repeat mode. */
        SETTINGS
    }

    void stationChanged(RadioStation station, Change change);
}
