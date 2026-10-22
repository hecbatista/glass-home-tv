package com.gathin.tvhome;

import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Outline;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.InputStream;
import java.util.List;

/**
 * Glass Home's Now Playing panel (config now_playing), Control Center style: the cover, the song and its artist from
 * whichever app is playing, with previous, play/pause and next. It reads the media sessions through
 * NowPlayingListener's access and shows only while a session is playing or paused; without the access it stays hidden.
 */
final class NowPlayingPanel {
    /** Where focus goes from the panel's buttons: up to the top bar, down to the dock. */
    interface Exits {
        void up();
        void down();
    }

    private final Context ctx;
    private final float density;
    private final boolean light;
    private final Exits exits;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final FrameLayout root;
    private final LinearLayout panel;
    /** The top bar's buttons: the panel sits halfway between their bottom edge and the dock band. */
    private View above;
    private final ImageView art;
    private final TextView title, artist;
    private final ImageButton prev, playPause, next;
    private final int artPx;
    private MediaSessionManager sessions;
    private MediaController controller;
    private boolean started, playing;
    /** placeAbove has run: until then the panel's place is unknown, and a song waits to show. */
    private boolean placed;
    /** Focus is in the rows under the dock, which scroll up into the panel's place: it steps aside meanwhile. */
    private boolean suppressed;
    /** The dock tiles' top edge the panel was last placed above, so a change in its own height keeps the gap. */
    private float dockTop;
    /** The song whose cover is shown, and the cover bitmap the app sent for it (the app may send it a moment later). */
    private String artKey;
    private Bitmap artSource;

    private final MediaSessionManager.OnActiveSessionsChangedListener sessionsChanged = list -> pick(list);

    /** How often a long title scrolls; the marquee only moves when the title does not fit. */
    private static final long MARQUEE_EVERY_MS = 12_000;
    private final Runnable marquee = new Runnable() {
        @Override public void run() {
            title.setSelected(false);
            title.setSelected(true);
            main.postDelayed(this, MARQUEE_EVERY_MS);
        }
    };

    private final MediaController.Callback callback = new MediaController.Callback() {
        @Override public void onMetadataChanged(MediaMetadata metadata) { show(); }
        @Override public void onPlaybackStateChanged(PlaybackState state) { show(); }
        @Override public void onSessionDestroyed() { pick(activeSessions()); }
    };

    NowPlayingPanel(Context ctx, FrameLayout root, float density, boolean light, Exits exits) {
        this.ctx = ctx;
        this.density = density;
        this.light = light;
        this.exits = exits;
        this.root = root;
        int ink = Appearance.ink(light);
        artPx = dp(72);

        panel = new LinearLayout(ctx);
        panel.setOrientation(LinearLayout.HORIZONTAL);
        panel.setGravity(Gravity.CENTER_VERTICAL);
        panel.setBackground(Appearance.glassPanel(dp(26), density, light));
        panel.setPadding(dp(10), dp(10), dp(14), dp(10));
        panel.setVisibility(View.GONE);
        panel.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);

