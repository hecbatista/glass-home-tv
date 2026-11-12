package io.github.hecbatista.glasshome;

import org.junit.Test;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/** The lyrics card's pieces without Android: LRC parsing, LRCLIB matching, and the playback clock. */
public class LyricsTest {
    /** Invented lines, in LRC as LRCLIB returns it (not a real song's lyrics). */
    static final String LRC = "[ar: Sample Artist]\n[ti: Night Drive]\n"
            + "[00:12.40] Streetlights hum along the avenue\n"
            + "[00:17.05] Radio low, the windows down\n"
            + "[00:21.9]\n"
            + "[00:25.600] Every sign is pointing back to you\n"
            + "[00:31.20][01:02.20] Holding on to the last of the light\n";

    @Test public void readsLrcLinesInTimeOrder() {
        Lyrics l = Lyrics.parseLrc(LRC);
        assertArrayEquals(new long[]{12_400, 17_050, 25_600, 31_200, 62_200}, l.times);
        assertEquals("Streetlights hum along the avenue", l.line(0));
        assertEquals("Holding on to the last of the light", l.line(3));
        assertEquals("Holding on to the last of the light", l.line(4));   // a line with two time stamps
        assertEquals("", l.line(9));
    }

    @Test public void theLinePlayingAtAPosition() {
        Lyrics l = Lyrics.parseLrc(LRC);
        assertEquals(-1, l.indexAt(0));            // the intro: before the first line
        assertEquals(-1, l.indexAt(12_399));
        assertEquals(0, l.indexAt(12_400));
        assertEquals(1, l.indexAt(20_000));
        assertEquals(1, l.indexAt(25_599));        // the empty break at 0:21.9 is not a line
        assertEquals(2, l.indexAt(25_600));
        assertEquals(4, l.indexAt(400_000));
    }

    @Test public void anOffsetShiftsEveryLine() {
        Lyrics l = Lyrics.parseLrc("[offset:+500]\n[00:10.00] One\n[00:20.00] Two");
        assertArrayEquals(new long[]{9_500, 19_500}, l.times);
        assertEquals(0, Lyrics.parseLrc("[offset:-250]\n[00:00.10] Start").times[0] - 350);
    }

    @Test public void textWithoutTimesIsNotSynced() {
        assertNull(Lyrics.parseLrc("Just plain words\nNo times at all"));
        assertNull(Lyrics.parseLrc(""));
        assertNull(Lyrics.parseLrc(null));
        assertNull(Lyrics.parseLrc("[00:01.00]\n[00:02.00]   "));
    }

    // ---- LRCLIB ----------------------------------------------------------------------------------------------

    /** Plays LRCLIB: scripted answers in order, and the URLs and headers it was asked with. */
    private static final class FakeLrcLib implements SpotifyApi.Transport {
        final Deque<SpotifyApi.Response> answers = new ArrayDeque<>();
        final List<String> urls = new ArrayList<>();
        final List<Map<String, String>> headers = new ArrayList<>();

        FakeLrcLib answer(int code, String body) {
            answers.add(new SpotifyApi.Response(code, body, 0));
            return this;
        }

        @Override
        public SpotifyApi.Response send(String method, String url, Map<String, String> h, String body) throws IOException {
            urls.add(url.replace(LrcLib.BASE, ""));
            headers.add(h);
            SpotifyApi.Response r = answers.poll();
            if (r == null) throw new AssertionError("unexpected request: " + url);
            return r;
        }
    }

    private static String record(double duration, String synced, boolean instrumental) {
        return "{\"id\":1,\"trackName\":\"Night Drive\",\"artistName\":\"Sample Artist\",\"albumName\":\"Sample Album\","
                + "\"duration\":" + duration + ",\"instrumental\":" + instrumental + ",\"plainLyrics\":\"x\","
                + "\"syncedLyrics\":" + (synced == null ? "null" : org.json.JSONObject.quote(synced)) + "}";
    }

    @Test public void theExactSongIsAskedForFirst() throws Exception {
        FakeLrcLib fake = new FakeLrcLib().answer(200, record(221, LRC, false));
        LrcLib.Result r = new LrcLib(LrcLib.BASE, fake).find("Night Drive", "Sample Artist", "Sample Album", 221_400);
        assertEquals(5, r.lyrics.size());
        assertEquals(List.of("/get?track_name=Night+Drive&artist_name=Sample+Artist&album_name=Sample+Album&duration=221"),
                fake.urls);
        assertEquals(LrcLib.USER_AGENT, fake.headers.get(0).get("User-Agent"));
    }

