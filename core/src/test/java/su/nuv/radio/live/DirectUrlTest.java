package su.nuv.radio.live;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.playback.AudioFrame;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import su.nuv.radio.audio.AudioEngine;
import su.nuv.radio.audio.YoutubeSettings;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("live")
class DirectUrlTest {

    @Test
    void playsDirectUrl() throws Exception {
        final String url = System.getProperty("radio.live.url", "");
        if (url.isBlank()) {
            return;
        }
        final AudioEngine engine = new AudioEngine(new YoutubeSettings(List.of("MUSIC"), "", "", "", "", ""),
                LivePipelineTest.proxy(), Logger.getLogger("live"));
        try {
            final AudioTrack track = (AudioTrack) engine.load(url).get(30, TimeUnit.SECONDS).orElseThrow();
            System.out.println("loaded: " + track.getClass().getSimpleName() + " len=" + track.getDuration());
            final AudioPlayer player = engine.createPlayer();
            player.playTrack(track);
            int frames = 0;
            final long start = System.currentTimeMillis();
            while (frames < 500 && System.currentTimeMillis() - start < 30_000) {
                final AudioFrame frame = player.provide(100, TimeUnit.MILLISECONDS);
                if (frame != null) {
                    frames++;
                }
            }
            System.out.println("frames=" + frames + " in " + (System.currentTimeMillis() - start) + "ms");
            assertTrue(frames >= 500);
        } finally {
            engine.shutdown();
        }
    }
}
