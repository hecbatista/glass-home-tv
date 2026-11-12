package io.github.hecbatista.glasshome;

import android.view.KeyEvent;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class SleepKeyTest {
    private static final int DOWN = KeyEvent.ACTION_DOWN, UP = KeyEvent.ACTION_UP, MENU = KeyEvent.KEYCODE_MENU;

    @Test public void aShortPressReachesTheApp() {
        SleepKey k = new SleepKey();
        assertEquals(SleepKey.Action.PASS, k.on(MENU, DOWN, 100, 100));
        assertEquals(SleepKey.Action.PASS, k.on(MENU, UP, 100, 250));
    }

    @Test public void holdingStartsSleepOnceAndSwallowsTheRest() {
        SleepKey k = new SleepKey();
        assertEquals(SleepKey.Action.PASS, k.on(MENU, DOWN, 100, 100));
        assertEquals(SleepKey.Action.PASS, k.on(MENU, DOWN, 100, 600));      // a repeat before the hold time
        assertEquals(SleepKey.Action.START, k.on(MENU, DOWN, 100, 950));
        assertEquals(SleepKey.Action.CONSUME, k.on(MENU, DOWN, 100, 1000));
        assertEquals(SleepKey.Action.CONSUME, k.on(MENU, UP, 100, 1400));
        // The next press is a fresh one.
        assertEquals(SleepKey.Action.PASS, k.on(MENU, DOWN, 2000, 2000));
        assertEquals(SleepKey.Action.PASS, k.on(MENU, UP, 2000, 2100));
    }

    @Test public void aHoldWithoutRepeatsStartsFromTheTimer() {
        // The accessibility filter sees only the down and the up of a held button.
        SleepKey k = new SleepKey();
        assertEquals(SleepKey.Action.PASS, k.on(MENU, DOWN, 100, 100));
        assertEquals(true, k.held(100));
        assertEquals(false, k.held(100));                                   // once per press
        assertEquals(SleepKey.Action.CONSUME, k.on(MENU, UP, 100, 1500));   // the app never sees the release
    }

    @Test public void aTimerAfterTheReleaseDoesNothing() {
        SleepKey k = new SleepKey();
        assertEquals(SleepKey.Action.PASS, k.on(MENU, DOWN, 100, 100));
        assertEquals(SleepKey.Action.PASS, k.on(MENU, UP, 100, 300));
        assertEquals(false, k.held(100));
    }

    @Test public void otherKeysAlwaysPass() {
        SleepKey k = new SleepKey();
        assertEquals(SleepKey.Action.PASS, k.on(KeyEvent.KEYCODE_DPAD_CENTER, DOWN, 100, 2000));
        assertEquals(SleepKey.Action.PASS, k.on(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, UP, 100, 2100));
    }
}
