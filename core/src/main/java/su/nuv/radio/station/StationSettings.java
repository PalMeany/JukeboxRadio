package su.nuv.radio.station;

/**
 * @param distance       how far the radio is heard, in blocks
 * @param defaultVolume  0..100 for a new station
 * @param maxQueue       total queued tracks per station
 * @param maxPerPlayer   queued tracks one player may have waiting, 0 for no limit
 * @param failSkipTicks  pause before moving past a track that failed to load
 */
public record StationSettings(float distance, int defaultVolume, int maxQueue, int maxPerPlayer, int failSkipTicks,
                              int idleMinutes) {
}