        art = new ImageView(ctx);
        art.setScaleType(ImageView.ScaleType.CENTER_CROP);
        art.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(14));
            }
        });
        art.setClipToOutline(true);
        panel.addView(art, new LinearLayout.LayoutParams(artPx, artPx));

        LinearLayout text = new LinearLayout(ctx);
        text.setOrientation(LinearLayout.VERTICAL);
        // The buttons sit 8dp left of the text so their glyphs line up with it: a focused button's disc reaches past
        // the column, into the gap beside the cover, and must not be cut off there.
        text.setClipChildren(false);
        text.setClipToPadding(false);
        panel.setClipChildren(false);
        panel.setClipToPadding(false);
        LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(dp(250), ViewGroup.LayoutParams.WRAP_CONTENT);
        textLp.setMarginStart(dp(14));
        panel.addView(text, textLp);
        // The cover is a square as tall as the text column (title, artist and buttons), so it fills the panel's height
        // inside the same inset as its sides.
        text.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            int h = b - t;
            ViewGroup.LayoutParams a = art.getLayoutParams();
            if (h > 0 && (a.width != h || a.height != h)) {
                a.width = h;
                a.height = h;
                art.setLayoutParams(a);
            }
        });

        title = line(ink, 17, R.font.inter_semibold);
        // A title too long for the panel scrolls to its end now and then (marquee), instead of staying cut off.
        title.setEllipsize(TextUtils.TruncateAt.MARQUEE);
        title.setMarqueeRepeatLimit(1);
        title.setHorizontalFadingEdgeEnabled(true);
        title.setFadingEdgeLength(dp(18));
        artist = line(Appearance.withAlphaOf(0x9E000000, ink), 14, R.font.inter_medium);
        text.addView(title);
        text.addView(artist);

        LinearLayout controls = new LinearLayout(ctx);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams controlsLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        controlsLp.topMargin = dp(4);
        controlsLp.setMarginStart(-dp(8));
        text.addView(controls, controlsLp);
        prev = button(R.drawable.ic_np_prev, R.string.np_previous, 26);
        playPause = button(R.drawable.ic_np_play, R.string.np_play, 28);
        next = button(R.drawable.ic_np_next, R.string.np_next, 26);
        controls.addView(prev);
        controls.addView(playPause);
        controls.addView(next);

        prev.setOnClickListener(v -> { if (controller != null) controller.getTransportControls().skipToPrevious(); });
        next.setOnClickListener(v -> { if (controller != null) controller.getTransportControls().skipToNext(); });
        playPause.setOnClickListener(v -> {
            if (controller == null) return;
            if (playing) controller.getTransportControls().pause();
            else controller.getTransportControls().play();
        });
        View.OnKeyListener keys = (v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP) { exits.up(); return true; }
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) { exits.down(); return true; }
            // The row's ends: stay on the panel instead of jumping to a tile or the top bar.
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) return v == prev;
            return keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && v == next;
        };
        prev.setOnKeyListener(keys);
        playPause.setOnKeyListener(keys);
        next.setOnKeyListener(keys);

        // Placed before its first layout, the panel only guesses its height; settle it once the real one is known.
        panel.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if (placed && b - t != ob - ot) place();
        });
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.END);
        // The same right edge as the top bar's buttons and the dock band: the header's 56dp padding.
        lp.setMarginEnd(dp(56));
        root.addView(panel, lp);
    }

    private int dp(float v) {
        return Math.round(v * density);
    }

    private TextView line(int color, float sp, int font) {
        TextView t = new TextView(ctx);
        t.setTextColor(color);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTypeface(ctx.getResources().getFont(font));
        t.setLetterSpacing(-0.01f);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setIncludeFontPadding(false);
        t.setPadding(0, dp(2), 0, dp(2));
        return t;
    }

    /** A round button: no fill at rest, a solid disc when focused (white on dark, ink on light), the glyph inverted. */
    private ImageButton button(int icon, int label, int iconDp) {
        ImageButton b = new ImageButton(ctx);
        b.setImageResource(icon);
        b.setContentDescription(ctx.getString(label));
        b.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        int size = dp(42), pad = (size - dp(iconDp)) / 2;
        b.setPadding(pad, pad, pad, pad);
        GradientDrawable on = new GradientDrawable();
        on.setShape(GradientDrawable.OVAL);
        on.setColor(light ? Appearance.INK_DARK : 0xF5FFFFFF);
        StateListDrawable bg = new StateListDrawable();
        bg.addState(new int[]{android.R.attr.state_focused}, on);
        bg.addState(new int[]{}, new GradientDrawable());
        b.setBackground(bg);
        b.setImageTintList(new ColorStateList(new int[][]{{android.R.attr.state_focused}, {}},
                new int[]{light ? 0xFFFFFFFF : Appearance.INK_DARK, Appearance.ink(light)}));
        b.setFocusable(true);
        b.setLayoutParams(new LinearLayout.LayoutParams(size, size));
        return b;
    }

    /** Home is in front: follow the TV's media sessions. Without the media access the panel simply stays hidden. */
    void start() {
        if (started) return;
        started = true;
        main.postDelayed(marquee, 3_000);
        sessions = ctx.getSystemService(MediaSessionManager.class);
        try {
            ComponentName listener = new ComponentName(ctx, NowPlayingListener.class);
            sessions.addOnActiveSessionsChangedListener(sessionsChanged, listener, main);
            pick(sessions.getActiveSessions(listener));
        } catch (SecurityException e) {
            sessions = null;
            panel.setVisibility(View.GONE);
        }
    }

    void stop() {
        if (!started) return;
        started = false;
        main.removeCallbacks(marquee);
        if (sessions != null) sessions.removeOnActiveSessionsChangedListener(sessionsChanged);
        attach(null);
    }

    private List<MediaController> activeSessions() {
        try {
            return sessions == null ? null : sessions.getActiveSessions(new ComponentName(ctx, NowPlayingListener.class));
        } catch (SecurityException e) {
            return null;
        }
    }

    /** The session to show: the first one playing, else the first paused one with a title. */
    private void pick(List<MediaController> list) {
        MediaController best = null;
        if (list != null) {
            for (MediaController c : list) {
                if (stateOf(c) == PlaybackState.STATE_PLAYING) { best = c; break; }
            }
            if (best == null) {
                for (MediaController c : list) {
                    int s = stateOf(c);
                    if ((s == PlaybackState.STATE_PAUSED || s == PlaybackState.STATE_BUFFERING) && titleOf(c) != null) {
                        best = c;
                        break;
                    }
                }
            }
            // Keep a session the panel already shows while nothing else plays, so it does not flicker away.
            if (best == null && controller != null) {
                for (MediaController c : list) if (c.getSessionToken().equals(controller.getSessionToken())) best = c;
            }
        }
        attach(best);
    }

    private void attach(MediaController c) {
        if (controller != null && (c == null || !controller.getSessionToken().equals(c.getSessionToken()))) {
            controller.unregisterCallback(callback);
            controller = null;
        }
        if (c != null && controller == null) {
            controller = c;
            controller.registerCallback(callback, main);
        }
        show();
    }

    private static int stateOf(MediaController c) {
        PlaybackState s = c.getPlaybackState();
        return s == null ? PlaybackState.STATE_NONE : s.getState();
    }

    private static String titleOf(MediaController c) {
        MediaMetadata m = c.getMetadata();
        if (m == null) return null;
        CharSequence t = m.getText(MediaMetadata.METADATA_KEY_TITLE);
        if (TextUtils.isEmpty(t) && m.getDescription() != null) t = m.getDescription().getTitle();
        return TextUtils.isEmpty(t) ? null : t.toString();
    }

    private void show() {
        if (controller == null) {
            hide();
            return;
        }
        int state = stateOf(controller);
        String song = titleOf(controller);
        boolean visible = song != null && (state == PlaybackState.STATE_PLAYING || state == PlaybackState.STATE_PAUSED
                || state == PlaybackState.STATE_BUFFERING || state == PlaybackState.STATE_CONNECTING);
        if (!visible) {
            hide();
            return;
        }
        MediaMetadata m = controller.getMetadata();
        CharSequence by = m.getText(MediaMetadata.METADATA_KEY_ARTIST);
        if (TextUtils.isEmpty(by)) by = m.getText(MediaMetadata.METADATA_KEY_ALBUM_ARTIST);
        if (TextUtils.isEmpty(by) && m.getDescription() != null) by = m.getDescription().getSubtitle();
        title.setText(song);
        artist.setText(by);
        artist.setVisibility(TextUtils.isEmpty(by) ? View.GONE : View.VISIBLE);
        playing = state == PlaybackState.STATE_PLAYING || state == PlaybackState.STATE_BUFFERING;
        playPause.setImageResource(playing ? R.drawable.ic_np_pause : R.drawable.ic_np_play);
        playPause.setContentDescription(ctx.getString(playing ? R.string.np_pause : R.string.np_play));
        long actions = controller.getPlaybackState() == null ? 0 : controller.getPlaybackState().getActions();
        prev.setAlpha((actions & PlaybackState.ACTION_SKIP_TO_PREVIOUS) != 0 ? 1f : 0.35f);
        next.setAlpha((actions & PlaybackState.ACTION_SKIP_TO_NEXT) != 0 ? 1f : 0.35f);
        // Spotify sends a new song's title first and its cover in a later update: follow the bitmap too.
        String key = controller.getPackageName() + "\n" + song + "\n" + by;
        Bitmap cover = coverOf(m);
        if (!key.equals(artKey) || cover != artSource) {
            artKey = key;
            artSource = cover;
            setArt(m, cover);
        }
        if (panel.getVisibility() != View.VISIBLE && placed && !suppressed) {
            panel.setAlpha(0f);
            panel.setVisibility(View.VISIBLE);
            panel.animate().alpha(1f).setDuration(240).start();
        }
    }

    private void hide() {
        boolean hadFocus = panel.hasFocus();
        panel.setVisibility(View.GONE);
        artKey = null;
        artSource = null;
        if (hadFocus) exits.down();
    }

    /** The cover from the metadata: a bitmap if the app sends one, else a content URI; a music glyph otherwise. */
    private static Bitmap coverOf(MediaMetadata m) {
        Bitmap bmp = m.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
        if (bmp == null) bmp = m.getBitmap(MediaMetadata.METADATA_KEY_ART);
        if (bmp == null) bmp = m.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON);
        if (bmp == null && m.getDescription() != null) bmp = m.getDescription().getIconBitmap();
        return bmp;
    }

    private void setArt(MediaMetadata m, Bitmap bmp) {
        if (bmp == null) {
            String uri = m.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI);
            if (uri == null) uri = m.getString(MediaMetadata.METADATA_KEY_ART_URI);
            if (uri == null && m.getDescription() != null && m.getDescription().getIconUri() != null) {
                uri = m.getDescription().getIconUri().toString();
            }
            bmp = load(uri);
        }
        if (bmp != null) {
            art.setBackground(null);
            art.setPadding(0, 0, 0, 0);
            art.setImageTintList(null);
            art.setImageBitmap(bmp.getWidth() > artPx * 2 ? Bitmap.createScaledBitmap(bmp, artPx * 2,
                    Math.max(1, Math.round(artPx * 2f * bmp.getHeight() / bmp.getWidth())), true) : bmp);
        } else {
            GradientDrawable tile = new GradientDrawable();
            tile.setColor(light ? 0x14000000 : 0x24FFFFFF);
            art.setBackground(tile);
            int pad = dp(24);
            art.setPadding(pad, pad, pad, pad);
            art.setImageResource(R.drawable.ic_np_music);
            art.setImageTintList(ColorStateList.valueOf(Appearance.withAlphaOf(0x99000000, Appearance.ink(light))));
        }
    }

    /** A local cover only (content:, android.resource:, file:): the launcher has no network access. */
    private Bitmap load(String uri) {
        if (uri == null) return null;
        Uri u = Uri.parse(uri);
        String scheme = u.getScheme();
        if (!ContentResolver.SCHEME_CONTENT.equals(scheme) && !ContentResolver.SCHEME_ANDROID_RESOURCE.equals(scheme)
                && !ContentResolver.SCHEME_FILE.equals(scheme)) return null;
        try (InputStream in = ctx.getContentResolver().openInputStream(u)) {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inSampleSize = 2;
            return in == null ? null : BitmapFactory.decodeStream(in, null, o);
        } catch (Exception e) {
            return null;
        }
    }

    void setAbove(View view) {
        above = view;
    }

    /**
     * Just above the dock band (which reaches 15dp above the tiles), level with the shelf's name; never closer to the
     * top bar's buttons than to the band. Before the top bar is laid out, 30dp clear of the band.
     */
    private void place() {
        float h = panel.getHeight() > 0 ? panel.getHeight() : dp(92);
        float bandTop = dockTop - dp(15);
        float y = bandTop - dp(30) - h;
        if (above != null && above.getHeight() > 0) {
            android.graphics.Rect r = new android.graphics.Rect(0, 0, above.getWidth(), above.getHeight());
            root.offsetDescendantRectToMyCoords(above, r);
            // Anchored 16dp above the band, beside the shelf's name; where the room is tighter than that, the same gap
            // above and below instead.
            float gap = Math.min(dp(16), (bandTop - r.bottom - h) / 2f);
            y = bandTop - gap - h;
        }
        panel.setY(Math.round(y));
    }

    boolean isShown() {
        return panel.getVisibility() == View.VISIBLE && !suppressed;
    }

    /** Fades the panel out while focus is in the rows under the dock, and back in (if a song is up) after. */
    void setSuppressed(boolean suppress) {
        if (suppressed == suppress) return;
        suppressed = suppress;
        panel.animate().cancel();
        if (suppress) {
            if (panel.getVisibility() == View.VISIBLE) {
                panel.animate().alpha(0f).setDuration(160).withEndAction(() -> {
                    if (suppressed) panel.setVisibility(View.INVISIBLE);
                }).start();
            }
        } else {
            if (panel.getVisibility() == View.INVISIBLE) panel.setVisibility(View.GONE);
            show();
        }
    }

    void focus() {
        playPause.requestFocus();
    }

    /** Sits above the dock, on the right of the shelf's name: its bottom on the same line as the shelf's. */
    void placeAbove(float dockTop) {
        this.dockTop = dockTop;
        place();
        if (!placed) {
            placed = true;
            show();
        }
    }
}
