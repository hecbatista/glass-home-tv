package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class StandbyLyricTest {
    private static final Lyrics SONG = Lyrics.parseLrc(
            "[00:10.00] First line\n[00:14.00] Second line\n[00:18.00]\n[00:40.00] After the break\n");

    @Test public void theIntroShowsNothing() {
        assertEquals(-1, StandbyLyric.lineAt(SONG, 5_000, -1));
    }

    @Test public void aLineShowsJustBeforeItsStamp() {
        assertEquals(-1, StandbyLyric.lineAt(SONG, 9_849, -1));
        assertEquals(0, StandbyLyric.lineAt(SONG, 9_850, -1));
        assertEquals(1, StandbyLyric.lineAt(SONG, 13_900, 0));
    }

    @Test public void aMarkedBreakEndsTheLineTenSecondsIn() {
        // Lyrics.parseLrc drops the empty line at 18 s, so the break is the ten-second rule's.
        assertEquals(1, StandbyLyric.lineAt(SONG, 20_000, 1));
        assertEquals(-1, StandbyLyric.lineAt(SONG, 24_000, 1));
        assertEquals(2, StandbyLyric.lineAt(SONG, 39_900, -1));
    }

    @Test public void aLineGoesAfterTenSecondsWithNoNextOne() {
        Lyrics long1 = Lyrics.parseLrc("[00:10.00] Held\n[00:40.00] Next\n");
        assertEquals(0, StandbyLyric.lineAt(long1, 19_800, 0));
        assertEquals(-1, StandbyLyric.lineAt(long1, 20_000, 0));
        assertEquals(1, StandbyLyric.lineAt(long1, 40_000, -1));
    }

    @Test public void aPollCorrectionNeverStepsBack() {
        // Shown: line 1 (from 14 s). Spotify's next answer puts the song at 13.2 s: keep line 1.
        assertEquals(1, StandbyLyric.lineAt(SONG, 13_200, 1));
        // A seek back to 11 s (more than 2 s before line 1): line 0.
        assertEquals(0, StandbyLyric.lineAt(SONG, 11_000, 1));
    }

    @Test public void lateLyricsOnlyWhenTheSongAlreadyFits() {
        assertTrue(StandbyLyric.fitsBeside(500, 300, 560));
        assertTrue(StandbyLyric.fitsBeside(560, 560, 560));
        assertFalse(StandbyLyric.fitsBeside(561, 300, 560));
        assertFalse(StandbyLyric.fitsBeside(300, 600, 560));
    }
}
