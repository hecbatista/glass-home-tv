package io.github.hecbatista.glasshome;

import org.junit.Test;

import java.util.Calendar;
import java.util.TimeZone;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class StandbyFaceTest {
    private static final TimeZone NY = TimeZone.getTimeZone("America/New_York");

    @Test public void nightRunsFrom22To07WithoutAnAlarm() {
        assertFalse(StandbyFace.night(21 * 60 + 59, false, -1));
        assertTrue(StandbyFace.night(22 * 60, false, -1));
        assertTrue(StandbyFace.night(3 * 60, false, -1));
        assertTrue(StandbyFace.night(6 * 60 + 59, false, -1));
        assertFalse(StandbyFace.night(7 * 60, false, -1));
        assertFalse(StandbyFace.night(12 * 60, false, -1));
    }

    @Test public void anAlarmEndsTheNightAtItsSunrise() {
        int fourForty = 4 * 60 + 40, eightForty = 8 * 60 + 40;
        assertTrue(StandbyFace.night(4 * 60 + 39, false, fourForty));
        assertFalse(StandbyFace.night(fourForty, false, fourForty));     // the sunrise takes over
        assertTrue(StandbyFace.night(8 * 60 + 30, false, eightForty));   // a 9:00 weekend alarm keeps it red
        assertFalse(StandbyFace.night(eightForty, false, eightForty));
        // An afternoon alarm doesn't move the night's end.
        assertFalse(StandbyFace.night(7 * 60 + 5, false, 15 * 60));
    }

    @Test public void sleepModeIsAlwaysNight() {
        assertTrue(StandbyFace.night(14 * 60, true, -1));
    }

    @Test public void theWakeMinuteComesFromAnAlarmWithin14Hours() {
        Calendar c = Calendar.getInstance(NY);
        c.clear();
        c.set(2026, 9, 25, 23, 0, 0);
        long now = c.getTimeInMillis();
        AlarmPlan plan = AlarmPlan.next(now, 7 * 60, 20, AlarmPlan.EVERY_DAY, NY);
        assertEquals(6 * 60 + 40, StandbyFace.wakeMinute(plan, now, NY));
        AlarmPlan far = AlarmPlan.next(now, 7 * 60, 20, AlarmPlan.WEEKENDS, NY);   // Saturday: days away
        assertEquals(-1, StandbyFace.wakeMinute(far, now, NY));
        assertEquals(-1, StandbyFace.wakeMinute(null, now, NY));
    }

    @Test public void theDriftStaysInItsBoxAndMovesAPixelAtATime() {
        int maxStep = 0;
        int[] prev = StandbyFace.drift(0, 1.3);
        for (long t = 10_000; t <= 24 * 3600_000L; t += 10_000) {
            int[] o = StandbyFace.drift(t, 1.3);
            assertTrue(Math.abs(o[0]) <= 24 && Math.abs(o[1]) <= 16);
            maxStep = Math.max(maxStep, Math.max(Math.abs(o[0] - prev[0]), Math.abs(o[1] - prev[1])));
            prev = o;
        }
        assertTrue("step " + maxStep, maxStep <= 1);
    }

    @Test public void theMorningBacklightRisesFromTheFloor() {
        assertEquals(Sunrise.FLOOR, StandbyFace.morningBacklight(0), 0f);
        assertEquals(StandbyFace.DAY_BACKLIGHT, StandbyFace.morningBacklight(1), 1e-6f);
    }

    @Test public void theNightMatrixMapsWhiteToTheTintAndBlendsFromNone() {
        float[] m = StandbyFace.nightMatrix(0xFFB81C0C, 1f);
        float red = m[0] + m[1] + m[2], green = m[5] + m[6] + m[7], blue = m[10] + m[11] + m[12];
        // White maps to 60% of the tint: #6E1107, darker than the clock.
        assertEquals(0x6E / 255f, red, 0.003f);
        assertEquals(0x11 / 255f, green, 0.003f);
        assertEquals(0x07 / 255f, blue, 0.003f);
        float[] none = StandbyFace.nightMatrix(0xFFB81C0C, 0f);
        assertEquals(1f, none[0], 0f);
        assertEquals(0f, none[1], 0f);
    }

    private static long at(int year, int month, int day, int hour, int minute) {
        Calendar c = Calendar.getInstance(NY);
        c.clear();
        c.set(year, month, day, hour, minute);
        return c.getTimeInMillis();
    }

    @Test public void nightClockRunsUntilTheNextSeven() {
        assertEquals(at(2026, Calendar.OCTOBER, 27, 7, 0), StandbyFace.nightEndAfter(at(2026, Calendar.OCTOBER, 27, 6, 30), NY));
        assertEquals(at(2026, Calendar.OCTOBER, 28, 7, 0), StandbyFace.nightEndAfter(at(2026, Calendar.OCTOBER, 27, 7, 0), NY));
        assertEquals(at(2026, Calendar.OCTOBER, 28, 7, 0), StandbyFace.nightEndAfter(at(2026, Calendar.OCTOBER, 27, 7, 30), NY));
        assertEquals(at(2026, Calendar.OCTOBER, 28, 7, 0), StandbyFace.nightEndAfter(at(2026, Calendar.OCTOBER, 27, 23, 0), NY));
        assertEquals(at(2026, Calendar.OCTOBER, 28, 7, 0), StandbyFace.nightEndAfter(at(2026, Calendar.OCTOBER, 27, 15, 0), NY));
    }

    @Test public void nightClockKeepsSevenAcrossTheClockChanges() {
        // 1 November 2026, 2:00 back to 1:00; 8 March 2026, 2:00 on to 3:00.
        long fallBack = StandbyFace.nightEndAfter(at(2026, Calendar.OCTOBER, 31, 23, 0), NY);
        assertEquals(at(2026, Calendar.NOVEMBER, 1, 7, 0), fallBack);
        assertEquals(9 * 60 * 60_000L, fallBack - at(2026, Calendar.OCTOBER, 31, 23, 0));
        long springOn = StandbyFace.nightEndAfter(at(2026, Calendar.MARCH, 7, 23, 0), NY);
        assertEquals(at(2026, Calendar.MARCH, 8, 7, 0), springOn);
        assertEquals(7 * 60 * 60_000L, springOn - at(2026, Calendar.MARCH, 7, 23, 0));
    }

    @Test public void nightClockIsNightUntilItsEndOrItIsCleared() {
        long three = at(2026, Calendar.OCTOBER, 27, 15, 0);
        StandbyFace.forceNight(three, NY);
        try {
            assertTrue(StandbyFace.forced(three));
            assertTrue(StandbyFace.forced(at(2026, Calendar.OCTOBER, 28, 6, 59)));
            assertFalse(StandbyFace.forced(at(2026, Calendar.OCTOBER, 28, 7, 0)));   // the rule takes over
            StandbyFace.clearForcedNight();
            assertFalse(StandbyFace.forced(three));
        } finally {
            StandbyFace.clearForcedNight();
        }
    }
}
