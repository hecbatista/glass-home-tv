package com.gathin.tvhome;

import android.content.Context;

/**
 * Screensaver Lyrics (.design-reviews/standby-lyrics.md): which line of a song's synced lyrics the StandBy face shows
 * beside the song, one at a time. Pure apart from {@link #enabled}, tested on the JVM; StandbyScene draws.
 */
final class StandbyLyric {
    /** The switch on the Alarm and Sleep screen, in AlarmScheduler's prefs; config.xml's standby_lyrics is its default. */
    static final String KEY = "standby_lyrics";
    /** A line shows this early, so its fade-in lands on the beat. */
    static final long LEAD_MS = 150;
    /** A line still showing this long after it started, with no next line yet, is an instrumental break: none. */
    static final long HOLD_MS = 10_000;
    /** A step back to an earlier line only for a jump this large (a seek), never for a poll's correction. */
    static final long BACK_MS = 2000;

    private StandbyLyric() {}

    /** The switch: offered with lyrics and the StandBy face, Off unless chosen (or a theme turns it on). */
    static boolean enabled(Context ctx, Config cfg) {
        return cfg.lyrics && cfg.standbyClock
                && AlarmScheduler.prefs(ctx).getBoolean(KEY, cfg.standbyLyrics);
    }

    /**
     * The line to show at this position, -1 for none (the intro, an empty line, a long break). {@code shown} is the
     * line on screen now, -1 for none: the answer goes back from it only when the position moved back more than
     * {@link #BACK_MS} before it started.
     */
    static int lineAt(Lyrics lyrics, long positionMs, int shown) {
        long p = positionMs + LEAD_MS;
        int i = lyrics.indexAt(p);
        if (shown >= 0 && i < shown && lyrics.time(shown) - p <= BACK_MS) i = shown;
        if (i < 0 || lyrics.line(i).trim().isEmpty()) return -1;
        return p - lyrics.time(i) > HOLD_MS ? -1 : i;
    }

    /**
     * Lyrics that arrive after the row is drawn: used only if the song's title and second line already fit the
     * narrower song column, so a title never shortens in front of the viewer.
     */
    static boolean fitsBeside(float titleWidth, float secondWidth, float column) {
        return titleWidth <= column && secondWidth <= column;
    }
}
