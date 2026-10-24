package com.gathin.tvhome;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * The Now Playing panel's full-screen lyrics (the lyrics critique's prototype C): the cover, the song and its buttons
 * on the left, every line of the song on the right in large type, the current one at y 338 and the column sliding up
 * as the song moves on. The background is drawn from the cover's colours. Opened with OK on the panel's cover, closed
 * with Back. All positions are TV px at 1920×1080 (density 2), as in the design.
 */
final class LyricsScreen {
    /** The panel's buttons, which this screen's own buttons press. */
    interface Controls {
        void previous();
        void playPause();
        void next();
        void closed();
    }

    private final Context ctx;
    private final float density;
    private final boolean animations;
    private final Controls controls;
    private final FrameLayout root;
    private final FrameLayout screen;
    private final ImageView cover;
    private final TextView title, artist, none;
    private final ImageButton prev, playPause, next;
    private final ImageView source;
    /** The song's progress under the artist: the bar and the two times, shown and hidden together. */
    private final FrameLayout progressRow;
    private final SongBar bar;
    private final TextView elapsed, remaining;
    private final SongProgress progress = new SongProgress();
    private final FadeColumn column;
    private final LinearLayout list;
    private final PathInterpolator ease = new PathInterpolator(0.2f, 0.8f, 0.2f, 1f);
    private Lyrics lyrics;
    private int shown = Integer.MIN_VALUE;
    private Bitmap coverShown;
    private Boolean playingShown;

