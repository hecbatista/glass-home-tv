package io.github.hecbatista.glasshome;

/**
 * The alarm's sunrise (the design brief's keyframes): the sky, horizon, sun and clock colours, and the backlight,
 * as p runs from 0 (the screen wakes) to 1 (the alarm). Colours blend in sRGB between five keys, like ArgbEvaluator;
 * the backlight rises as p^2.2, so the first minutes stay near the panel's dimmest. Pure, tested on the JVM.
 */
final class Sunrise {
    /** The panel's dimmest window brightness. 0.0 dims this TV's backlight without blanking it (checked on the TV). */
    static final float FLOOR = 0.0f;
    /** The backlight at the alarm time; ringing then takes it to 1.0 over a minute. */
    static final float TOP = 0.80f;

    static final int[] SKY = {0xFF000000, 0xFF000000, 0xFF050102, 0xFF0E0608, 0xFF1A1012};
    static final int[] HORIZON = {0xFF0A0100, 0xFF1E0400, 0xFF3A0E04, 0xFF5A2410, 0xFF6E3A1E};
    static final int[] SUN = {0xFF3D0600, 0xFF7A1404, 0xFFC2400E, 0xFFF08A30, 0xFFFFC780};
    static final int[] CLOCK = {0xFFB81C0C, 0xFFD23A1C, 0xFFF07A48, 0xFFFFC79A, 0xFFFFF3E4};

    private Sunrise() {}

    static float clamp01(float p) {
        return p < 0f ? 0f : p > 1f ? 1f : p;
    }

    static float backlight(float p) {
        p = clamp01(p);
        return FLOOR + (TOP - FLOOR) * (float) Math.pow(p, 2.2);
    }

    /** The colour at {@code p} along keys placed at 0, 0.25, 0.5, 0.75 and 1. */
    static int key(float p, int[] keys) {
        p = clamp01(p);
        int i = Math.min(3, (int) (p / 0.25f));
        return blend(keys[i], keys[i + 1], (p - i * 0.25f) / 0.25f);
    }

    /** sRGB blend of two opaque-or-not colours, channel by channel (what ArgbEvaluator does). */
    static int blend(int from, int to, float t) {
        int out = 0;
        for (int shift = 0; shift <= 24; shift += 8) {
            int a = (from >>> shift) & 0xFF, b = (to >>> shift) & 0xFF;
            out |= (Math.round(a + (b - a) * t) & 0xFF) << shift;
        }
        return out;
    }
}
