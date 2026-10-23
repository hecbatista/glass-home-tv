package com.gathin.tvhome;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.view.View;
import android.view.ViewParent;
import android.view.ViewTreeObserver;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Glass Home's liquid glass, fitted to Apple's renders over this Home screen: what is behind the glass, pushed toward
 * white and saturated (Dark 12% and 1.3×, Light 56% and 1.9×), even from top to bottom, with a 1px rim lit at the
 * top and bottom, a faint dark edge at the sides, a soft inner band and (on Light) a soft shadow.
 *
 * What is behind the glass on Home is the background colour plus the focused app's glow, which MainActivity draws as
 * a 480×270 white mask tinted with the app's colour and stretched over the screen. The colour adjustment is affine, so
 * it can be drawn without reading the screen back: a solid round rect in the adjusted background colour, then the
 * same glow mask through a BitmapShader tinted with the adjusted glow colour. The rim, inner band and shadow depend
 * only on the corner radius: baked once per radius into a small bitmap and drawn in nine slices.
 */
final class GlassMaterial {
    /** The Home screen's material while Glass Home runs (one Home activity at a time); null elsewhere. */
    static GlassMaterial current;

    /** Fitted values, in TV pixels at density 2 (Apple's renders are 1920×1080). */
    private static final class Fit {
        final float mix, sat, rimTop, rimBot, rimSide, innerTop, innerBot, innerH;
        final float shadowA, shadowBlur, shadowDy, smallA, smallBlur, smallDy;

        Fit(float mix, float sat, float rimTop, float rimBot, float rimSide, float innerTop, float innerBot, float innerH,
            float shadowA, float shadowBlur, float shadowDy, float smallA, float smallBlur, float smallDy) {
            this.mix = mix; this.sat = sat; this.rimTop = rimTop; this.rimBot = rimBot; this.rimSide = rimSide;
            this.innerTop = innerTop; this.innerBot = innerBot; this.innerH = innerH;
            this.shadowA = shadowA; this.shadowBlur = shadowBlur; this.shadowDy = shadowDy;
            this.smallA = smallA; this.smallBlur = smallBlur; this.smallDy = smallDy;
        }
    }

    private static final Fit DARK = new Fit(0.12f, 1.30f, 0.25f, 0.20f, 0.40f, 0.12f, 0.16f, 14,
            0.20f, 10, 3, 0.10f, 6, 2);
    private static final Fit LIGHT = new Fit(0.56f, 1.90f, 1.0f, 0.70f, 0.28f, 0.70f, 0.55f, 16,
            0.075f, 18, 18, 0.03f, 10, 8);
    private static final float[] LUMA = {0.2126f, 0.7152f, 0.0722f};
    private static final float RIM_POWER = 4, INNER_POWER = 2;
    /** A surface this short (TV px at density 2) casts Apple's lighter shadow: the top bar's pills, not the panel. */
    private static final float SMALL_SURFACE_PX = 160;

    private final Fit fit;
    /** The screen's background colour (background_color), behind the glass where no glow is. */
    private final int background;
    private final float scale;   // TV px at this density per fitted px
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint slice = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Matrix shaderMatrix = new Matrix();
    private final Map<String, Edge> edges = new HashMap<>();
    private final List<WeakReference<View>> surfaces = new ArrayList<>();
    private View root, ambient;
    private Bitmap glowMask;
    private int tint = -1;
    private float seenAlpha = -1;
    private int seenVisibility = -1;
    /** A wallpaper is behind the glass: the fill cannot be the adjusted background colour, so it is see-through white. */
    private boolean overWallpaper;
    /** Where each surface sat at the last frame: one moved with setY (the Now Playing panel) needs its glow redrawn. */
    private final Map<View, Long> origins = new java.util.WeakHashMap<>();

    GlassMaterial(boolean light, int background, float density) {
        this.background = background;
        fit = light ? LIGHT : DARK;
        scale = density / 2f;
        fill.setColor(vib(background));
    }

