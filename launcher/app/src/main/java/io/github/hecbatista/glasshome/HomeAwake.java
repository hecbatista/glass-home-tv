package io.github.hecbatista.glasshome;

/**
 * How long Home keeps the screen on after the last button press (home_awake_minutes). 0 leaves it to the TV's own
 * screensaver and sleep timer; after the chosen time they take over again. Only while Home is on screen.
 */
final class HomeAwake {
    static final String KEY = "home_awake_minutes";
    /** Home keeps the screen on for as long as it is on screen. */
    static final int ALWAYS = -1;
    /** Launcher settings' choices: the TV's setting, 30 minutes, 1 hour, 2 hours, always. */
    static final int[] CHOICES = {0, 30, 60, 120, ALWAYS};

    private HomeAwake() {}

    static boolean valid(int minutes) {
        return minutes == ALWAYS || minutes >= 0 && minutes <= 24 * 60;
    }

    /** The minutes chosen on the TV, else the theme's, else 0 (the TV's own timer). */
    static int chosen(int saved, int theme) {
        return valid(saved) ? saved : valid(theme) ? theme : 0;
    }
}
