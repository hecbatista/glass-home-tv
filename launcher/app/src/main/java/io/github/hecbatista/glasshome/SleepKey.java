package io.github.hecbatista.glasshome;

import android.view.KeyEvent;

/**
 * Sleep mode's remote shortcut (config sleep_mode): holding ≡ Menu dims the screen. A short press must still reach
 * the app in front, and an accessibility service can't send a key it has held back, so the press goes through and
 * the hold is timed instead: the accessibility filter sees a held button's down and up but not Android's key
 * repeats, so the helper starts a timer on the down ({@link #HOLD_MS}) and calls {@link #held} when it fires. If the
 * button is still down then, sleep mode starts and the release is consumed, so the app never sees a complete press.
 * Repeats, on firmware that does pass them, start it the same way. Pure, so it is tested on the JVM.
 */
final class SleepKey {
    /** How long ≡ Menu is held before sleep mode starts. */
    static final long HOLD_MS = 800;

    enum Action { PASS, CONSUME, START }

    /** The down time of the Menu press that is down now, or -1. */
    private long downNow = -1;
    /** The down time of the press that started sleep mode, whose remaining events are consumed. */
    private long startedDownTime = -1;

    /**
     * What to do with a key event: PASS it to the app, CONSUME it, or START sleep mode (and consume it). Keys other
     * than Menu always pass. After a PASS on a Menu down, the caller times {@link #HOLD_MS} and calls {@link #held}.
     */
    Action on(int keyCode, int action, long downTime, long eventTime) {
        if (keyCode != KeyEvent.KEYCODE_MENU) return Action.PASS;
        boolean started = downTime == startedDownTime;
        if (action == KeyEvent.ACTION_UP) {
            downNow = -1;
            if (started) {
                startedDownTime = -1;
                return Action.CONSUME;
            }
            return Action.PASS;
        }
        if (started) return Action.CONSUME;
        downNow = downTime;
        if (eventTime - downTime >= HOLD_MS) {
            startedDownTime = downTime;
            return Action.START;
        }
        return Action.PASS;
    }

    /** The hold timer for the press that went down at {@code downTime} ran out: true starts sleep mode. */
    boolean held(long downTime) {
        if (downNow != downTime || startedDownTime == downTime) return false;
        startedDownTime = downTime;
        return true;
    }
}
