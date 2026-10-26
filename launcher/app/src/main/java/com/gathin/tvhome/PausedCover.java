package com.gathin.tvhome;

import android.graphics.drawable.GradientDrawable;
import android.view.View;

import androidx.dynamicanimation.animation.FloatPropertyCompat;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;

/**
 * Glass Home's paused cover, Apple Music style: while the song is paused the cover shrinks a little and dims, and
 * when it plays again it springs back with a small bounce. Used by the Now Playing panel and the full-screen lyrics.
 * The dim is a black layer in the cover's foreground, under any focus ring, so the ring stays bright.
 */
final class PausedCover {
    /** The cover's size while paused, and how dark the dim layer gets (0 to 1). */
    static final float SCALE = 0.88f, DIM = 0.3f;

    private final View cover;
    private final GradientDrawable dim;
    private final boolean animations;
    private final SpringAnimation spring;
    /** 0 playing, 1 paused; the bounce back takes it a little below 0. */
    private float amount;
    private Boolean playingShown;

    /**
     * {@code dim} is a drawable in the cover's foreground; this sets its colour and alpha. {@code light} is the
     * surface under the cover: the dim fades toward it.
     */
    PausedCover(View cover, GradientDrawable dim, boolean animations, boolean light) {
        this.cover = cover;
        this.dim = dim;
        this.animations = animations;
        dim.setColor(dimColour(light));
        dim.setAlpha(0);
        spring = new SpringAnimation(this, new FloatPropertyCompat<PausedCover>("paused") {
            @Override public float getValue(PausedCover p) { return p.amount; }
            @Override public void setValue(PausedCover p, float value) { p.apply(value); }
        });
        spring.setMinimumVisibleChange(SpringAnimation.MIN_VISIBLE_CHANGE_SCALE);
    }

    /** Springs to the new state; {@code animate} false (a first show, the screen just opening) sets it at once. */
    void setPlaying(boolean playing, boolean animate) {
        if (playingShown != null && playingShown == playing) return;
        boolean spring = animate && animations && playingShown != null;
        playingShown = playing;
        float target = playing ? 0f : 1f;
        if (!spring) {
            this.spring.cancel();
            apply(target);
            return;
        }
        // Pausing settles without overshoot; playing again comes back with a slight bounce past full size.
        this.spring.setSpring(new SpringForce(target).setStiffness(playing ? 380f : 260f)
                .setDampingRatio(playing ? 0.55f : 1f));
        this.spring.start();
    }

    private void apply(float value) {
        amount = value;
        float s = scaleAt(value);
        cover.setScaleX(s);
        cover.setScaleY(s);
        dim.setAlpha(Math.round(255 * dimAt(value)));
    }

    /** The dim layer's colour: black on dark surfaces, the Light background on Light, so a cover rests rather than greys. */
    static int dimColour(boolean light) {
        return light ? 0xFFECEEF3 : 0xFF000000;
    }

    static float scaleAt(float amount) {
        return 1f - (1f - SCALE) * amount;
    }

    /** The dim layer's opacity; never below 0 while the bounce overshoots. */
    static float dimAt(float amount) {
        return DIM * Math.max(0f, Math.min(1f, amount));
    }
}
