package io.github.hecbatista.glasshome;

import android.content.Context;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * The Now Playing panel's lyrics card (config lyrics): the previous, current and next line of a song's synced lyrics,
 * above the song row. When the song moves on a line, the lines slide up one row; any other jump (a seek, a new song)
 * just redraws them. All rows share one size so nothing jumps while they slide; the current one is bright.
 */
final class LyricsCard {
    private static final int ROWS_SHOWN = 3;
    private final float density;
    private final LinearLayout view;
    private final LinearLayout track;
    /** Rows above, at and below the current line, plus one more below so a slide has something to bring in. */
    private final TextView[] rows = new TextView[ROWS_SHOWN + 1];
    private final int rowPx, bright, dim;
    private final boolean animations;
    private Lyrics lyrics;
    private int shown = Integer.MIN_VALUE;

    LyricsCard(Context ctx, float density, boolean light, boolean animations) {
        this.density = density;
        this.animations = animations;
        int ink = Appearance.ink(light);
        bright = ink;
        dim = Appearance.withAlphaOf(0x5C000000, ink);
        rowPx = dp(25);

        view = new LinearLayout(ctx);
        view.setOrientation(LinearLayout.VERTICAL);
        view.setPadding(dp(16), dp(15), dp(16), dp(4));
        view.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);

        FrameLayout clip = new FrameLayout(ctx);
        clip.setClipChildren(true);
        view.addView(clip, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, rowPx * ROWS_SHOWN));
        track = new LinearLayout(ctx);
        track.setOrientation(LinearLayout.VERTICAL);
        clip.addView(track, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        for (int i = 0; i < rows.length; i++) {
            TextView t = new TextView(ctx);
            t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15.5f);
            t.setTypeface(ctx.getResources().getFont(R.font.inter_semibold));
            t.setLetterSpacing(-0.012f);
            t.setSingleLine(true);
            t.setEllipsize(TextUtils.TruncateAt.END);
            t.setIncludeFontPadding(false);
            t.setGravity(android.view.Gravity.CENTER_VERTICAL);
            track.addView(t, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, rowPx));
            rows[i] = t;
        }
    }

    View view() {
        return view;
    }

    private int dp(float v) {
        return Math.round(v * density);
    }

    /** A new song's lyrics; the card draws nothing until show() says which line plays. */
    void setLyrics(Lyrics l) {
        lyrics = l;
        shown = Integer.MIN_VALUE;
        track.animate().cancel();
        track.setTranslationY(0);
    }

    /** The line playing now, -1 before the first. Moving on by exactly one line slides; anything else redraws. */
    void show(int index) {
        if (lyrics == null || index == shown) return;
        boolean slide = animations && shown != Integer.MIN_VALUE && index == shown + 1;
        shown = index;
        track.animate().cancel();
        if (!slide) {
            track.setTranslationY(0);
            fill(index);
            return;
        }
        // The row below takes the bright colour as it rises into the middle; the old current line dims as it leaves.
        rows[1].setTextColor(dim);
        rows[2].setTextColor(bright);
        rows[3].setText(lyrics.line(index + 2));
        rows[3].setTextColor(dim);
        track.setTranslationY(0);
        track.animate().translationY(-rowPx).setDuration(420)
                .setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f))
                .withEndAction(() -> {
                    track.setTranslationY(0);
                    fill(shown);
                }).start();
    }

    private void fill(int index) {
        for (int i = 0; i < rows.length; i++) {
            rows[i].setText(i == 1 ? text(index) : lyrics.line(index - 1 + i));
            rows[i].setTextColor(i == 1 ? bright : dim);
        }
    }

    /** A line's text; before the first line, the middle row shows a note while the intro plays. */
    private String text(int i) {
        if (lyrics == null) return "";
        if (i == -1) return "♪";
        return lyrics.line(i);
    }
}
