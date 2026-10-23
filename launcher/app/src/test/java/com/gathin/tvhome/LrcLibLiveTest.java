package com.gathin.tvhome;

import org.junit.Assume;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;

/**
 * The lyrics lookup against the real LRCLIB. Skipped unless LYRICS_LIVE=1, so ordinary test runs never go to the
 * network. It prints how many lines it found and when the first ones start, never the lyrics themselves:
 *
 *     LYRICS_LIVE=1 ./gradlew testReleaseUnitTest --tests com.gathin.tvhome.LrcLibLiveTest
 */
public class LrcLibLiveTest {
    private static void look(String title, String artist, String album, long durationMs) throws Exception {
        long t0 = System.currentTimeMillis();
        LrcLib.Result r = new LrcLib().find(title, artist, album, durationMs);
        long took = System.currentTimeMillis() - t0;
        String what = r.instrumental ? "instrumental" : r.lyrics == null ? "no synced lyrics"
                : r.lyrics.size() + " lines, first at " + r.lyrics.times[0] + " ms, second at "
                + (r.lyrics.size() > 1 ? r.lyrics.times[1] + " ms" : "-");
        System.out.println(title + " / " + artist + ": " + what + " (" + took + " ms)");
    }

    @Test public void songsFromThisTv() throws Exception {
        Assume.assumeTrue("set LYRICS_LIVE=1 to ask the real LRCLIB", "1".equals(System.getenv("LYRICS_LIVE")));
        // Songs Spotify reported on this account during testing, as the panel would ask for them.
        look("Feel No Ways", "Drake", "Views", 241_000);
        look("Solar Eclipse (feat. Don Toliver)", "Drake, Don Toliver", "", 0);
        look("Nights", "Frank Ocean", "Blonde", 307_151);
        look("Warm Static", "Corridor", "", 0);
        assertNotNull(new LrcLib().find("Feel No Ways", "Drake", "Views", 241_000).lyrics);
    }
}