    /**
     * A theme wallpaper is on: what is behind the glass is a picture, not the background colour and glow, so the fill
     * becomes white at the fitted mix strength (0x1F on Dark, 0x8F on Light) and the glow layer is left out.
     */
    void setOverWallpaper(boolean on) {
        overWallpaper = on;
        fill.setColor(on ? Math.round(fit.mix * 255) << 24 | 0xFFFFFF : vib(background));
    }

    /** The colour the glass shows over this colour: toward white, then more saturated. */
    int vib(int color) {
        return vib(color, fit.mix, fit.sat);
    }

    /** vib() for any fit: {@code mix} toward white, then saturation {@code sat} about Rec. 709 luma (a ColorMatrix). */
    static int vib(int color, float mix, float sat) {
        int r = (color >> 16) & 0xFF, g = (color >> 8) & 0xFF, b = color & 0xFF;
        float k = 1 - mix, offset = 255 * mix;
        int[] out = new int[3];
        for (int i = 0; i < 3; i++) {
            float v = offset;
            int[] in = {r, g, b};
            for (int j = 0; j < 3; j++) v += ((1 - sat) * LUMA[j] + (i == j ? sat : 0)) * k * in[j];
            out[i] = Math.max(0, Math.min(255, Math.round(v)));
        }
        return 0xFF000000 | out[0] << 16 | out[1] << 8 | out[2];
    }

