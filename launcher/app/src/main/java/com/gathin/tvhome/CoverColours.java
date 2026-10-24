package com.gathin.tvhome;

import android.graphics.Bitmap;

/**
 * The full-screen lyrics' background colours from a cover (Fire OS 8 has no RenderEffect blur, and Palette would be a
 * new dependency): the two most vivid hues, a deep mix of them and a dark base. Pure int maths on pixels, so it is
 * tested on the JVM.
 */
final class CoverColours {
    /** Home's dark background and two quiet blues, for a song without a cover. */
    static final int[] NONE = {0xFF2E3A5C, 0xFF3A2E5C, 0xFF1B1F2E, 0xFF0B0B0F};

    private CoverColours() {}

    static int[] of(Bitmap cover) {
        if (cover == null) return NONE;
        Bitmap small = Bitmap.createScaledBitmap(cover, 24, 24, true);
        int[] px = new int[24 * 24];
        small.getPixels(px, 0, 24, 0, 0, 24, 24);
        if (small != cover) small.recycle();
        return pick(px);
    }

    /**
     * {first vivid, second vivid, deep mix, dark base}. Pixels vote for one of 12 hues by saturation × value; the two
     * strongest hues (the second at least 2 hues away when there is one) give the fields, each lifted to a usable
     * brightness. A grey cover gets its own average, so a black-and-white cover stays black and white.
     */
    static int[] pick(int[] pixels) {
        float[] weight = new float[12];
        float[][] sum = new float[12][3];
        long r = 0, g = 0, b = 0;
        for (int p : pixels) {
            int pr = (p >> 16) & 0xFF, pg = (p >> 8) & 0xFF, pb = p & 0xFF;
            r += pr;
            g += pg;
            b += pb;
            float[] hsv = hsv(pr, pg, pb);
            float w = hsv[1] * hsv[2];
            if (w < 0.08f) continue;
            int bin = Math.min(11, (int) (hsv[0] / 30f));
            weight[bin] += w;
            sum[bin][0] += pr * w;
            sum[bin][1] += pg * w;
            sum[bin][2] += pb * w;
        }
        int n = Math.max(1, pixels.length);
        int average = rgb((int) (r / n), (int) (g / n), (int) (b / n));
        int first = -1, second = -1;
        for (int i = 0; i < 12; i++) if (weight[i] > 0 && (first < 0 || weight[i] > weight[first])) first = i;
        for (int i = 0; i < 12; i++) {
            if (i == first || weight[i] <= 0 || hueGap(i, first) < 2) continue;
            if (second < 0 || weight[i] > weight[second]) second = i;
        }
        int a = first < 0 ? lift(average, 0.55f) : lift(meanOf(sum[first], weight[first]), 0.75f);
        int c = second < 0 ? lift(shade(a, 0.7f), 0.5f) : lift(meanOf(sum[second], weight[second]), 0.7f);
        int deep = shade(mix(a, c), 0.45f);
        int base = shade(average, 0.12f);
        return new int[]{0xFF000000 | a, 0xFF000000 | c, 0xFF000000 | deep, 0xFF000000 | base};
    }

    private static int hueGap(int a, int b) {
        int d = Math.abs(a - b);
        return Math.min(d, 12 - d);
    }

    private static int meanOf(float[] s, float w) {
        return rgb(Math.round(s[0] / w), Math.round(s[1] / w), Math.round(s[2] / w));
    }

    private static int mix(int a, int b) {
        return rgb((((a >> 16) & 0xFF) + ((b >> 16) & 0xFF)) / 2, (((a >> 8) & 0xFF) + ((b >> 8) & 0xFF)) / 2,
                ((a & 0xFF) + (b & 0xFF)) / 2);
    }

    /** The colour at this brightness (HSV value), keeping its hue and saturation. */
    static int shade(int color, float value) {
        float[] hsv = hsv((color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF);
        return fromHsv(hsv[0], hsv[1], value);
    }

    /** At least this bright, so a dark cover still lights the background. */
    private static int lift(int color, float minValue) {
        float[] hsv = hsv((color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF);
        return fromHsv(hsv[0], hsv[1], Math.max(hsv[2], minValue));
    }

    static float[] hsv(int r, int g, int b) {
        int max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
        float d = max - min, h;
        if (d == 0) h = 0;
        else if (max == r) h = 60f * (((g - b) / d) % 6);
        else if (max == g) h = 60f * ((b - r) / d + 2);
        else h = 60f * ((r - g) / d + 4);
        if (h < 0) h += 360;
        return new float[]{h, max == 0 ? 0 : d / max, max / 255f};
    }

    static int fromHsv(float h, float s, float v) {
        float c = v * s, x = c * (1 - Math.abs((h / 60f) % 2 - 1)), m = v - c;
        float r, g, b;
        if (h < 60) { r = c; g = x; b = 0; }
        else if (h < 120) { r = x; g = c; b = 0; }
        else if (h < 180) { r = 0; g = c; b = x; }
        else if (h < 240) { r = 0; g = x; b = c; }
        else if (h < 300) { r = x; g = 0; b = c; }
        else { r = c; g = 0; b = x; }
        return rgb(Math.round((r + m) * 255), Math.round((g + m) * 255), Math.round((b + m) * 255));
    }

    private static int rgb(int r, int g, int b) {
        return (Math.max(0, Math.min(255, r)) << 16) | (Math.max(0, Math.min(255, g)) << 8) | Math.max(0, Math.min(255, b));
    }
}
