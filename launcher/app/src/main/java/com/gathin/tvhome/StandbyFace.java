package com.gathin.tvhome;

/**
 * The StandBy screensaver's rules (.design-reviews/standby-screensaver.md): when the night face applies, the drift
 * that keeps the face moving by whole pixels, and the backlight. Pure, tested on the JVM; StandbyScene draws.
 */
final class StandbyFace {
    /** 22:00 and 07:00, as minutes of the day. */
    static final int NIGHT_START = 22 * 60, NIGHT_END = 7 * 60;
    /** The backlight by day; at night the panel's dimmest (Sunrise.FLOOR). */
    static final float DAY_BACKLIGHT = 0.50f;
    /** An alarm whose sunrise starts within this long ends the night early (or late) instead of 07:00. */
    static final long WAKE_WINDOW_MS = 14 * 60 * 60_000L;

    private StandbyFace() {}

    /**
     * Night: sleep mode is on, or it is from 22:00 until the night ends. The night ends at {@code wake} (the alarm's
     * sunrise start as a minute of the day, when it falls within the window; -1 otherwise) if that is in the
     * morning, else at 07:00.
     */
    static boolean night(int minuteOfDay, boolean sleepMode, int wake) {
        if (sleepMode) return true;
        int end = wake >= 0 && wake < 12 * 60 ? wake : NIGHT_END;
        return minuteOfDay >= NIGHT_START || minuteOfDay < end;
    }

    /** The minute of the day the alarm's sunrise starts, if it does within the next 14 hours; -1 otherwise. */
    static int wakeMinute(AlarmPlan plan, long now, java.util.TimeZone zone) {
        if (plan == null || plan.sunriseAt - now > WAKE_WINDOW_MS || plan.sunriseAt < now) return -1;
        java.util.Calendar c = java.util.Calendar.getInstance(zone);
        c.setTimeInMillis(plan.sunriseAt);
        return c.get(java.util.Calendar.HOUR_OF_DAY) * 60 + c.get(java.util.Calendar.MINUTE);
    }

    /**
     * The face's offset in whole pixels: a Lissajous orbit inside ±24 × ±16 TV px, periods 37 and 23 minutes. Updated
     * every 10 s it moves at most 1 px per update, under what an eye notices at 10 ft.
     */
    static int[] drift(long elapsedMs, double phase) {
        double t = elapsedMs / 1000.0;
        return new int[]{(int) Math.round(24 * Math.sin(2 * Math.PI * t / (37 * 60) + phase)),
                (int) Math.round(16 * Math.sin(2 * Math.PI * t / (23 * 60) + 1.7 * phase))};
    }

    /** Night to day over ten minutes: the backlight rises as p^2.2 from the floor, like the sunrise. */
    static float morningBacklight(float p) {
        p = Sunrise.clamp01(p);
        return Sunrise.FLOOR + (DAY_BACKLIGHT - Sunrise.FLOOR) * (float) Math.pow(p, 2.2);
    }

    /**
     * The cover's colour matrix between none (0) and StandBy's night tint (1): luma mapped from black onto 60% of
     * {@code tint}, so even a white cover stays darker than the red clock (at most #6E1107 for #B81C0C) instead of
     * being the largest light on the screen. Android's ColorMatrix order.
     */
    static float[] nightMatrix(int tint, float amount) {
        final float k = 0.60f;
        float r = k * ((tint >> 16) & 0xFF) / 255f, g = k * ((tint >> 8) & 0xFF) / 255f, b = k * (tint & 0xFF) / 255f;
        float yr = .2126f, yg = .7152f, yb = .0722f;
        float[] night = {r * yr, r * yg, r * yb, 0, 0, g * yr, g * yg, g * yb, 0, 0, b * yr, b * yg, b * yb, 0, 0,
                0, 0, 0, 1, 0};
        float[] id = {1, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 1, 0};
        float[] out = new float[20];
        for (int i = 0; i < 20; i++) out[i] = id[i] + (night[i] - id[i]) * amount;
        return out;
    }
}