    /** The screen's root and the view that draws the glow over it; their changes redraw every glass surface. */
    void attach(View root, View ambient) {
        this.root = root;
        this.ambient = ambient;
        // The glow fades in and out by its view's alpha; a frame that changes it redraws the glass before it draws.
        root.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override public boolean onPreDraw() {
                float a = glowAlpha();
                int vis = ambient.getVisibility();
                if (a != seenAlpha || vis != seenVisibility) {
                    seenAlpha = a;
                    seenVisibility = vis;
                    invalidateSurfaces();
                } else {
                    invalidateMoved();
                }
                return true;
            }
        });
    }

    /** MainActivity's glow mask (white, alpha only): the same bitmap the ambient view stretches over the screen. */
    void setGlow(Bitmap mask) {
        glowMask = mask;
        glow.setShader(mask == null ? null : new BitmapShader(mask, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
        invalidateSurfaces();
    }

    /** The glow's colour (the focused app's), as MainActivity tints the mask with it. */
    void setTint(int color) {
        if (color == tint) return;
        tint = color;
        glow.setColorFilter(new PorterDuffColorFilter(vib(0xFF000000 | color), PorterDuff.Mode.SRC_IN));
        invalidateSurfaces();
    }

    /** A view whose background or decoration is this glass: redrawn when the glow behind it changes. */
    void register(View v) {
        for (WeakReference<View> w : surfaces) if (w.get() == v) return;
        surfaces.add(new WeakReference<>(v));
    }

    private void invalidateSurfaces() {
        for (Iterator<WeakReference<View>> it = surfaces.iterator(); it.hasNext(); ) {
            View v = it.next().get();
            if (v == null) it.remove();
            else v.invalidate();
        }
    }

    /** Redraws the surfaces whose place on the screen changed since the last frame (a translation does not re-record). */
    private void invalidateMoved() {
        for (Iterator<WeakReference<View>> it = surfaces.iterator(); it.hasNext(); ) {
            View v = it.next().get();
            if (v == null) {
                it.remove();
                continue;
            }
            float[] o = originOf(v);
            long key = (long) Float.floatToIntBits(o[0]) << 32 | (Float.floatToIntBits(o[1]) & 0xFFFFFFFFL);
            Long was = origins.put(v, key);
            if (was != null && was != key) v.invalidate();
        }
    }

    private float glowAlpha() {
        return ambient == null || ambient.getVisibility() != View.VISIBLE ? 0f : ambient.getAlpha();
    }

    /** Where {@code v}'s top-left corner sits on the root, translations included (the panel is placed with setY). */
    float[] originOf(View v) {
        float x = 0, y = 0;
        View at = v;
        while (at != null && at != root) {
            x += at.getX();
            y += at.getY();
            ViewParent p = at.getParent();
            if (!(p instanceof View)) break;
            at = (View) p;
            x -= at.getScrollX();
            y -= at.getScrollY();
        }
        return new float[]{x, y};
    }

    /**
     * Draws a glass round rect at {@code r} (canvas coordinates) on a canvas whose origin sits at
     * ({@code originX}, {@code originY}) on the root.
     */
    void draw(Canvas c, RectF r, float radius, float originX, float originY) {
        c.drawRoundRect(r, radius, radius, fill);
        float a = glowAlpha();
        if (!overWallpaper && glowMask != null && tint != -1 && a > 0 && root != null && root.getWidth() > 0) {
            shaderMatrix.setScale(root.getWidth() / (float) glowMask.getWidth(), root.getHeight() / (float) glowMask.getHeight());
            shaderMatrix.postTranslate(-originX, -originY);
            glow.getShader().setLocalMatrix(shaderMatrix);
            glow.setAlpha(Math.round(a * 255));
            c.drawRoundRect(r, radius, radius, glow);
        }
        edge(radius, r.height() <= SMALL_SURFACE_PX * scale).draw(c, r, slice);
    }

    // ---- the edge bitmap: shadow, inner band and rim for one radius --------------------------------------------

    private Edge edge(float radius, boolean small) {
        String key = Math.round(radius) + (small ? "s" : "");
        Edge e = edges.get(key);
        if (e == null) {
            e = new Edge(Math.round(radius), small);
            edges.put(key, e);
        }
        return e;
    }

    private static float bright(double nx, double ny, float top, float bot, float p) {
        return (float) (top * Math.pow(Math.max(0, -ny), p) + bot * Math.pow(Math.max(0, ny), p));
    }

    /** A SweepGradient centred at (cx, cy): one colour per edge-normal direction, as the fit was measured. */
    private static SweepGradient sweep(float cx, float cy, int rgb, AlphaAt alpha) {
        int[] colors = new int[73];
        for (int i = 0; i <= 72; i++) {
            double a = i / 72.0 * 2 * Math.PI;
            float v = Math.max(0, Math.min(1, alpha.at(Math.cos(a), Math.sin(a))));
            colors[i] = Math.round(v * 255) << 24 | (rgb & 0xFFFFFF);
        }
        return new SweepGradient(cx, cy, colors, null);
    }

    private interface AlphaAt {
        float at(double nx, double ny);
    }

    /** One radius's shadow, inner band and rim, around a square with 48px of straight edge, drawn in nine slices. */
    private final class Edge {
        final Bitmap bitmap;
        final int padX, padT, padB, r, straight;

        Edge(int r, boolean small) {
            this.r = r;
            straight = Math.round(48 * scale);
            float shA = small ? fit.smallA : fit.shadowA;
            float shBlur = (small ? fit.smallBlur : fit.shadowBlur) * scale;
            float shDy = (small ? fit.smallDy : fit.shadowDy) * scale;
            padX = (int) Math.ceil(shBlur * 2.5);
            padT = Math.max(4, (int) Math.ceil(shBlur * 2.5 - shDy));
            padB = (int) Math.ceil(shBlur * 2.5 + shDy);
            int side = 2 * r + straight;
            bitmap = Bitmap.createBitmap(2 * padX + side, padT + padB + side, Bitmap.Config.ARGB_8888);
            Canvas g = new Canvas(bitmap);
            float x = padX, y = padT, cx = x + side / 2f, cy = y + side / 2f;
            RectF shape = new RectF(x, y, x + side, y + side);
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

            // Shadow: the shape blurred and dropped, then cut out where the glass itself sits.
            if (shA > 0) {
                p.setColor(Math.round(shA * 255) << 24);
                p.setMaskFilter(new BlurMaskFilter(Math.max(1f, shBlur * 1.7f), BlurMaskFilter.Blur.NORMAL));
                g.drawRoundRect(new RectF(shape.left, shape.top + shDy, shape.right, shape.bottom + shDy), r, r, p);
                p.setMaskFilter(null);
                p.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
                g.drawRoundRect(shape, r, r, p);
                p.setXfermode(null);
            }

            // Inner band: a blurred white outline kept inside the shape, strongest at the top and bottom.
            float innerH = fit.innerH * scale;
            Bitmap band = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), Bitmap.Config.ARGB_8888);
            Canvas bg = new Canvas(band);
            Path clip = new Path();
            clip.addRoundRect(shape, r, r, Path.Direction.CW);
            bg.save();
            bg.clipPath(clip);
            // The stroke lies wholly outside the shape (an outline outset by innerH, innerH thick each way); only its
            // blur reaches in, 0.5 at the edge and falling off with sigma about 7px, as the fit measured.
            Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setColor(0xFFFFFFFF);
            stroke.setStrokeWidth(2 * innerH);
            stroke.setMaskFilter(new BlurMaskFilter(Math.max(1f, innerH * 0.81f), BlurMaskFilter.Blur.NORMAL));
            RectF outset = new RectF(shape.left - innerH, shape.top - innerH, shape.right + innerH, shape.bottom + innerH);
            bg.drawRoundRect(outset, r + innerH, r + innerH, stroke);
            bg.restore();
            Paint weight = new Paint();
            weight.setShader(sweep(cx, cy, 0xFFFFFF, (nx, ny) -> bright(nx, ny, fit.innerTop, fit.innerBot, INNER_POWER)));
            weight.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
            bg.drawRect(0, 0, band.getWidth(), band.getHeight(), weight);
            g.drawBitmap(band, 0, 0, null);
            band.recycle();

            // Rim: 1px lit at the top and bottom just inside the edge, a faint dark line just outside at the sides.
            Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
            rim.setStyle(Paint.Style.STROKE);
            rim.setStrokeWidth(1);
            rim.setShader(sweep(cx, cy, 0xFFFFFF, (nx, ny) -> bright(nx, ny, fit.rimTop, fit.rimBot, RIM_POWER)));
            g.drawRoundRect(new RectF(x + 0.5f, y + 0.5f, x + side - 0.5f, y + side - 0.5f), r - 0.5f, r - 0.5f, rim);
            rim.setShader(sweep(cx, cy, 0x000000, (nx, ny) -> (float) (fit.rimSide * Math.pow(Math.abs(nx), RIM_POWER))));
            g.drawRoundRect(new RectF(x - 0.5f, y - 0.5f, x + side + 0.5f, y + side + 0.5f), r + 0.5f, r + 0.5f, rim);
        }

        /** Nine-slice: corners as baked, the straight middles stretched, the centre left empty. */
        void draw(Canvas c, RectF b, Paint paint) {
            int w = bitmap.getWidth(), h = bitmap.getHeight(), half = straight / 2;
            int[] sx = {0, padX + r, padX + r + half, padX + r + half + 1, w};
            int[] sy = {0, padT + r, padT + r + half, padT + r + half + 1, h};
            float[] dx = {b.left - padX, b.left + r, b.right - r, b.right + padX};
            float[] dy = {b.top - padT, b.top + r, b.bottom - r, b.bottom + padB};
            int[][] cols = {{sx[0], sx[1]}, {sx[2], sx[3]}, {sx[3] + half - 1, sx[4]}};
            int[][] rws = {{sy[0], sy[1]}, {sy[2], sy[3]}, {sy[3] + half - 1, sy[4]}};
            Rect src = new Rect();
            RectF dst = new RectF();
            for (int j = 0; j < 3; j++) {
                for (int i = 0; i < 3; i++) {
                    if (i == 1 && j == 1) continue;
                    if (dx[i + 1] <= dx[i] || dy[j + 1] <= dy[j]) continue;
                    src.set(cols[i][0], rws[j][0], cols[i][1], rws[j][1]);
                    dst.set(dx[i], dy[j], dx[i + 1], dy[j + 1]);
                    c.drawBitmap(bitmap, src, dst, paint);
                }
            }
        }
    }
}
