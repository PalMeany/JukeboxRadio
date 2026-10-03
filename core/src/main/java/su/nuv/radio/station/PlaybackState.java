package su.nuv.radio.station;

public enum PlaybackState {
    /** Nothing current. */
    IDLE,
    /** Looking up and buffering the current track. */
    LOADING,
    PLAYING,
    PAUSED,
    /** The current track could not be played; the station moves on shortly. */
    FAILED
}
