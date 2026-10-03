package su.nuv.radio.live;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.playback.AudioFrame;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import su.nuv.radio.audio.AudioEngine;
import su.nuv.radio.audio.AudioResolver;
import su.nuv.radio.audio.PcmFrames;
import su.nuv.radio.audio.YoutubeSettings;
import su.nuv.radio.catalog.model.ResolveResult;
import su.nuv.radio.catalog.model.TrackMeta;
import su.nuv.radio.catalog.spotify.SpotifyCatalog;
import su.nuv.radio.catalog.spotify.SpotifyConfig;
import su.nuv.radio.catalog.youtube.YouTubeCatalog;
import su.nuv.radio.menu.art.CoverService;
import su.nuv.radio.util.Http;
import su.nuv.radio.util.NetProxy;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Hits the real network. Run with: ./gradlew test -Plive */
@Tag("live")
class LivePipelineTest {

    private static final Logger LOG = Logger.getLogger("live");

    /** {@code -Pproxy=http://host:port} sends the whole live run through that proxy, as network.proxy does. */
    static NetProxy proxy() {
        final NetProxy proxy = NetProxy.parse(System.getProperty("radio.live.proxy", "")).orElse(null);
        Http.useProxy(proxy);
        return proxy;
    }

    @BeforeAll
    static void routeThroughProxy() {
        proxy();
    }

    @Test
    void spotifyPlaylistToPcm() throws Exception {
        final SpotifyCatalog spotify = new SpotifyCatalog(new SpotifyConfig("", "", "US", true, 50), LOG);
        final ResolveResult result = spotify.resolve("https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M?si=abc")
                .get(20, TimeUnit.SECONDS);
        assertTrue(result instanceof ResolveResult.Many);
        final List<TrackMeta> tracks = ((ResolveResult.Many) result).collection().tracks();
        System.out.println("playlist: " + ((ResolveResult.Many) result).collection().title() + ", " + tracks.size());
        assertFalse(tracks.isEmpty());
        final TrackMeta first = spotify.enrich(tracks.getFirst()).get(20, TimeUnit.SECONDS);
        System.out.println("first: " + first.artistLine() + " — " + first.title() + " " + first.durationMs()
                + "ms cover=" + first.coverUrl());
        assertTrue(first.coverUrl() != null);

        final var cover = new CoverService(LOG).get(first.coverUrl()).get(20, TimeUnit.SECONDS);
        assertTrue(cover.isPresent());
        System.out.printf("label colour #%06X%n", cover.get().labelColor());

        final AudioEngine engine = new AudioEngine(new YoutubeSettings(
                List.of("MUSIC", "WEB", "MWEB", "WEBEMBEDDED", "ANDROID_VR", "TV"), "", "", "", "", ""), proxy(), LOG);
        try {
            final su.nuv.radio.audio.YtDlp ytDlp = new su.nuv.radio.audio.YtDlp(LOG, List.of(), proxy());
            ytDlp.install(System.getProperty("radio.live.ytdlp", ""), java.nio.file.Path.of("build", "bin"), false, false);
            final AudioTrack audio = new AudioResolver(engine, ytDlp, AudioResolver.Mode.AUTO, LOG).resolve(first)
                    .get(60, TimeUnit.SECONDS);
            System.out.println("matched: " + audio.getInfo().author + " — " + audio.getInfo().title + " "
                    + audio.getInfo().length + "ms " + audio.getInfo().uri);
            final AudioPlayer player = engine.createPlayer();
            player.addListener(new com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter() {
                @Override
                public void onTrackException(AudioPlayer p, AudioTrack t, com.sedmelluq.discord.lavaplayer.tools.FriendlyException e) {
                    System.out.println("TRACK EXCEPTION: " + e.getMessage());
                    Throwable c = e.getCause();
                    while (c != null) {
                        System.out.println("  cause: " + c);
                        c = c.getCause();
                    }
                }

                @Override
                public void onTrackEnd(AudioPlayer p, AudioTrack t, com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason r) {
                    System.out.println("TRACK END: " + r);
                }
            });
            player.playTrack(audio);
            int frames = 0;
            long energy = 0;
            final long deadline = System.currentTimeMillis() + 30_000;
            while (frames < 250 && System.currentTimeMillis() < deadline) {
                final AudioFrame frame = player.provide(100, TimeUnit.MILLISECONDS);
                if (frame == null) {
                    continue;
                }
                final short[] mono = PcmFrames.stereoBeToMono(frame.getData(), frame.getDataLength());
                for (short s : mono) {
                    energy += Math.abs(s);
                }
                frames++;
            }
            System.out.println("decoded frames: " + frames + ", mean |sample| " + (frames == 0 ? 0 : energy / (frames * 960L)));
            player.destroy();
            assertTrue(frames >= 250, "decoded only " + frames + " frames");
            assertTrue(energy > 0, "silence");
        } finally {
            engine.shutdown();
        }
    }

    @Test
    void youtubeLink() throws Exception {
        final AudioEngine engine = new AudioEngine(new YoutubeSettings(
                List.of("MUSIC", "WEB", "MWEB", "WEBEMBEDDED", "ANDROID_VR", "TV"), "", "", "", "", ""), proxy(), LOG);
        try {
            final ResolveResult result = new YouTubeCatalog(engine, 50)
                    .resolve("https://www.youtube.com/watch?v=dQw4w9WgXcQ").get(30, TimeUnit.SECONDS);
            final TrackMeta track = ((ResolveResult.Single) result).track();
            System.out.println("youtube: " + track.artistLine() + " — " + track.title() + " " + track.durationMs()
                    + " cover=" + track.coverUrl());
            assertTrue(track.durationMs() > 0);
        } finally {
            engine.shutdown();
        }
    }
}
