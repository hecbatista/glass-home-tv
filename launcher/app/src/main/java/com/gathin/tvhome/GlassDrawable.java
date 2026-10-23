package com.gathin.tvhome;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.View;

/** A view's background in GlassMaterial: its own position on the screen decides which part of the glow shows through. */
final class GlassDrawable extends Drawable {
    private final GlassMaterial material;
    private final View host;
    private final float radius;
    private final RectF rect = new RectF();

    GlassDrawable(GlassMaterial material, View host, float radius) {
        this.material = material;
        this.host = host;
        this.radius = radius;
        material.register(host);
    }

    @Override
    public void draw(Canvas canvas) {
        rect.set(getBounds());
        float[] o = material.originOf(host);
        material.draw(canvas, rect, Math.min(radius, Math.min(rect.width(), rect.height()) / 2f), o[0], o[1]);
    }

    @Override public void setAlpha(int alpha) {}
    @Override public void setColorFilter(ColorFilter colorFilter) {}
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
