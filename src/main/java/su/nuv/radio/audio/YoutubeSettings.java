package su.nuv.radio.audio;

import java.util.List;

/**
 * YouTube access tuning. YouTube changes often; these are the knobs youtube-source exposes.
 *
 * @param clients           client names in try order (MUSIC, WEB, MWEB, WEBEMBEDDED, ANDROID_VR, TV, ...)
 * @param oauthRefreshToken refresh token for OAuth (TV clients), blank to skip
 * @param poToken           proof-of-origin token for the WEB client, blank to skip
 * @param visitorData       visitor data paired with {@code poToken}
 * @param remoteCipherUrl   URL of a yt-cipher server, blank to decipher locally
 */
public record YoutubeSettings(List<String> clients, String oauthRefreshToken, String poToken, String visitorData,
                              String remoteCipherUrl, String remoteCipherPassword) {
}
