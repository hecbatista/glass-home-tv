package com.gathin.tvhome;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SunriseAndTonesTest {
    @Test public void keyframesHitTheirStopsAndBlendBetween() {
        assertEquals(Sunrise.CLOCK[0], Sunrise.key(0f, Sunrise.CLOCK));
        assertEquals(Sunrise.CLOCK[2], Sunrise.key(0.5f, Sunrise.CLOCK));
        assertEquals(Sunrise.CLOCK[4], Sunrise.key(1f, Sunrise.CLOCK));
        assertEquals(Sunrise.CLOCK[4], Sunrise.key(3f, Sunrise.CLOCK));
        assertEquals(Sunrise.blend(Sunrise.SUN[0], Sunrise.SUN[1], 0.5f), Sunrise.key(0.125f, Sunrise.SUN));
        assertEquals(0xFF808080, Sunrise.blend(0xFF000000, 0xFFFFFFFF, 0.5f));
    }

    @Test public void backlightStartsAtTheFloorAndRisesSlowlyFirst() {
        assertEquals(Sunrise.FLOOR, Sunrise.backlight(0f), 0f);
        assertEquals(Sunrise.TOP, Sunrise.backlight(1f), 1e-6f);
        assertTrue(Sunrise.backlight(0.5f) < 0.2f);
        // A 2 s tick through a 20 minute sunrise moves the backlight by well under 0.01.
        float worst = 0;
        for (int i = 0; i < 600; i++) worst = Math.max(worst, Sunrise.backlight((i + 1) / 600f) - Sunrise.backlight(i / 600f));
        assertTrue("step " + worst, worst < 0.004f);
    }

    @Test public void tonesLoopAtTheirLengthsAndPeakAtMinus3dB() {
        short[] chimes = AlarmTones.chimes(), harp = AlarmTones.harp();
        assertEquals(4 * AlarmTones.RATE, chimes.length);
        assertEquals(5 * AlarmTones.RATE, harp.length);
        for (short[] s : new short[][]{chimes, harp}) {
            int peak = 0;
            for (short v : s) peak = Math.max(peak, Math.abs(v));
            assertEquals(Math.round(AlarmTones.PEAK * 32767), peak, 2);
            // The loop is seamless: the last 10 ms are silent.
            for (int i = s.length - AlarmTones.RATE / 100; i < s.length; i++) assertTrue(Math.abs(s[i]) < 200);
        }
    }

    @Test public void harpSoundsTheSameEveryMorning() {
        assertArrayEquals(AlarmTones.harp(), AlarmTones.harp());
        assertTrue(!Arrays.equals(AlarmTones.harp(), AlarmTones.chimes()));
    }

    @Test public void theRampTakesTwoMinutesOrOneAfterASnooze() {
        assertEquals(-30, AlarmTones.rampDb(0, false), 1e-9);
        assertEquals(-15, AlarmTones.rampDb(60, false), 1e-9);
        assertEquals(0, AlarmTones.rampDb(500, false), 1e-9);
        assertEquals(-15, AlarmTones.rampDb(0, true), 1e-9);
        assertEquals(0, AlarmTones.rampDb(60, true), 1e-9);
        assertEquals(1f, AlarmTones.linear(0), 1e-6f);
    }

    @Test public void theVolumeFloorNeverLowersALouderSetting() {
        assertEquals(5, AlarmTones.floorIndex(0, 15, 0.30));
        assertEquals(12, AlarmTones.floorIndex(12, 15, 0.30));
        assertEquals(30, AlarmTones.floorIndex(30, 100, 0.30));
    }
}
