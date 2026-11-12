package io.github.hecbatista.glasshome;

import java.util.Random;

/**
 * The alarm's sounds, generated in code (no audio files in the APK): "Chimes", a rising G-major-pentatonic bell
 * phrase, and "Harp", a rising plucked arpeggio. Each renders one loop of 48 kHz mono PCM16, peaking at −3 dBFS;
 * AlarmSound loops it and ramps the volume. Pure, tested on the JVM.
 */
final class AlarmTones {
    static final int RATE = 48_000;
    static final int CHIMES = 0, HARP = 1, LIGHT_ONLY = 2;
    /** −3 dBFS. */
    static final double PEAK = 0.7079;

    private AlarmTones() {}

    static short[] render(int sound) {
        return sound == HARP ? harp() : chimes();
    }

    /** G4, C5, E5, G5 at 0, 0.45, 0.90 and 1.35 s: bell partials, each decaying faster the higher it is. 4.0 s. */
    static short[] chimes() {
        double[] mix = new double[(int) (4.0 * RATE)];
        double[] freqs = {392.00, 523.25, 659.26, 783.99};
        double[] starts = {0, 0.45, 0.90, 1.35};
        double[] levels = {0.80, 0.85, 0.90, 1.00};
        double[][] partials = {{1, 1.0}, {2, 0.30}, {3, 0.12}, {4.2, 0.06}};
        int length = (int) (2.6 * RATE), attack = (int) (0.030 * RATE), release = (int) (0.020 * RATE);
        for (int n = 0; n < freqs.length; n++) {
            int at = (int) (starts[n] * RATE);
            for (int i = 0; i < length && at + i < mix.length; i++) {
                double t = i / (double) RATE, v = 0;
                for (double[] p : partials) {
                    v += p[1] * Math.exp(-t * p[0] / 0.8) * Math.sin(2 * Math.PI * freqs[n] * p[0] * t);
                }
                double env = Math.min(1.0, i / (double) attack) * Math.min(1.0, (length - i) / (double) release);
                mix[at + i] += levels[n] * env * v;
            }
        }
        return normalise(mix);
    }

    /** Karplus–Strong plucks C4 E4 G4 C5 E5 G5 C6, 140 ms apart, a softened noise seed (fixed). 5.0 s. */
    static short[] harp() {
        double[] mix = new double[(int) (5.0 * RATE)];
        double[] freqs = {261.63, 329.63, 392.00, 523.25, 659.26, 783.99, 1046.50};
        Random noise = new Random(20261025L);
        int length = (int) (3.5 * RATE), release = (int) (0.020 * RATE);
        for (int n = 0; n < freqs.length; n++) {
            int at = (int) (n * 0.140 * RATE);
            int period = (int) Math.round(RATE / freqs[n]);
            double[] line = new double[period];
            double prev = 0;
            for (int k = 0; k < period; k++) {
                double r = noise.nextDouble() * 2 - 1;
                line[k] = (r + prev) / 2;   // averaged over 2 samples: a softer pluck
                prev = r;
            }
            double last = line[period - 1];
            for (int i = 0; i < length && at + i < mix.length; i++) {
                int k = i % period;
                double x = line[k];
                line[k] = 0.996 * (x + last) / 2;
                last = x;
                double env = Math.min(1.0, (length - i) / (double) release);
                mix[at + i] += x * env;
            }
        }
        return normalise(mix);
    }

    private static short[] normalise(double[] mix) {
        double peak = 0;
        for (double v : mix) peak = Math.max(peak, Math.abs(v));
        double gain = peak == 0 ? 0 : PEAK / peak;
        short[] out = new short[mix.length];
        for (int i = 0; i < mix.length; i++) out[i] = (short) Math.round(mix[i] * gain * 32767);
        return out;
    }

    /** The track gain in dB at {@code seconds} into a ring: −30 to 0 over 120 s, or from −15 over 60 s after a snooze. */
    static double rampDb(double seconds, boolean afterSnooze) {
        if (afterSnooze) return -15 + 15 * Math.min(1, seconds / 60.0);
        return -30 + 30 * Math.min(1, seconds / 120.0);
    }

    static float linear(double db) {
        return (float) Math.pow(10, db / 20);
    }

    /** The volume index during a ring: at least {@code fraction} of max, never lower than the user's. */
    static int floorIndex(int current, int max, double fraction) {
        return Math.max(current, (int) Math.ceil(fraction * max));
    }
}
