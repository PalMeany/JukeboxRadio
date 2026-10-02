package su.nuv.radio.catalog.spotify;

/**
 * @param market        ISO country for availability, e.g. "US"; blank lets Spotify decide
 * @param embedFallback read public embed pages when the Web API cannot answer (no keys, or a
 *                      playlist the app owner does not own)
 */
public record SpotifyConfig(String clientId, String clientSecret, String market, boolean embedFallback,
                            int collectionLimit) {

    public boolean hasCredentials() {
        return this.clientId != null && !this.clientId.isBlank()
                && this.clientSecret != null && !this.clientSecret.isBlank();
    }
}
