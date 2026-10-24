package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** The full-screen lyrics' progress bar: its times, and how it follows the player without stepping back. */
public class SongProgressTest {
    @Test public void clockText() {
        assertEquals("0:00", SongProgress.clock(0));
        assertEquals("0:07", SongProgress.clock(7));
        assertEquals("3:41", SongProgress.clock(221));
        assertEquals("12:05", SongProgress.clock(725));
        assertEquals("59:59", SongProgress.clock(3599));
        assertEquals("1:00:00", SongProgress.clock(3600));
        assertEquals("1:02:03", SongProgress.clock(3723));
        assertEquals("10:00:09", SongProgress.clock(36009));
    }

    @Test public void theTwoTimesAlwaysAddUpToTheLength() {
        SongProgress p = new SongProgress();
        long dur = 200_900;   // 3:20.9: the length shows as 3:20
        for (long pos = 0; pos <= dur + 3000; pos += 137) {
            p.update("a", dur, pos);
            assertEquals(200, p.elapsedS() + p.remainingS());
        }
        p.update("a", dur, 59_999);
        assertEquals("0:59", p.elapsedText());
        assertEquals("−2:21", p.remainingText());
    }

    @Test public void pastTheEndStaysFull() {
        SongProgress p = new SongProgress();
        p.update("a", 180_000, 185_000);
        assertEquals(1f, p.fraction(), 0f);
        assertEquals("3:00", p.elapsedText());
        assertEquals("−0:00", p.remainingText());
    }

    @Test public void hiddenUntilTheLengthAndPositionAreKnown() {
        SongProgress p = new SongProgress();
        p.update("a", 180_000, -1);
        assertFalse(p.visible());
        p.update("a", 180_000, 1_000);
        assertTrue(p.visible());
        p.update("b", 0, 2_000);
        assertTrue(p.songChanged());
        assertFalse(p.visible());
        p.update("c", 200_000, -1);
        assertFalse(p.visible());   // a new song does not carry the last one's position
    }

    @Test public void aShortStepBackIsHeld() {
        SongProgress p = new SongProgress();
        p.update("a", 180_000, 60_000);
        assertFalse(p.update("a", 180_000, 58_200));   // a fresh report 1.8 s behind
        assertEquals(60_000, p.shownMs());
        p.update("a", 180_000, 59_900);
        assertEquals(60_000, p.shownMs());
        p.update("a", 180_000, 60_100);   // the clock passes it: follow again
        assertEquals(60_100, p.shownMs());
        p.update("a", 180_000, 60_500);   // forward corrections apply directly
        assertEquals(60_500, p.shownMs());
    }

    @Test public void aSeekOrRestartSnapsBack() {
        SongProgress p = new SongProgress();
        p.update("a", 180_000, 60_000);
        p.update("a", 180_000, 58_000);   // exactly 2 s back: a seek
        assertEquals(58_000, p.shownMs());
        p.update("a", 180_000, 1_000);   // Previous restarted the song
        assertEquals(1_000, p.shownMs());
    }

    @Test public void whilePausedAShortStepBackIsHeldToo() {
        SongProgress p = new SongProgress();
        p.update("a", 180_000, 60_000);   // paused here by the screen's own button
        p.update("a", 180_000, 59_400);   // the player's read: where it really stopped
        assertEquals(60_000, p.shownMs());
        p.update("a", 180_000, 61_000);   // forward applies at once, paused or not
        assertEquals(61_000, p.shownMs());
    }

    @Test public void aNewSongResetsAtOnce() {
        SongProgress p = new SongProgress();
        p.update("a", 180_000, 179_000);
        assertTrue(p.update("b", 240_000, 500));
        assertTrue(p.songChanged());
        assertEquals(500, p.shownMs());
        assertFalse(p.update("b", 240_000, 500));
        assertFalse(p.songChanged());
    }

    @Test public void fractionOfTheBar() {
        SongProgress p = new SongProgress();
        p.update("a", 200_000, 50_000);
        assertEquals(0.25f, p.fraction(), 1e-6f);
        p.update("a", 200_000, -5);   // unknown again: keep what is shown
        assertEquals(0.25f, p.fraction(), 1e-6f);
    }

    @Test public void aNewSongsFirstReportThatCarriesOnTheOldSongIsNotItsOwn() {
        assertTrue(SongProgress.carriesOn(218_400, 218_000));    // the old song's state, read with the new metadata
        assertTrue(SongProgress.carriesOn(216_500, 218_000));    // 1.5 s apart: still the old one
        assertFalse(SongProgress.carriesOn(216_400, 218_000));
        assertFalse(SongProgress.carriesOn(1_000, 218_000));     // a new song from its start
        assertFalse(SongProgress.carriesOn(4_800, 4_500));       // too early in to tell: take it
        assertFalse(SongProgress.carriesOn(60_000, 218_000));    // a new song started part-way
    }
}
