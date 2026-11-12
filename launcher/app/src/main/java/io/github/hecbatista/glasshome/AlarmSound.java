package io.github.hecbatista.glasshome;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

/**
 * Plays the alarm's tones (AlarmTones): one generated phrase looped in a static AudioTrack, its gain ramped from
 * −30 dB to full over two minutes (from −15 dB over one after a snooze), faded out over 600 ms on Snooze or Stop.
 * While it rings, the TV is unmuted and raised to at least 30% of its volume, never lowered, and put back afterwards.
 */
final class AlarmSound {
    private static final AudioAttributes ALARM = new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build();
    private static final double FLOOR = 0.30;
    private static final int[] STREAMS = {AudioManager.STREAM_ALARM, AudioManager.STREAM_MUSIC};

    /** The volumes before the first ring of this alarm, restored after Stop or giving up; null when nothing's saved. */
    private static int[] savedIndex;
    private static boolean[] savedMute;

    private final AudioManager audio;
    private final Handler main = new Handler(Looper.getMainLooper());
    private AudioTrack track;
    private AudioFocusRequest focus;
    private long startedAt;
    private boolean afterSnooze;
    private float gain;

    private final Runnable ramp = new Runnable() {
        @Override public void run() {
            if (track == null) return;
            double s = (SystemClock.elapsedRealtime() - startedAt) / 1000.0;
            setGain(AlarmTones.linear(AlarmTones.rampDb(s, afterSnooze)));
            main.postDelayed(this, 250);
        }
    };

    AlarmSound(Context ctx) {
        audio = ctx.getSystemService(AudioManager.class);
    }

    /** Starts ringing: the volume floor, audio focus so a show pauses, and the looped phrase. */
    void ring(int sound, boolean afterSnooze) {
        stopNow();
        if (sound == AlarmTones.LIGHT_ONLY) return;
        raiseVolume();
        focus = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT).setAudioAttributes(ALARM).build();
        audio.requestAudioFocus(focus);
        this.afterSnooze = afterSnooze;
        track = build(AlarmTones.render(sound), true);
        startedAt = SystemClock.elapsedRealtime();
        setGain(AlarmTones.linear(AlarmTones.rampDb(0, afterSnooze)));
        track.play();
        main.post(ramp);
    }

    /** One phrase at −10 dB, as a preview in Launcher settings: no loop, no volume change, no focus. */
    void preview(int sound) {
        stopNow();
        if (sound == AlarmTones.LIGHT_ONLY) return;
        track = build(AlarmTones.render(sound), false);
        setGain(AlarmTones.linear(-10));
        track.play();
    }

    /** Down to silence over {@code ms}, then stopped; {@code done} runs after. */
    void fadeOut(long ms, Runnable done) {
        main.removeCallbacks(ramp);
        if (track == null) {
            if (done != null) done.run();
            return;
        }
        final float from = gain;
        final long t0 = SystemClock.elapsedRealtime();
        main.post(new Runnable() {
            @Override public void run() {
                float t = Math.min(1f, (SystemClock.elapsedRealtime() - t0) / (float) ms);
                setGain(from * (1f - t));
                if (t < 1f) {
                    main.postDelayed(this, 30);
                } else {
                    stopNow();
                    if (done != null) done.run();
                }
            }
        });
    }

    void stopNow() {
        main.removeCallbacks(ramp);
        if (track != null) {
            try {
                track.stop();
            } catch (IllegalStateException ignored) {
            }
            track.release();
            track = null;
        }
        if (focus != null) {
            // Lets a paused app know it may play again.
            audio.abandonAudioFocusRequest(focus);
            focus = null;
        }
    }

    /** Puts the TV's volume back as it was before the first ring (after Stop or giving up, not on a snooze). */
    void restoreVolume() {
        if (savedIndex == null) return;
        for (int i = 0; i < STREAMS.length; i++) {
            try {
                audio.setStreamVolume(STREAMS[i], savedIndex[i], 0);
                if (savedMute[i]) audio.adjustStreamVolume(STREAMS[i], AudioManager.ADJUST_MUTE, 0);
            } catch (SecurityException ignored) {
                // Do Not Disturb policy: leave it.
            }
        }
        savedIndex = null;
        savedMute = null;
    }

    private void raiseVolume() {
        if (savedIndex == null) {
            savedIndex = new int[STREAMS.length];
            savedMute = new boolean[STREAMS.length];
            for (int i = 0; i < STREAMS.length; i++) {
                savedIndex[i] = audio.getStreamVolume(STREAMS[i]);
                savedMute[i] = audio.isStreamMute(STREAMS[i]);
            }
        }
        for (int stream : STREAMS) {
            try {
                audio.adjustStreamVolume(stream, AudioManager.ADJUST_UNMUTE, 0);
                int now = audio.getStreamVolume(stream);
                int floor = AlarmTones.floorIndex(now, audio.getStreamMaxVolume(stream), FLOOR);
                if (floor > now) audio.setStreamVolume(stream, floor, 0);
            } catch (SecurityException ignored) {
            }
        }
    }

    private void setGain(float g) {
        gain = g;
        if (track != null) track.setVolume(g);
    }

    private static AudioTrack build(short[] pcm, boolean loop) {
        AudioTrack t = new AudioTrack.Builder()
                .setAudioAttributes(ALARM)
                .setAudioFormat(new AudioFormat.Builder().setSampleRate(AlarmTones.RATE)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(pcm.length * 2)
                .build();
        t.write(pcm, 0, pcm.length);
        if (loop) t.setLoopPoints(0, pcm.length, -1);
        return t;
    }
}