    @Test public void withoutAnExactMatchTheClosestLengthWins() throws Exception {
        String list = "[" + record(250, LRC, false) + "," + record(222, null, false) + ","
                + record(220, "[00:01.00] Closest", false) + "," + record(240, "[00:01.00] Far", false) + "]";
        FakeLrcLib fake = new FakeLrcLib().answer(404, "{\"code\":404}").answer(200, list);
        LrcLib.Result r = new LrcLib(LrcLib.BASE, fake).find("Night Drive", "Sample Artist", "Sample Album", 221_000);
        // 222 s has no synced lyrics; 220 s is within 3 s and has them; 240 and 250 s are other recordings.
        assertEquals("Closest", r.lyrics.line(0));
        assertEquals("/search?track_name=Night+Drive&artist_name=Sample+Artist", fake.urls.get(1));
    }

    @Test public void featuringIsTriedWithoutAndOnlyTheFirstArtistIsAsked() throws Exception {
        FakeLrcLib fake = new FakeLrcLib()
                .answer(404, "{}").answer(200, "[]")                         // full title: nothing
                .answer(404, "{}").answer(200, "[" + record(200, LRC, false) + "]");
        LrcLib.Result r = new LrcLib(LrcLib.BASE, fake).find("Night Drive (feat. Guest Singer)",
                "Sample Artist, Guest Singer", "Sample Album", 200_000);
        assertNotNull(r.lyrics);
        assertEquals("/get?track_name=Night+Drive+%28feat.+Guest+Singer%29&artist_name=Sample+Artist"
                + "&album_name=Sample+Album&duration=200", fake.urls.get(0));
        assertEquals("/get?track_name=Night+Drive&artist_name=Sample+Artist&album_name=Sample+Album&duration=200",
                fake.urls.get(2));
    }

    @Test public void noAlbumOrLengthGoesStraightToSearch() throws Exception {
        FakeLrcLib fake = new FakeLrcLib().answer(200, "[" + record(180, LRC, false) + "]");
        assertNotNull(new LrcLib(LrcLib.BASE, fake).find("Night Drive", "Sample Artist", null, 0).lyrics);
        assertEquals(1, fake.urls.size());
        assertTrue(fake.urls.get(0).startsWith("/search?"));
    }

    @Test public void instrumentalsAndPlainTextShowNoCard() throws Exception {
        LrcLib.Result instrumental = new LrcLib(LrcLib.BASE, new FakeLrcLib().answer(200, record(200, null, true)))
                .find("Night Drive", "Sample Artist", "Sample Album", 200_000);
        assertTrue(instrumental.instrumental);
        assertNull(instrumental.lyrics);
        // Plain lyrics only: nothing to follow the song with, so no card, and it counts as none.
        FakeLrcLib plain = new FakeLrcLib().answer(200, record(200, null, false)).answer(200, "[]");
        assertSame(LrcLib.Result.NONE, new LrcLib(LrcLib.BASE, plain).find("Night Drive", "Sample Artist", "Sample Album", 200_000));
    }

    @Test public void titlesAndArtists() {
        assertArrayEquals(new String[]{"Song (feat. X)", "Song"}, LrcLib.titles("Song (feat. X)"));
        assertArrayEquals(new String[]{"Song [with Y]", "Song"}, LrcLib.titles("Song [with Y]"));
        assertArrayEquals(new String[]{"Song - 2011 Remaster", "Song"}, LrcLib.titles("Song - 2011 Remaster"));
        assertArrayEquals(new String[]{"Song (Interlude)"}, LrcLib.titles("Song (Interlude)"));
        assertEquals("Drake", LrcLib.firstArtist("Drake, Don Toliver"));
        assertEquals("Simon", LrcLib.firstArtist("Simon & Garfunkel"));
        assertEquals("Sample Artist", LrcLib.firstArtist("Sample Artist"));
    }

    // ---- the clock and Spotify's position --------------------------------------------------------------------

    @Test public void theClockRunsWhilePlayingAndStopsWhenPaused() {
        PlaybackClock c = new PlaybackClock();
        c.set(81_234, 1_000, 1f);
        assertEquals(81_234, c.positionAt(1_000));
        assertEquals(84_234, c.positionAt(4_000));
        c.set(84_234, 4_000, 0f);
        assertEquals(84_234, c.positionAt(60_000));
    }

    @Test public void spotifySaysWhereTheSongIs() {
        SpotifyPlayback p = SpotifyPlayback.parse(SpotifyPlaybackTest.fixture("player-track.json"));
        assertEquals(81_234, p.progressMs);
        assertEquals(307_151, p.durationMs);
        assertEquals("Blonde", p.album);
    }
}
