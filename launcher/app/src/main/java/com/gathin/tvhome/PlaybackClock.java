package com.gathin.tvhome;

/**
 * Where a song is now, from the last position its player reported: the position plus the time since, at the
 * playback speed (0 while paused). The lyrics card asks it several times a second; the players report far less often
 * (Spotify's Web API every few seconds, a media session on each change).
 */
final class PlaybackClock {
    private long positionMs, atMs;
    private float speed;

    /** The player said: {@code positionMs} into the song at {@code atMs} (a monotonic clock), moving at {@code speed}. */
    void set(long positionMs, long atMs, float speed) {
        this.positionMs = positionMs;
        this.atMs = atMs;
        this.speed = speed;
    }

    float speed() {
        return speed;
    }

    long positionAt(long nowMs) {
        return positionMs + (long) ((nowMs - atMs) * speed);
    }
}
