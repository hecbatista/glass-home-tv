package com.gathin.tvhome;

/**
 * The full-screen lyrics' progress bar without Android: the position it shows, and its two times. The player's
 * position comes in on every tick (the PlaybackClock, carried forward from the player's last report); this keeps
 * the shown position from stepping back when a fresh report lands a little behind the carried-forward one.
 */
final class SongProgress {
    /** A step back shorter than this is a report catching up (playing or paused), and is held; a longer one is a seek. */
    static final long HOLD_MS = 2000;
    static final String MINUS = "−";

    private String song;
    private long durationMs;
    /** The position on screen, 0 … duration; -1 until this song's position is known. */
    private long shownMs = -1;
    private boolean songChanged;

    /**
     * One tick: the song (any key that changes with it), its length (0 or less when unknown) and where the player is
     * ({@code positionMs} below 0 when it has not said). Returns true when anything shown changed.
     */
    boolean update(String song, long durationMs, long positionMs) {
        songChanged = song == null ? this.song != null : !song.equals(this.song);
        boolean was = visible();
        long before = shownMs, durBefore = this.durationMs;
        if (songChanged) {
            this.song = song;
            shownMs = -1;
        }
        this.durationMs = durationMs;
        if (positionMs >= 0) {
            long pos = clamp(positionMs);
            boolean shortStepBack = shownMs >= 0 && pos < shownMs && shownMs - pos < HOLD_MS;
            if (!shortStepBack) shownMs = pos;
            else shownMs = clamp(shownMs);   // the length may have changed under it
        }
        return songChanged || was != visible() || before != shownMs || durBefore != durationMs;
    }

    private long clamp(long ms) {
        if (ms < 0) return 0;
        return durationMs > 0 && ms > durationMs ? durationMs : ms;
    }

    /**
     * On a song change: whether the first report for the new song ({@code reportMs}, carried to now) is really the
     * old song's last state, being well into a song and within 1.5 s of where the old one was ({@code oldMs}).
     */
    static boolean carriesOn(long reportMs, long oldMs) {
        return reportMs > 5000 && Math.abs(reportMs - oldMs) <= 1500;
    }

    /** The last update brought a different song. */
    boolean songChanged() {
        return songChanged;
    }

    /** Shown only with a known length and a known position. */
    boolean visible() {
        return durationMs > 0 && shownMs >= 0;
    }

    long shownMs() {
        return shownMs;
    }

    /** How much of the bar is filled, 0 … 1. */
    float fraction() {
        return visible() ? (float) ((double) shownMs / durationMs) : 0f;
    }

    long elapsedS() {
        return Math.min(durationMs / 1000, Math.max(0, shownMs) / 1000);
    }

    long remainingS() {
        return durationMs / 1000 - elapsedS();
    }

    String elapsedText() {
        return clock(elapsedS());
    }

    String remainingText() {
        return MINUS + clock(remainingS());
    }

    /** m:ss with no leading zero on the minutes, h:mm:ss from an hour on. */
    static String clock(long s) {
        long h = s / 3600, m = s / 60 % 60, sec = s % 60;
        StringBuilder b = new StringBuilder(8);
        if (h > 0) b.append(h).append(':').append(m < 10 ? "0" : "");
        b.append(h > 0 ? m : s / 60).append(':');
        if (sec < 10) b.append('0');
        return b.append(sec).toString();
    }
}
