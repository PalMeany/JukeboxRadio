package su.nuv.radio;

import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import org.junit.jupiter.api.Test;
import su.nuv.radio.audio.AudioResolver;
import su.nuv.radio.audio.PcmFrames;
import su.nuv.radio.catalog.apple.AppleMusicCatalog;
import su.nuv.radio.catalog.apple.AppleMusicLink;
import su.nuv.radio.catalog.model.TrackCollection;
import su.nuv.radio.catalog.model.SearchPage;
import su.nuv.radio.catalog.model.TrackMeta;
import su.nuv.radio.catalog.spotify.SpotifyLink;
import su.nuv.radio.catalog.youtube.YouTubeCatalog;
import su.nuv.radio.menu.art.CoverArt;
import su.nuv.radio.menu.pack.PackBuilder;
import su.nuv.radio.menu.text.FontChars;
import su.nuv.radio.menu.text.Glyphs;
import su.nuv.radio.menu.text.TitleBuilder;
import su.nuv.radio.util.Durations;
import su.nuv.radio.util.Links;
import su.nuv.radio.util.NetProxy;

import java.awt.Color;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UnitTest {

    @Test
    void spotifyLinks() {
        assertEquals(new SpotifyLink(SpotifyLink.Type.TRACK, "70cHKK8bHAfJrOGVnfRG9J"),
                SpotifyLink.parse("https://open.spotify.com/track/70cHKK8bHAfJrOGVnfRG9J?si=1234").orElseThrow());
        assertEquals(SpotifyLink.Type.PLAYLIST,
                SpotifyLink.parse("https://open.spotify.com/intl-ru/playlist/37i9dQZF1DXcBWIGoYBM5M").orElseThrow().type());
        assertEquals(SpotifyLink.Type.ALBUM, SpotifyLink.parse("spotify:album:4aawyAB9vmqN3uQ7FjRGTy").orElseThrow().type());
        assertTrue(SpotifyLink.isShortLink("https://spotify.link/AbCdEf12"));
        assertFalse(SpotifyLink.parse("https://example.com/track/abc").isPresent());
    }

    @Test
    void resyncWaitsForQuiet() {
        final short[] quiet = new short[PcmFrames.SAMPLES_PER_FRAME];
        final short[] soft = new short[PcmFrames.SAMPLES_PER_FRAME];
        soft[10] = 1_500;
        final short[] loud = new short[PcmFrames.SAMPLES_PER_FRAME];
        loud[10] = 20_000;
        final su.nuv.radio.audio.Resync resync = new su.nuv.radio.audio.Resync(0);
        // never inside the first 30 s, even in silence
        assertFalse(resync.due(quiet, 29_000));
        assertFalse(resync.due(quiet, 29_020));
        // after that, two quiet frames in a row; one is not enough
        assertFalse(resync.due(quiet, 31_000));
        assertTrue(resync.due(quiet, 31_020));
        // a soft passage only counts once it has waited 90 s
        assertFalse(resync.due(soft, 31_020 + 60_000));
        assertFalse(resync.due(soft, 31_040 + 60_000));
        assertFalse(resync.due(soft, 31_020 + 90_000));
        assertTrue(resync.due(soft, 31_040 + 90_000));
        // loud music with no break at all is realigned after 4 minutes
        final long t = 31_040 + 90_000;
        assertFalse(resync.due(loud, t + 200_000));
        assertFalse(resync.due(loud, t + 240_000));
        assertTrue(resync.due(loud, t + 240_020));
        // a new relay may realign at once, at the next quiet frames
        resync.hurry(t + 240_100);
        assertFalse(resync.due(quiet, t + 240_120));
        assertTrue(resync.due(quiet, t + 240_140));
    }

    @Test
    void relayMeshExtendsReach() {
        final java.util.UUID world = java.util.UUID.randomUUID();
        final su.nuv.radio.relay.BlockPos jukebox = new su.nuv.radio.relay.BlockPos(world, 0, 64, 0);
        final su.nuv.radio.relay.BlockPos a = new su.nuv.radio.relay.BlockPos(world, 40, 64, 0);
        final su.nuv.radio.relay.BlockPos b = new su.nuv.radio.relay.BlockPos(world, 85, 64, 0);
        final su.nuv.radio.relay.BlockPos c = new su.nuv.radio.relay.BlockPos(world, 130, 64, 0);
        final su.nuv.radio.relay.BlockPos lost = new su.nuv.radio.relay.BlockPos(world, 300, 64, 0);
        final su.nuv.radio.relay.BlockPos elsewhere = new su.nuv.radio.relay.BlockPos(java.util.UUID.randomUUID(), 0, 64, 0);
        // a chain of hops each within 48 reaches 130 blocks out; the far and other-world ones stay off
        assertEquals(Set.of(a, b, c),
                su.nuv.radio.relay.RelayNetwork.active(jukebox, List.of(c, lost, b, a, elsewhere), 48));
        // take the middle one away and the chain breaks behind it
        assertEquals(Set.of(a), su.nuv.radio.relay.RelayNetwork.active(jukebox, List.of(a, c), 48));
        assertEquals(45.0, su.nuv.radio.relay.RelayNetwork.gap(b, jukebox, Set.of(a)), 1e-9);
        assertTrue(Double.isInfinite(su.nuv.radio.relay.RelayNetwork.gap(elsewhere, jukebox, Set.of(a))));
    }

    @Test
    void linkCodesAndPositions() {
        final Set<String> seen = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            final String code = su.nuv.radio.relay.LinkCodes.generate(seen::contains);
            assertTrue(seen.add(code));
            assertEquals(code, su.nuv.radio.relay.LinkCodes.normalize(" " + code.toLowerCase() + " ").orElseThrow());
        }
        assertEquals("K7Q2P", su.nuv.radio.relay.LinkCodes.normalize("k7q-2p").orElseThrow());
        assertTrue(su.nuv.radio.relay.LinkCodes.normalize("K0Q2P").isEmpty()); // 0 is not in the alphabet
        assertTrue(su.nuv.radio.relay.LinkCodes.normalize("K7Q2").isEmpty());
        final su.nuv.radio.relay.BlockPos pos = new su.nuv.radio.relay.BlockPos(java.util.UUID.randomUUID(), -12, -60, 3);
        assertEquals(pos, su.nuv.radio.relay.BlockPos.parse(pos.serialize()).orElseThrow());
        assertTrue(su.nuv.radio.relay.BlockPos.parse("nope").isEmpty());
    }

    @Test
    void relayStoreRoundTripsAndShifts(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) {
        final java.nio.file.Path file = dir.resolve("links.yml");
        final java.util.logging.Logger log = java.util.logging.Logger.getAnonymousLogger();
        final java.util.UUID world = java.util.UUID.randomUUID();
        final su.nuv.radio.relay.BlockPos jukebox = new su.nuv.radio.relay.BlockPos(world, 1, 2, 3);
        final su.nuv.radio.relay.BlockPos note = new su.nuv.radio.relay.BlockPos(world, -5, 70, 8);
        final su.nuv.radio.relay.RelayStore store = new su.nuv.radio.relay.RelayStore(file, log);
        store.putJukebox("K7Q2P", jukebox);
        store.putRelay(note, "K7Q2P", 150);
        store.write(store.snapshot());

        final su.nuv.radio.relay.RelayStore loaded = new su.nuv.radio.relay.RelayStore(file, log);
        loaded.load();
        assertEquals("K7Q2P", loaded.codeOf(jukebox).orElseThrow());
        assertEquals(new su.nuv.radio.relay.RelayStore.Relay("K7Q2P", 100), loaded.relay(note).orElseThrow());

        assertEquals(List.of("K7Q2P"), loaded.shift(List.of(note), 0, 0, 1));
        assertTrue(loaded.relay(note).isEmpty());
        assertEquals(Set.of(note.offset(0, 0, 1)), loaded.relaysOf("K7Q2P").keySet());
        // a broken jukebox drops its code but leaves its relays waiting
        loaded.removeJukebox(jukebox);
        assertTrue(loaded.jukeboxOf("K7Q2P").isEmpty());
        assertEquals(1, loaded.relaysOf("K7Q2P").size());
    }

    @Test
    void iconsAreCentred() {
        su.nuv.radio.menu.art.Icons.masks().forEach((name, rows) -> {
            assertEquals(su.nuv.radio.menu.art.Icons.GRID, rows.length, name);
            int left = 99, right = -1, top = 99, bottom = -1;
            for (int y = 0; y < rows.length; y++) {
                assertEquals(su.nuv.radio.menu.art.Icons.GRID, rows[y].length(), name + " row " + y);
                for (int x = 0; x < rows[y].length(); x++) {
                    if (rows[y].charAt(x) == '#') {
                        left = Math.min(left, x);
                        right = Math.max(right, x);
                        top = Math.min(top, y);
                        bottom = Math.max(bottom, y);
                    }
                }
            }
            // the play triangle sits half a pixel right on purpose: its visual centre is left of its box
            final double slack = name.equals(su.nuv.radio.menu.art.Icons.PLAY) ? 0.5 : 0.0;
            assertTrue(Math.abs((left + right) / 2.0 - 5) <= slack, name + " is off-centre horizontally");
            assertTrue(Math.abs((top + bottom) / 2.0 - 5) <= 0.5, name + " is off-centre vertically");
        });
    }

    @Test
    void appleMusicLinks() {
        final AppleMusicLink track = AppleMusicLink.parse(
                "https://music.apple.com/ru/album/%D0%B4%D0%BE-%D1%81%D0%B8%D1%85-%D0%BF%D0%BE%D1%80/1698975492?i=1698975725&uo=4")
                .orElseThrow();
        assertEquals(new AppleMusicLink(AppleMusicLink.Type.TRACK, "1698975725", "ru"), track);
        assertEquals(new AppleMusicLink(AppleMusicLink.Type.ALBUM, "1698975492", "us"),
                AppleMusicLink.parse("music.apple.com/album/1698975492").orElseThrow());
        assertEquals(AppleMusicLink.Type.TRACK, AppleMusicLink.parse("https://music.apple.com/gb/song/x/1698975725").orElseThrow().type());
        assertEquals(new AppleMusicLink(AppleMusicLink.Type.PLAYLIST, "pl.u-e98lGAgsWaXm1", "ru"),
                AppleMusicLink.parse("https://music.apple.com/ru/playlist/my-mix/pl.u-e98lGAgsWaXm1").orElseThrow());
        assertEquals(AppleMusicLink.Type.ARTIST,
                AppleMusicLink.parse("https://music.apple.com/ru/artist/mayot/1458525039").orElseThrow().type());
        assertTrue(AppleMusicLink.isAppleMusic(Links.clean("Послушай https://music.apple.com/ru/album/x/1?i=2 !")));
        assertFalse(AppleMusicLink.parse("https://music.apple.com/ru/playlist/x/123").isPresent());
        assertFalse(AppleMusicLink.parse("https://apple.com/ru/album/x/123").isPresent());
    }

    @Test
    void appleMusicParsing() {
        assertEquals(List.of("MORGENSHTERN", "SODA LUV", "OG Buda", "MAYOT"),
                AppleMusicCatalog.splitArtists("MORGENSHTERN, SODA LUV & OG Buda & MAYOT"));
        final com.google.gson.JsonObject row = com.google.gson.JsonParser.parseString("""
                {"wrapperType":"track","kind":"song","trackId":1698975725,"artistName":"MAYOT","collectionName":"ОБА",
                 "trackName":"до сих пор","trackTimeMillis":191429,
                 "artworkUrl100":"https://is1-ssl.mzstatic.com/a/cover.jpg/100x100bb.jpg",
                 "trackViewUrl":"https://music.apple.com/ru/album/x/1698975492?i=1698975725&uo=4"}""").getAsJsonObject();
        final TrackMeta meta = AppleMusicCatalog.lookupTrack(row);
        assertEquals("до сих пор", meta.title());
        assertEquals("MAYOT", meta.mainArtist());
        assertEquals(191_429, meta.durationMs());
        assertEquals("https://is1-ssl.mzstatic.com/a/cover.jpg/300x300bb.jpg", meta.coverUrl());
        assertEquals("https://music.apple.com/ru/album/x/1698975492?i=1698975725", meta.url());

        final String page = "<html><script type=\"application/json\" id=\"serialized-server-data\">"
                + "{\"data\":[{\"data\":{\"sections\":["
                + "{\"itemKind\":\"containerDetailHeaderLockup\",\"items\":[{\"title\":\"Top 100: Russia\","
                + "\"subtitleLinks\":[{\"title\":\"Apple Music\"}],"
                + "\"artwork\":{\"dictionary\":{\"url\":\"https://a/{w}x{h}bb.{f}\"}}}]},"
                + "{\"itemKind\":\"trackLockup\",\"items\":[{\"title\":\"Шадэ\",\"duration\":168942,"
                + "\"artistName\":\"By Индия, Xcho & МОТ\",\"tertiaryLinks\":[{\"title\":\"Шадэ - Single\"}],"
                + "\"contentDescriptor\":{\"kind\":\"song\",\"identifiers\":{\"storeAdamID\":\"6766252279\"},"
                + "\"url\":\"https://music.apple.com/ru/album/x/6766252274?i=6766252279\"}},"
                + "{\"title\":\"Клип\",\"contentDescriptor\":{\"kind\":\"musicVideo\",\"identifiers\":{\"storeAdamID\":\"1\"}}}]}"
                + "]}}]}</script></html>";
        final TrackCollection playlist = new AppleMusicCatalog(100, java.util.logging.Logger.getAnonymousLogger())
                .parsePlaylistPage(AppleMusicLink.parse("https://music.apple.com/ru/playlist/pl.abc").orElseThrow(), page);
        assertEquals("Top 100: Russia", playlist.title());
        assertEquals("https://a/300x300bb.jpg", playlist.coverUrl());
        assertEquals(1, playlist.tracks().size());
        final TrackMeta first = playlist.tracks().getFirst();
        assertEquals(List.of("By Индия", "Xcho", "МОТ"), first.artists());
        assertEquals("Шадэ - Single", first.album());
        assertEquals(168_942, first.durationMs());
    }

    @Test
    void cushionPrebuffersAndRefills() {
        // a fake lavaplayer: hands out whatever frames the test has made "available"
        final java.util.ArrayDeque<com.sedmelluq.discord.lavaplayer.track.playback.AudioFrame> ready = new java.util.ArrayDeque<>();
        final var player = (com.sedmelluq.discord.lavaplayer.player.AudioPlayer) java.lang.reflect.Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{com.sedmelluq.discord.lavaplayer.player.AudioPlayer.class},
                (proxy, method, args) -> method.getName().equals("provide") && (args == null || args.length == 0) ? ready.poll() : null);
        final Runnable add = () -> {
            final byte[] pcm = new byte[PcmFrames.SAMPLES_PER_FRAME * 4];
            pcm[0] = 0x10; // left and right sample 0 = 0x1000, so a real frame is not silence
            pcm[2] = 0x10;
            ready.add(new com.sedmelluq.discord.lavaplayer.track.playback.ImmutableAudioFrame(0, pcm, 100,
                    su.nuv.radio.audio.AudioEngine.OUTPUT_FORMAT));
        };
        final su.nuv.radio.audio.FrameCushion cushion = new su.nuv.radio.audio.FrameCushion();
        for (int i = 0; i < 59; i++) {
            add.run();
        }
        assertEquals(0, cushion.next(player, false)[0], "59 frames: still filling");
        add.run();
        assertEquals(0x1000, cushion.next(player, false)[0], "60 frames: plays");
        for (int i = 0; i < 59; i++) {
            assertEquals(0x1000, cushion.next(player, false)[0]);
        }
        assertEquals(0, cushion.next(player, false)[0], "ran dry: silence, refill");
        add.run();
        assertEquals(0, cushion.next(player, false)[0], "one frame is not enough to restart");
        assertEquals(0x1000, cushion.next(player, true)[0], "track over: the tail plays anyway");
        assertFalse(cushion.hasTail());
        for (int i = 0; i < 80; i++) {
            add.run();
        }
        cushion.next(player, false);
        cushion.reset();
        assertEquals(0, cushion.next(player, false)[0], "reset drops what was queued");
    }

    @Test
    void volumeIsPerceptuallyEven() {
        assertEquals(10000, PcmFrames.applyVolume(new short[]{10000}, 100)[0]);
        assertEquals(0, PcmFrames.applyVolume(new short[]{10000}, 0)[0]);
        // loudness ~ amplitude^0.6: equal slider steps give equal loudness steps
        final double half = Math.pow(PcmFrames.applyVolume(new short[]{10000}, 50)[0] / 10000.0, 0.6);
        assertEquals(0.5, half, 0.01);
        final double tenth = Math.pow(PcmFrames.applyVolume(new short[]{10000}, 10)[0] / 10000.0, 0.6);
        assertEquals(0.1, tenth, 0.01);
    }

    @Test
    void audioFramesMatchVoiceChat() {
        // Simple Voice Chat plays 960-sample frames at 48 kHz; any other rate changes speed and pitch
        assertEquals(48_000, su.nuv.radio.audio.AudioEngine.OUTPUT_FORMAT.sampleRate);
        assertEquals(PcmFrames.SAMPLES_PER_FRAME, su.nuv.radio.audio.AudioEngine.OUTPUT_FORMAT.chunkSampleCount);
        assertEquals(2, su.nuv.radio.audio.AudioEngine.OUTPUT_FORMAT.channelCount);
    }

    @Test
    void proxyAddresses() {
        assertEquals(new NetProxy("127.0.0.1", 3128), NetProxy.parse("http://127.0.0.1:3128").orElseThrow());
        assertEquals(new NetProxy("proxy.lan", 8080), NetProxy.parse(" proxy.lan:8080 ").orElseThrow());
        assertEquals("http://127.0.0.1:3128", NetProxy.parse("127.0.0.1:3128").orElseThrow().url());
        assertFalse(NetProxy.parse("").isPresent());
        assertFalse(NetProxy.parse(null).isPresent());
        assertThrows(IllegalArgumentException.class, () -> NetProxy.parse("socks5://127.0.0.1:1080"));
        assertThrows(IllegalArgumentException.class, () -> NetProxy.parse("http://127.0.0.1"));
        assertThrows(IllegalArgumentException.class, () -> NetProxy.parse("http://user:pass@127.0.0.1:3128"));
    }

    @Test
    void linkCleanup() {
        assertEquals("https://open.spotify.com/track/abc123",
                Links.clean("Послушай это: https://open.spotify.com/track/abc123."));
        assertTrue(Links.looksLikeLink("youtu.be/dQw4w9WgXcQ"));
        assertFalse(Links.looksLikeLink("daft punk around the world"));
        assertEquals("https://youtu.be/x", Links.withScheme("youtu.be/x"));
    }

    @Test
    void youtubeLinksRecognised() {
        final YouTubeCatalog catalog = new YouTubeCatalog(null, 10);
        assertTrue(catalog.canResolve("https://www.youtube.com/watch?v=dQw4w9WgXcQ"));
        assertTrue(catalog.canResolve("https://music.youtube.com/watch?v=dQw4w9WgXcQ&list=RDAMVM"));
        assertTrue(catalog.canResolve("https://youtu.be/dQw4w9WgXcQ"));
        assertTrue(catalog.canResolve("https://www.youtube.com/playlist?list=PL123"));
        assertFalse(catalog.canResolve("https://open.spotify.com/track/x"));
    }

    @Test
    void pcmDownmix() {
        final byte[] frame = new byte[3840];
        // left = 1000, right = 3000 -> mono 2000 for the first sample
        frame[0] = (byte) (1000 >> 8);
        frame[1] = (byte) 1000;
        frame[2] = (byte) (3000 >> 8);
        frame[3] = (byte) 3000;
        // left = -2, right = -4 -> -3
        frame[4] = (byte) 0xFF;
        frame[5] = (byte) 0xFE;
        frame[6] = (byte) 0xFF;
        frame[7] = (byte) 0xFC;
        final short[] mono = PcmFrames.stereoBeToMono(frame, frame.length);
        assertEquals(960, mono.length);
        assertEquals(2000, mono[0]);
        assertEquals(-3, mono[1]);
        assertEquals(960, PcmFrames.stereoBeToMono(new byte[100], 100).length);
    }

    @Test
    void matchScoringPrefersOriginal() {
        final TrackMeta meta = new TrackMeta("spotify", "id", "Nicole Kidman", List.of("ADÉLA"), null, 181_270, null,
                null, null);
        final double topic = AudioResolver.score(new AudioTrackInfo("Nicole Kidman", "ADÉLA - Topic", 181_000, "a",
                false, "u"), meta, 0);
        final double live = AudioResolver.score(new AudioTrackInfo("Nicole Kidman (Live at Arena)", "ADÉLA", 240_000,
                "b", false, "u"), meta, 1);
        final double alive = AudioResolver.score(new AudioTrackInfo("Stayin Alive", "Bee Gees", 181_000, "c", false,
                "u"), new TrackMeta("spotify", "x", "Stayin Alive", List.of("Bee Gees"), null, 181_000, null, null, null), 0);
        assertTrue(topic > live);
        assertTrue(alive > 60, "'alive' must not count as 'live': " + alive);
    }

    @Test
    void searchPaging() {
        final SearchPage page = new SearchPage("q", 0, 5, 12, List.of(meta("a"), meta("b"), meta("c"), meta("d"), meta("e")));
        assertTrue(page.hasNext());
        assertFalse(page.hasPrevious());
        assertFalse(new SearchPage("q", 10, 5, 12, List.of(meta("a"), meta("b"))).hasNext());
    }

    @Test
    void russianPlurals() {
        assertEquals("1 трек", Durations.tracks(1));
        assertEquals("3 трека", Durations.tracks(3));
        assertEquals("11 треков", Durations.tracks(11));
        assertEquals("21 трек", Durations.tracks(21));
        assertEquals("3:07", Durations.clock(187_000));
        assertEquals("1:02:44", Durations.clock(3_764_000));
    }

    @Test
    void glyphFitting() {
        final String text = "Очень длинное название трека, которое не влезает";
        final String fitted = Glyphs.fit(text, 0.3125, 300);
        assertTrue(fitted.endsWith("…"));
        assertTrue(Glyphs.width(fitted, 0.3125) <= 300);
        final List<String> lines = Glyphs.wrap(text, 0.4375, 360, 2);
        assertEquals(2, lines.size());
        lines.forEach(line -> assertTrue(Glyphs.width(line, 0.4375) <= 360, line));
        // real font widths: 'i' is narrower than 'm'
        assertTrue(Glyphs.advance('i') < Glyphs.advance('m'));
    }

    @Test
    void coverColours() {
        final CoverArt sunset = CoverArt.fromPixels(gradient(250, 140, 60, 120, 30, 170));
        final float[] hsb = Color.RGBtoHSB((sunset.labelColor() >> 16) & 0xFF, (sunset.labelColor() >> 8) & 0xFF,
                sunset.labelColor() & 0xFF, null);
        assertTrue(hsb[1] > 0.3f && hsb[2] > 0.6f, "label must be vivid and bright enough on black vinyl");
        final int mono = CoverArt.fromPixels(gradient(20, 20, 20, 235, 235, 235)).labelColor();
        final float[] monoHsb = Color.RGBtoHSB((mono >> 16) & 0xFF, (mono >> 8) & 0xFF, mono & 0xFF, null);
        assertTrue(monoHsb[1] < 0.15f, "monochrome covers get a paper label");
        assertEquals(32 * 32, sunset.grid(32).length);
        assertEquals(64, sunset.grid(8).length);
    }

    private static int[] gradient(int r0, int g0, int b0, int r1, int g1, int b1) {
        final int n = CoverArt.SOURCE;
        final int[] px = new int[n * n];
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                final double t = y / (double) n;
                px[y * n + x] = (int) (r0 + (r1 - r0) * t) << 16 | (int) (g0 + (g1 - g0) * t) << 8
                        | (int) (b0 + (b1 - b0) * t);
            }
        }
        return px;
    }

    @Test
    void spacesAddUp() {
        for (int advance : new int[]{-513, -159, -8, -1, 1, 7, 64, 300}) {
            int sum = 0;
            for (char c : FontChars.spaces(advance).toCharArray()) {
                sum += c >= FontChars.POS ? 1 << (c - FontChars.POS) : -(1 << (c - FontChars.NEG));
            }
            assertEquals(advance, sum, "advance " + advance);
        }
    }

    @Test
    void titleBuilderTracksPen() {
        final TitleBuilder t = new TitleBuilder();
        t.background(FontChars.BG_MAIN_L, 0, 176);
        assertEquals(177, t.x());
        t.text("Abc", 184, 20, 0x404040, TitleBuilder.Align.LEFT);
        assertEquals(184 + Glyphs.advanceSum("Abc"), t.x());
        t.bar(184, 90, 52, 10, 0xFF0000, 0x222222);
        assertEquals(184 + 104, t.x());
        assertEquals("Ab? c", Glyphs.sanitize("Ab日 c"));
    }

    @Test
    void packFontsStayLight() throws Exception {
        // every glyph in every font is baked by the client on each resource reload
        int glyphs = 0;
        try (java.util.zip.ZipInputStream zip = new java.util.zip.ZipInputStream(
                new java.io.ByteArrayInputStream(new PackBuilder(64).build().zip()))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (!entry.getName().startsWith("assets/jukeboxradio/font/")) {
                    continue;
                }
                final var root = com.google.gson.JsonParser.parseString(
                        new String(zip.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                for (var provider : root.getAsJsonArray("providers")) {
                    if (provider.getAsJsonObject().has("chars")) {
                        for (var row : provider.getAsJsonObject().getAsJsonArray("chars")) {
                            glyphs += (int) row.getAsString().codePoints().filter(cp -> cp != 0).count();
                        }
                    }
                }
            }
        }
        assertTrue(glyphs < 12_000, "glyphs in pack fonts: " + glyphs);
        assertEquals("jukeboxradio:t95", FontChars.textFont(95).asString());
        assertEquals("jukeboxradio:t95", FontChars.textFont(94).asString());
        assertEquals("jukeboxradio:t6", FontChars.textFont(0).asString());
        for (char c : "AzЁёЖщ—·…«»→−é".toCharArray()) {
            assertTrue(Glyphs.supported(c), "menu charset lost " + c);
        }
        assertEquals("? Tokyo", Glyphs.sanitize("東 Tokyo"));
    }

    @Test
    void packBuildsDeterministically() throws Exception {
        final PackBuilder.Built first = new PackBuilder(64).build();
        final PackBuilder.Built second = new PackBuilder(64).build();
        assertEquals(first.sha1(), second.sha1());
        final java.util.Set<String> names = new java.util.HashSet<>();
        try (java.util.zip.ZipInputStream zip = new java.util.zip.ZipInputStream(new java.io.ByteArrayInputStream(first.zip()))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                names.add(entry.getName());
                if (entry.getName().endsWith(".json") || entry.getName().endsWith(".mcmeta")) {
                    com.google.gson.JsonParser.parseString(new String(zip.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
                }
            }
        }
        assertTrue(names.contains("pack.mcmeta"));
        assertTrue(names.contains("assets/jukeboxradio/font/ui.json"));
        assertTrue(names.contains("assets/jukeboxradio/items/disc_spin.json"));
        assertTrue(names.contains("assets/jukeboxradio/textures/gui/main_l.png"));
        assertTrue(first.zip().length < 2_000_000, "pack size " + first.zip().length);
    }

    private static TrackMeta meta(String id) {
        return new TrackMeta("t", id, id, List.of(), null, 0, null, null, null);
    }
}