    LyricsScreen(Context ctx, FrameLayout root, float density, boolean animations, Controls controls) {
        this.ctx = ctx;
        this.root = root;
        this.density = density;
        this.animations = animations;
        this.controls = controls;

        screen = new FrameLayout(ctx);
        screen.setVisibility(View.GONE);
        screen.setClickable(true);
        // Open, the lyrics keep the TV awake: nobody presses a button while reading along. Only while it is visible.
        screen.setKeepScreenOn(true);
        screen.setBackgroundColor(0xFF0B0B0F);

        // Left: the cover (112, 158, 540 square), the title and artist under it, the song's progress, then the buttons
        // and the source glyph.
        cover = new ImageView(ctx);
        cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
        cover.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), px(36));
            }
        });
        cover.setClipToOutline(true);
        cover.setElevation(px(40));
        if (android.os.Build.VERSION.SDK_INT >= 28) cover.setOutlineSpotShadowColor(0xFF000000);
        screen.addView(cover, at(112, 158, 540, 540));

        title = text(40, R.font.inter_semibold, 0xFFFFFFFF);
        screen.addView(title, at(112, 726, 540, 48));
        artist = text(30, R.font.inter_medium, 0x9EFFFFFF);
        screen.addView(artist, at(112, 774, 540, 40));

        // The progress: an 8 px bar across the cover's width (y 842), elapsed and remaining under its ends (y 860).
        // Not a control: never focused, and silent for VoiceView (it would announce every second).
        progressRow = new FrameLayout(ctx);
        progressRow.setVisibility(View.INVISIBLE);
        progressRow.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        bar = new SongBar(ctx);
        progressRow.addView(bar, at(0, 0, 540, 8));
        elapsed = time(Gravity.START);
        progressRow.addView(elapsed, at(0, 18, 200, 32));
        remaining = time(Gravity.END);
        progressRow.addView(remaining, at(340, 18, 200, 32));
        screen.addView(progressRow, at(112, 842, 540, 50));

        prev = button(R.drawable.ic_np_prev, R.string.np_previous, 52);
        playPause = button(R.drawable.ic_np_pause, R.string.np_pause, 56);
        next = button(R.drawable.ic_np_next, R.string.np_next, 52);
        screen.addView(prev, at(96, 908, 84, 84));
        screen.addView(playPause, at(180, 908, 84, 84));
        screen.addView(next, at(264, 908, 84, 84));
        prev.setOnClickListener(v -> controls.previous());
        playPause.setOnClickListener(v -> controls.playPause());
        next.setOnClickListener(v -> controls.next());
        source = new ImageView(ctx);
        source.setImageTintList(ColorStateList.valueOf(0x8CFFFFFF));
        source.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        screen.addView(source, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        // Right: the lines, x 764 to 1808, fading out at the top and bottom of the screen.
        column = new FadeColumn(ctx);
        list = new LinearLayout(ctx);
        list.setOrientation(LinearLayout.VERTICAL);
        column.addView(list, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        screen.addView(column, at(764, 0, 1044, 1080));
        none = text(40, R.font.inter_semibold, 0x80FFFFFF);
        none.setText(R.string.lyrics_none);
        none.setVisibility(View.GONE);
        screen.addView(none, at(764, 338, 1044, 48));

        root.addView(screen, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private int px(float tvPx) {
        return Math.round(tvPx * density / 2f);
    }

    private FrameLayout.LayoutParams at(float x, float y, float w, float h) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(px(w), px(h));
        lp.leftMargin = px(x);
        lp.topMargin = px(y);
        return lp;
    }

    private TextView text(float tvPx, int font, int color) {
        TextView t = new TextView(ctx);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(tvPx));
        t.setTypeface(ctx.getResources().getFont(font));
        t.setTextColor(color);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setIncludeFontPadding(false);
        t.setGravity(Gravity.CENTER_VERTICAL);
        return t;
    }

    /** A time under the bar: Inter Medium 26 px with tabular digits, so it keeps its width as it counts. */
    private TextView time(int gravity) {
        TextView t = text(26, R.font.inter_medium, 0xCCFFFFFF);
        t.setFontFeatureSettings("tnum");
        t.setGravity(gravity | Gravity.CENTER_VERTICAL);
        t.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        return t;
    }

    /** An 84 px round button: the glyph white at rest, ink on Home's white disc when focused. */
    private ImageButton button(int icon, int label, float glyphPx) {
        ImageButton b = new ImageButton(ctx);
        b.setImageResource(icon);
        b.setContentDescription(ctx.getString(label));
        b.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int pad = (px(84) - px(glyphPx)) / 2;
        b.setPadding(pad, pad, pad, pad);
        GradientDrawable on = new GradientDrawable();
        on.setShape(GradientDrawable.OVAL);
        on.setColor(0xF5FFFFFF);
        StateListDrawable bg = new StateListDrawable();
        bg.addState(new int[]{android.R.attr.state_focused}, on);
        bg.addState(new int[]{}, new GradientDrawable());
        b.setBackground(bg);
        b.setImageTintList(new ColorStateList(new int[][]{{android.R.attr.state_focused}, {}},
                new int[]{Appearance.INK_DARK, 0xFFFFFFFF}));
        b.setFocusable(true);
        return b;
    }

    boolean isOpen() {
        return screen.getVisibility() == View.VISIBLE;
    }

    void open() {
        if (isOpen()) return;
        screen.bringToFront();
        screen.setVisibility(View.VISIBLE);
        playPause.requestFocus();
        if (animations) {
            screen.setAlpha(0f);
            screen.animate().alpha(1f).setDuration(240).start();
        }
    }

    void close() {
        if (!isOpen()) return;
        screen.animate().cancel();
        progressRow.animate().cancel();
        progressRow.setAlpha(1f);
        screen.setVisibility(View.GONE);
        controls.closed();
    }

    /**
     * Keys while open: Back closes, Left and Right walk the three buttons, Up and Down stay put. OK, the media keys and
     * the volume pass on (false), so the focused button takes OK as a click.
     */
    boolean onKey(KeyEvent event) {
        int code = event.getKeyCode();
        boolean down = event.getAction() == KeyEvent.ACTION_DOWN;
        if (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_ESCAPE) {
            if (!down && !event.isCanceled()) close();
            return true;
        }
        View[] row = {prev, playPause, next};
        int at = prev.isFocused() ? 0 : next.isFocused() ? 2 : 1;
        if (code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT) {
            if (down) row[Math.max(0, Math.min(2, at + (code == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1)))].requestFocus();
            return true;
        }
        if (code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN) {
            if (down && !row[at].isFocused()) row[at].requestFocus();
            return true;
        }
        return false;
    }

    // ---- what plays -----------------------------------------------------------------------------------------------

    void setSong(CharSequence song, CharSequence by) {
        if (!TextUtils.equals(title.getText(), song)) title.setText(song);
        if (!TextUtils.equals(artist.getText(), by)) artist.setText(by);
        artist.setVisibility(TextUtils.isEmpty(by) ? View.GONE : View.VISIBLE);
    }

    void setPlaying(boolean playing) {
        if (playingShown != null && playingShown == playing) return;
        playingShown = playing;
        playPause.setImageResource(playing ? R.drawable.ic_np_pause : R.drawable.ic_np_play);
        playPause.setContentDescription(ctx.getString(playing ? R.string.np_pause : R.string.np_play));
    }

    void setSkips(float prevAlpha, float nextAlpha) {
        prev.setAlpha(prevAlpha);
        next.setAlpha(nextAlpha);
    }

    /**
     * Where the song is, on each tick while open: the song (a key that changes with it), its length (0 or less when
     * unknown) and the position (below 0 until the player has said). The row shows only with both known. It appears
     * at once with a new song or before the screen opens, and fades in when it becomes known for the same song.
     */
    void setProgress(String song, long durationMs, long positionMs) {
        if (!progress.update(song, durationMs, positionMs)) return;
        boolean on = progress.visible();
        if (on) {
            bar.setFraction(progress.fraction());
            String e = progress.elapsedText(), r = progress.remainingText();
            if (!TextUtils.equals(elapsed.getText(), e)) elapsed.setText(e);
            if (!TextUtils.equals(remaining.getText(), r)) remaining.setText(r);
        }
        if (on == (progressRow.getVisibility() == View.VISIBLE)) return;
        progressRow.animate().cancel();
        progressRow.setAlpha(1f);
        progressRow.setVisibility(on ? View.VISIBLE : View.INVISIBLE);
        if (on && animations && isOpen() && !progress.songChanged()) {
            progressRow.setAlpha(0f);
            progressRow.animate().alpha(1f).setDuration(240).start();
        }
    }

    /** The source glyph (already trimmed to its visible box), its right edge at x 652 and centred on the buttons. */
    void setSource(Bitmap glyph) {
        source.setImageBitmap(glyph);
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) source.getLayoutParams();
        lp.leftMargin = px(652) - glyph.getWidth();
        lp.topMargin = px(950) - glyph.getHeight() / 2;
        source.setLayoutParams(lp);
    }

    /** The cover, and the background made from its colours; null gives the music glyph on Home's dark background. */
    void setCover(Bitmap bmp) {
        if (bmp == coverShown && cover.getDrawable() != null) return;
        coverShown = bmp;
        if (bmp != null) {
            cover.setBackground(null);
            cover.setPadding(0, 0, 0, 0);
            cover.setImageTintList(null);
            cover.setImageBitmap(bmp);
        } else {
            GradientDrawable tile = new GradientDrawable();
            tile.setColor(0x24FFFFFF);
            cover.setBackground(tile);
            int pad = px(170);
            cover.setPadding(pad, pad, pad, pad);
            cover.setImageResource(R.drawable.ic_np_music);
            cover.setImageTintList(ColorStateList.valueOf(0x99FFFFFF));
        }
        BitmapDrawable bg = new BitmapDrawable(ctx.getResources(), background(CoverColours.of(bmp)));
        bg.setFilterBitmap(true);
        screen.setBackground(bg);
    }

    /**
     * Every line of the song, one view each. Null shows "No synced lyrics" once the lookup is done ({@code known}),
     * and nothing while it still runs.
     */
    void setLyrics(Lyrics l, boolean known) {
        none.setVisibility(l == null && known ? View.VISIBLE : View.GONE);
        if (l == lyrics) return;
        lyrics = l;
        shown = Integer.MIN_VALUE;
        list.animate().cancel();
        list.removeAllViews();
        if (l == null) return;
        for (int i = 0; i < l.size(); i++) {
            TextView t = new TextView(ctx);
            t.setText(l.line(i));
            t.setTypeface(ctx.getResources().getFont(R.font.inter_display_bold));
            t.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(60));
            t.setLetterSpacing(-0.015f);
            t.setTextColor(0xFFFFFFFF);
            t.setIncludeFontPadding(false);
            // 72 px lines: Inter's own line is 1.21 em (73 px), so the spacing only trims it.
            t.setLineSpacing(px(72) - t.getPaint().getFontMetricsInt(null), 1f);
            t.setAlpha(0.5f);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = px(34);
            list.addView(t, lp);
        }
    }

    /** The line playing now (-1 before the first): its top moves to y 338, the lines before it dim to 35%. */
    void show(int index) {
        if (lyrics == null || !isOpen() || index == shown || list.getChildCount() == 0) return;
        if (list.getHeight() == 0) {
            list.post(() -> show(index));
            return;
        }
        boolean slide = animations && shown != Integer.MIN_VALUE;
        shown = index;
        int anchor = Math.max(0, index);
        float y = px(338) - list.getChildAt(anchor).getTop() + (index < 0 ? px(72 + 34) : 0);
        list.animate().cancel();
        if (slide) list.animate().translationY(y).setDuration(600).setInterpolator(ease).start();
        else list.setTranslationY(y);
        for (int i = 0; i < list.getChildCount(); i++) {
            View row = list.getChildAt(i);
            float a = i == index ? 1f : i < index ? 0.35f : 0.5f;
            if (slide) row.animate().alpha(a).setDuration(600).start();
            else row.setAlpha(a);
        }
    }

    /**
     * The design's background as one small bitmap stretched over the screen: three soft colour fields from the cover
     * on a dark base, then a dim that is lighter behind the cover and darker behind the lyrics.
     */
    private static Bitmap background(int[] c) {
        int w = 480, h = 270;
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        canvas.drawColor(c[3]);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        field(canvas, p, 0.86f * w, 0.42f * h, 0.34f * w, c[2], 1f);
        field(canvas, p, 0.28f * w, 0.18f * h, 0.24f * w, c[0], 0.9f);
        field(canvas, p, 0.18f * w, 0.80f * h, 0.15f * w, c[1], 0.85f);
        p.setShader(new LinearGradient(0, 0, w, 0,
                new int[]{0x4D000000, 0x59000000, 0x8C060416, 0x99060416}, new float[]{0f, 0.36f, 0.46f, 1f},
                Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, w, h, p);
        return bmp;
    }

    private static void field(Canvas canvas, Paint p, float cx, float cy, float r, int color, float alpha) {
        int a = Math.round(alpha * 255);
        p.setShader(new RadialGradient(cx, cy, r * 1.6f, (a << 24) | (color & 0xFFFFFF), color & 0xFFFFFF,
                Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, canvas.getWidth(), canvas.getHeight(), p);
    }

    /**
     * The progress bar: a capsule track, and the same capsule filled up to the playhead, cut straight there. The two
     * parts never overlap, so each keeps its own colour.
     */
    private static final class SongBar extends View {
        private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG), fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF capsule = new RectF();
        private float fraction;

        SongBar(Context ctx) {
            super(ctx);
            track.setColor(0x40FFFFFF);
            fill.setColor(0xE6FFFFFF);
            setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        void setFraction(float f) {
            if (f == fraction) return;
            fraction = f;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float w = getWidth(), h = getHeight(), r = h / 2f, fillW = w * Math.max(0f, Math.min(1f, fraction));
            capsule.set(0, 0, w, h);
            if (fillW < w) {
                int save = canvas.save();
                canvas.clipRect(fillW, 0, w, h);
                canvas.drawRoundRect(capsule, r, r, track);
                canvas.restoreToCount(save);
            }
            if (fillW > 0) {
                int save = canvas.save();
                canvas.clipRect(0, 0, fillW, h);
                canvas.drawRoundRect(capsule, r, r, fill);
                canvas.restoreToCount(save);
            }
        }
    }

    /** The lyrics column: the lines fade out over its top 150 px and its bottom 240 px, as in the design's mask. */
    private final class FadeColumn extends FrameLayout {
        private final Paint mask = new Paint();
        private int maskHeight;

        FadeColumn(Context ctx) {
            super(ctx);
            setClipChildren(true);
            mask.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
        }

        /** The list is as tall as the whole song, not the screen; it slides inside the column. */
        @Override
        protected void measureChildWithMargins(View child, int widthSpec, int widthUsed, int heightSpec, int heightUsed) {
            super.measureChildWithMargins(child, widthSpec, widthUsed,
                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED), heightUsed);
        }

        @Override
        protected void dispatchDraw(Canvas canvas) {
            // The lines and the mask in one offscreen layer, so the mask cuts only the lines, not the background.
            int h = getHeight();
            int save = canvas.saveLayer(0, 0, getWidth(), h, null);
            super.dispatchDraw(canvas);
            if (h != maskHeight) {
                maskHeight = h;
                mask.setShader(new LinearGradient(0, 0, 0, h,
                        new int[]{0x00000000, 0xFF000000, 0xFF000000, 0x00000000},
                        new float[]{0f, 150f / 1080f, 840f / 1080f, 1040f / 1080f}, Shader.TileMode.CLAMP));
            }
            canvas.drawRect(0, 0, getWidth(), h, mask);
            canvas.restoreToCount(save);
        }
    }

}
