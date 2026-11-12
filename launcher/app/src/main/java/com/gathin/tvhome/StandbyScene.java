package com.gathin.tvhome;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.SystemClock;
import android.text.Layout;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Glass Home's screensaver in the style of iPhone's StandBy (.design-reviews/standby-screensaver.md): a 520 px clock on
 * black with the playing song's cover in the bottom-left corner, a red night face from 22:00 (or in sleep mode) at
 * the panel's dimmest backlight, and a drift of whole pixels instead of jumps. Shared by ClockDream (the system
 * screensaver) and ClockActivity (Home left idle). Positions are TV px at 1920×1080.
 */
final class StandbyScene {
    private static final int RED = 0xFFB81C0C, DAY = 0xFFE6E6EA;
    private static final long TICK_MS = 2000, DRIFT_MS = 10_000, LYRIC_TICK_MS = 200;
    private static final long NOTHING_MS = 15_000, PAUSED_MS = 10 * 60_000L, COVER_WAIT_MS = 3000;

    /** The face on screen now, so the alarm can end it before its own screen (endCurrent). */
    private static WeakReference<StandbyScene> current = new WeakReference<>(null);
    /**
     * The face that just stopped, so the next one carries on without a blink or a jump: Night Clock (ClockActivity)
     * gives way to the system screensaver after the TV's idle time.
     */
    private static final long HANDOFF_MS = 2000;
    private static long handoffAt = -1, handoffStartedAt;
    private static double handoffPhase;
    private static NowPlayingFeed.Track handoffTrack;

    private final Context ctx;
    private final Window window;
    private final Runnable end;
    private final float density;
    private final boolean motion;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final FrameLayout root, face, row;
    private final View glowA, glowB;
    private final TextView date, clock, title, second;
    /** Screensaver Lyrics: the line being sung, right of the song (.design-reviews/standby-lyrics.md). */
    private final TextView lyric;
    private final boolean lyricsOn;
    private final ImageView cover, source;
    private final GradientDrawable glowShapeA, glowShapeB;
    private final NowPlayingFeed feed;
    private double phase = Math.random() * 2 * Math.PI;
    private long startedAt;

    /** 0 day … 1 night, as drawn now. */
    private float night;
    private boolean nightTarget;
    private ValueAnimator crossing;
    private long morningFrom = -1;
    private boolean musicShown;
    /** The cover glow's strength, 0 … 1, animated with the row so it never snaps on or off. */
    private float glowLevel;
    private ValueAnimator glowAnim;
    /** The cover's 2 px edge by day; one drawable, recoloured as night comes. */
    private final GradientDrawable coverEdge = new GradientDrawable();
    /** The overlay that holds the backlight level (setBacklight). */
    private View lightHolder;
    private WindowManager.LayoutParams lightLp;
    private NowPlayingFeed.Track shown, pending;
    /** The lyrics of the song in the row (null: none, or not known yet), and the line on screen (-1: none). */
    private Lyrics lyrics;
    private String lyricsKey;
    private int lyricShown = -1;
    /** Until then the next line change draws at once: this face took over from one already showing it. */
    private long lyricInstantUntil = -1;
    private HandlerThread lookupThread;
    private Handler lookup;
    private final java.util.Set<String> lookingUp = new java.util.HashSet<>();
    private long pausedSince = -1;

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            update();
            main.postDelayed(this, TICK_MS);
        }
    };
    private final Runnable drift = new Runnable() {
        @Override public void run() {
            int[] o = StandbyFace.drift(SystemClock.elapsedRealtime() - startedAt, phase);
            face.setTranslationX(px(o[0]));
            face.setTranslationY(px(o[1]));
            main.postDelayed(this, DRIFT_MS);
        }
    };
    private final Runnable nothingPlaying = () -> showMusic(false);
    private final Runnable lyricTick = new Runnable() {
        @Override public void run() {
            updateLyric();
            main.postDelayed(this, LYRIC_TICK_MS);
        }
    };
    private final Runnable coverTimeout = () -> {
        if (pending != null) swapRow(pending);
    };

    StandbyScene(Context ctx, Window window, boolean spotifyConnect, boolean motion, boolean lyricsOn, Runnable end) {
        this.ctx = ctx;
        this.lyricsOn = lyricsOn;
        this.window = window;
        this.end = end;
        this.motion = motion;
        density = ctx.getResources().getDisplayMetrics().density;
        root = new FrameLayout(ctx);
        root.setBackgroundColor(0xFF000000);
        glowShapeA = glowShape();
        glowShapeB = glowShape();
        glowA = new View(ctx);
        glowA.setBackground(glowShapeA);
        glowB = new View(ctx);
        glowB.setBackground(glowShapeB);
        glowB.setAlpha(0f);
        root.addView(glowA, match());
        root.addView(glowB, match());
        face = new FrameLayout(ctx);
        root.addView(face, match());

        Typeface semibold = ctx.getResources().getFont(R.font.inter_semibold);
        date = text(semibold, 40, 0.08f);
        face.addView(date, at(112, top(date, 160)));
        clock = text(ctx.getResources().getFont(R.font.inter_display_semibold), 520, -0.03f);
        clock.setGravity(Gravity.CENTER_HORIZONTAL);
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.topMargin = top(clock, 729);
        face.addView(clock, clp);

        row = new FrameLayout(ctx);
        FrameLayout.LayoutParams rlp = new FrameLayout.LayoutParams(px(1696), px(240));
        rlp.leftMargin = px(112);
        rlp.topMargin = px(744);
        face.addView(row, rlp);
        row.setAlpha(0f);
        cover = new ImageView(ctx);
        cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
        cover.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), px(20));
            }
        });
        cover.setClipToOutline(true);
        coverEdge.setCornerRadius(px(20));
        cover.setForeground(coverEdge);
        row.addView(cover, new FrameLayout.LayoutParams(px(240), px(240)));
        title = text(semibold, 40, 0f);
        title.setMaxWidth(px(1040));
        title.setEllipsize(TextUtils.TruncateAt.END);
        row.addView(title, at(272, top(title, 852) - px(744)));
        LinearLayout line = new LinearLayout(ctx);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(Gravity.CENTER_VERTICAL);
        source = new ImageView(ctx);
        source.setVisibility(View.GONE);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(px(32), px(32));
        slp.setMarginEnd(px(12));
        line.addView(source, slp);
        second = text(ctx.getResources().getFont(R.font.inter_medium), 32, 0f);
        second.setMaxWidth(px(1040));
        second.setEllipsize(TextUtils.TruncateAt.END);
        line.addView(second);
        row.addView(line, at(272, top(second, 900) - px(744)));
        // Two 40 px lines on the row's baselines (852 and 900), from x 1008 to the edge.
        lyric = text(semibold, 40, -0.01f);
        lyric.setSingleLine(false);
        lyric.setMaxLines(2);
        lyric.setEllipsize(TextUtils.TruncateAt.END);
        // Balanced, so a second line is never one stray word; never hyphenated.
        lyric.setBreakStrategy(Layout.BREAK_STRATEGY_BALANCED);
        lyric.setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE);
        lyric.setLineSpacing(px(48) - lyric.getPaint().getFontMetricsInt(null), 1f);
        lyric.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);
        lyric.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        lyric.setAlpha(0f);
        lyric.setVisibility(View.GONE);
        FrameLayout.LayoutParams llp = at(896, top(lyric, 852) - px(744));
        llp.width = px(800);
        row.addView(lyric, llp);

        feed = new NowPlayingFeed(ctx, spotifyConnect, this::onTrack);
    }

    View view() {
        return root;
    }

    /** The alarm is about to show its screen: end the screensaver first. */
    static void endCurrent() {
        StandbyScene s = current.get();
        if (s != null) s.end.run();
    }

    /** The night face is up: the alarm treats the TV as off, so its sunrise runs. */
    static boolean showingNight() {
        StandbyScene s = current.get();
        return s != null && s.nightTarget;
    }

    void start() {
        long now = SystemClock.elapsedRealtime();
        StandbyScene before = current.get();
        boolean handoff = true;
        NowPlayingFeed.Track carried = null;
        if (before != null && before != this) {
            phase = before.phase;
            startedAt = before.startedAt;
            if (before.musicShown) carried = before.shown;
        } else if (handoffAt >= 0 && now - handoffAt < HANDOFF_MS) {
            phase = handoffPhase;
            startedAt = handoffStartedAt;
            carried = handoffTrack;
        } else {
            handoff = false;
            startedAt = now;
        }
        handoffAt = -1;
        handoffTrack = null;
        current = new WeakReference<>(this);
        lyricInstantUntil = handoff && carried != null ? now + 1000 : -1;
        if (lyricsOn) {
            lookupThread = new HandlerThread("standby-lyrics");
            lookupThread.start();
            lookup = new Handler(lookupThread.getLooper());
        }
        nightTarget = isNight();
        night = nightTarget ? 1f : 0f;
        // Black and at the right backlight before anything shows, then the face fades in.
        setBacklight(nightTarget ? Sunrise.FLOOR : StandbyFace.DAY_BACKLIGHT);
        if (carried != null) showMusicNow(carried);
        paint();
        face.animate().cancel();
        if (handoff) {
            face.setAlpha(1f);   // the same face, in the same place: no fade
        } else {
            face.setAlpha(0f);
            face.animate().alpha(1f).setDuration(1200).setInterpolator(new DecelerateInterpolator()).start();
        }
        feed.start();
        main.post(tick);
        main.post(drift);
        if (lyricsOn) main.post(lyricTick);
    }

    void stop() {
        if (current.get() == this) current = new WeakReference<>(null);
        handoffAt = SystemClock.elapsedRealtime();
        handoffPhase = phase;
        handoffStartedAt = startedAt;
        handoffTrack = musicShown ? shown : null;
        feed.stop();
        main.removeCallbacksAndMessages(null);
        if (lookupThread != null) {
            lookup.removeCallbacksAndMessages(null);
            lookupThread.quitSafely();
            lookupThread = null;
            lookup = null;
        }
        lookingUp.clear();
        if (crossing != null) crossing.cancel();
        if (glowAnim != null) glowAnim.cancel();
        face.animate().cancel();
        releaseBacklight();
    }

    /** Play/pause, next and previous from the remote, without ending the screensaver. */
    boolean mediaKey(int keyCode) {
        return feed.control(keyCode);
    }

    // ---- time and night ----------------------------------------------------------------------------------------

    private boolean isNight() {
        Calendar c = Calendar.getInstance();
        int minute = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE);
        long now = System.currentTimeMillis();
        if (StandbyFace.forced(now)) return true;
        int wake = StandbyFace.wakeMinute(AlarmScheduler.planned(ctx, now), now, TimeZone.getDefault());
        return StandbyFace.night(minute, SleepMode.isOn(), wake);
    }

    private void update() {
        long now = System.currentTimeMillis();
        clock.setText(DateFormat.format(DateFormat.is24HourFormat(ctx) ? "HH:mm" : "h:mm", now));
        date.setText(DateFormat.format(DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEEMMMMd"), now)
                .toString().toUpperCase(Locale.getDefault()));
        boolean want = isNight();
        if (want && !nightTarget) {
            nightTarget = true;
            morningFrom = -1;
            crossTo(1f, 3000);   // 22:00 or sleep mode: 3 s
        } else if (!want && nightTarget) {
            nightTarget = false;
            morningFrom = SystemClock.elapsedRealtime();   // morning: ten minutes on this tick
        }
        if (morningFrom >= 0) {
            float p = Math.min(1f, (SystemClock.elapsedRealtime() - morningFrom) / 600_000f);
            night = 1f - p;
            setBacklight(StandbyFace.morningBacklight(p));
            paint();
            if (p >= 1f) morningFrom = -1;
        }
        if (shown != null && !shown.playing && pausedSince >= 0
                && SystemClock.elapsedRealtime() - pausedSince >= PAUSED_MS) {
            showMusic(false);
        }
    }

    private void crossTo(float target, long ms) {
        if (crossing != null) crossing.cancel();
        final float from = night;
        final float lightFrom = window.getAttributes().screenBrightness;
        crossing = ValueAnimator.ofFloat(0f, 1f);
        crossing.setDuration(ms);
        crossing.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
        crossing.addUpdateListener(a -> {
            float t = (float) a.getAnimatedValue();
            night = from + (target - from) * t;
            float light = lightFrom + ((target >= 1f ? Sunrise.FLOOR : StandbyFace.DAY_BACKLIGHT) - lightFrom) * t;
            if (Math.abs(light - window.getAttributes().screenBrightness) > 0.004f || t == 1f) setBacklight(light);
            paint();
        });
        crossing.start();
    }

    /** Colours for the current mix of day and night. */
    private void paint() {
        int ink = Sunrise.blend(DAY, RED, night);
        clock.setTextColor(ink);
        title.setTextColor(ink);
        lyric.setTextColor(ink);
        int dim = night >= 1f ? RED : Sunrise.blend(0x9EFFFFFF, RED, night);
        date.setTextColor(dim);
        second.setTextColor(dim);
        source.setImageTintList(ColorStateList.valueOf(night >= 1f ? RED : Sunrise.blend(0x8CFFFFFF, RED, night)));
        if (cover.getDrawable() != null && shown != null && shown.cover != null) {
            cover.setColorFilter(night <= 0f ? null
                    : new ColorMatrixColorFilter(StandbyFace.nightMatrix(RED, night)));
        } else {
            cover.setImageTintList(ColorStateList.valueOf(night >= 0.5f ? RED : 0x99FFFFFF));
            GradientDrawable tile = (GradientDrawable) cover.getBackground();
            if (tile != null) tile.setColor(night >= 0.5f ? 0x24B81C0C : 0x24FFFFFF);
        }
        // A 2 px edge so a black cover keeps its shape, by day only.
        coverEdge.setStroke(px(2), Sunrise.blend(0x1FFFFFFF, 0x00FFFFFF, night));
        // The glow follows the row in and out, and halves while paused: a faded cover shouldn't light its corner.
        boolean playing = shown != null && shown.playing;
        glowA.setAlpha((1f - night) * glowLevel * (playing ? 1f : 0.5f));
    }

    /**
     * The backlight. Fire OS ignores a screensaver window's own screenBrightness (checked on the TV: the override stays
     * NaN), so a 1 px see-through overlay carries it, as sleep mode's layer does; the window's value is kept too, for
     * Home's idle clock and firmware that honours it.
     */
    private void setBacklight(float value) {
        WindowManager.LayoutParams lp = window.getAttributes();
        lp.screenBrightness = value;
        window.setAttributes(lp);
        if (!android.provider.Settings.canDrawOverlays(ctx)) return;
        WindowManager wm = ctx.getSystemService(WindowManager.class);
        if (lightHolder == null) {
            lightHolder = new View(ctx);
            lightLp = new WindowManager.LayoutParams(1, 1, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                    android.graphics.PixelFormat.TRANSLUCENT);
            lightLp.gravity = Gravity.TOP | Gravity.START;
            lightLp.screenBrightness = value;
            try {
                wm.addView(lightHolder, lightLp);
            } catch (RuntimeException e) {
                lightHolder = null;
            }
        } else if (lightLp.screenBrightness != value) {
            lightLp.screenBrightness = value;
            wm.updateViewLayout(lightHolder, lightLp);
        }
    }

    private void releaseBacklight() {
        if (lightHolder == null) return;
        try {
            ctx.getSystemService(WindowManager.class).removeView(lightHolder);
        } catch (RuntimeException ignored) {
        }
        lightHolder = null;
    }

    // ---- what's playing --------------------------------------------------------------------------------------------

    private void onTrack(NowPlayingFeed.Track t) {
        main.removeCallbacks(nothingPlaying);
        if (t == null) {
            // Ride out a failed poll before calling it nothing.
            if (musicShown) main.postDelayed(nothingPlaying, NOTHING_MS);
            return;
        }
        if (t.playing) pausedSince = -1;
        else if (pausedSince < 0) pausedSince = SystemClock.elapsedRealtime();
        lookUp(t);
        boolean newSong = shown == null || !TextUtils.equals(shown.title, t.title) || !TextUtils.equals(shown.artist, t.artist);
        if (!musicShown) {
            fill(t);
            showMusic(true);
            return;
        }
        if (!newSong) {
            fill(t);   // cover arrived, or play state changed: no fade
            return;
        }
        // A new song: wait up to 3 s for its cover (Spotify sends the title first), then swap the row.
        pending = t;
        main.removeCallbacks(coverTimeout);
        // The lyrics decide the columns, so the swap waits for them too, in the same 3 s.
        if (t.cover != null && lyricsSettled(t)) swapRow(t);
        else main.postDelayed(coverTimeout, COVER_WAIT_MS);
    }

    private void swapRow(NowPlayingFeed.Track t) {
        main.removeCallbacks(coverTimeout);
        pending = null;
        row.animate().cancel();
        row.animate().alpha(0f).setDuration(300).setInterpolator(new AccelerateInterpolator()).withEndAction(() -> {
            fill(t);
            row.animate().alpha(1f).setDuration(500).setInterpolator(new DecelerateInterpolator()).start();
        }).start();
    }

    /** Puts the song in the row: cover (or the music tile), title, artist or "Paused", the source glyph. */
    private void fill(NowPlayingFeed.Track t) {
        NowPlayingFeed.Track before = shown;
        shown = t;
        if (t.cover != null) {
            cover.setBackground(null);
            cover.setPadding(0, 0, 0, 0);
            cover.setImageTintList(null);
            if (before == null || before.cover != t.cover) cover.setImageBitmap(t.cover);
        } else {
            GradientDrawable tile = new GradientDrawable();
            tile.setCornerRadius(px(20));
            cover.setBackground(tile);
            int pad = px(64);
            cover.setPadding(pad, pad, pad, pad);
            cover.setColorFilter(null);
            cover.setImageResource(R.drawable.ic_np_music);
        }
        cover.animate().alpha(t.playing ? 1f : 0.40f).setDuration(before == null ? 0 : 400).start();
        if (lyricsOn && !TextUtils.equals(t.lyricsKey, lyricsKey)) {
            lyricsKey = t.lyricsKey;
            LrcLib.Result known = lyricsKey == null ? null : LrcLib.CACHE.get(lyricsKey);
            lyrics = known == null ? null : known.lyrics;
            lyricShown = -1;
            lyric.animate().cancel();
            lyric.setAlpha(0f);
            lyric.setText(null);
        }
        setColumns(lyrics != null);
        if (!t.playing && lyricShown >= 0) {
            // Paused: the line goes with the cover's dim, and the current one comes back on resume.
            lyricShown = -1;
            lyric.animate().cancel();
            lyric.animate().alpha(0f).setDuration(400).start();
        }
        setText(title, t.title);
        setText(second, t.playing ? t.artist : ctx.getString(R.string.standby_paused));
        second.setVisibility(TextUtils.isEmpty(second.getText()) ? View.GONE : View.VISIBLE);
        // The source glyph only for another device: on this TV, you are looking at it.
        boolean remote = !"tv".equals(t.source);
        source.setVisibility(remote ? View.VISIBLE : View.GONE);
        if (remote) source.setImageResource(NowPlayingPanel.sourceIconRes(t.source));
        // The glow takes the cover's colour; two layers crossfade over 600 ms.
        int c = (t.cover != null ? CoverColours.of(t.cover) : CoverColours.NONE)[0] & 0xFFFFFF;
        glowShapeB.setColors(new int[]{0x38000000 | c, 0x14000000 | c, c});
        glowB.animate().cancel();
        glowB.setAlpha(0f);
        glowB.animate().alpha(glowA.getAlpha()).setDuration(600).withEndAction(() -> {
            glowShapeA.setColors(new int[]{0x38000000 | c, 0x14000000 | c, c});
            glowB.setAlpha(0f);
        }).start();
        paint();
    }

    // ---- Screensaver Lyrics ------------------------------------------------------------------------------------

    /** Looks this song's lyrics up on LRCLIB, off the main thread, unless they are known or on their way. */
    private void lookUp(NowPlayingFeed.Track t) {
        String key = t.lyricsKey;
        if (lookup == null || key == null || LrcLib.CACHE.containsKey(key) || !lookingUp.add(key)) return;
        LrcLib lrclib = new LrcLib();
        lookup.post(() -> {
            LrcLib.Result found;
            try {
                found = lrclib.find(t.title, t.artist, t.album, t.durationMs);
            } catch (java.io.IOException e) {
                found = null;   // offline: not cached, so a later showing asks again
            }
            final LrcLib.Result result = found;
            main.post(() -> onLyrics(key, result));
        });
    }

    private void onLyrics(String key, LrcLib.Result result) {
        if (!lookingUp.remove(key)) return;   // stopped meanwhile
        if (result != null) LrcLib.CACHE.put(key, result);
        if (pending != null && key.equals(pending.lyricsKey) && pending.cover != null) {
            swapRow(pending);   // the new song was waiting for these
            return;
        }
        // Late for the song in the row: only if its words already fit the narrower column.
        if (shown == null || !key.equals(lyricsKey) || lyrics != null || result == null || result.lyrics == null) return;
        float sourceWidth = source.getVisibility() == View.VISIBLE ? px(44) : 0;
        if (!StandbyLyric.fitsBeside(title.getPaint().measureText(title.getText().toString()),
                sourceWidth + second.getPaint().measureText(second.getText().toString()), px(560))) {
            return;
        }
        lyrics = result.lyrics;
        setColumns(true);
    }

    /** The lyrics of this song are known (found, or known to be missing), so the row can be drawn for good. */
    private boolean lyricsSettled(NowPlayingFeed.Track t) {
        return lookup == null || t.lyricsKey == null || LrcLib.CACHE.containsKey(t.lyricsKey);
    }

    /** Two columns (the song at 560 px, the line beside it) for a song with synced lyrics; one at 1040 otherwise. */
    private void setColumns(boolean two) {
        title.setMaxWidth(px(two ? 560 : 1040));
        second.setMaxWidth(px(two ? 560 : 1040));
        lyric.setVisibility(two ? View.VISIBLE : View.GONE);
    }

    /** Puts the line being sung in the column: out over 150 ms, then the new one in over 250, in place. */
    private void updateLyric() {
        if (lyrics == null || shown == null || !shown.playing || !musicShown) return;
        int want = StandbyLyric.lineAt(lyrics, feed.positionAt(SystemClock.elapsedRealtime()), lyricShown);
        if (want == lyricShown) return;
        lyricShown = want;
        String text = want < 0 ? null : lyrics.line(want);
        lyric.animate().cancel();
        if (SystemClock.elapsedRealtime() < lyricInstantUntil) {
            lyricInstantUntil = -1;
            lyric.setText(text);
            lyric.setAlpha(text == null ? 0f : 1f);
            return;
        }
        Runnable in = () -> {
            lyric.setText(text);
            if (text != null) {
                lyric.animate().alpha(1f).setDuration(250).setInterpolator(new DecelerateInterpolator()).start();
            }
        };
        if (lyric.getAlpha() <= 0f) in.run();
        else lyric.animate().alpha(0f).setDuration(150).setInterpolator(new AccelerateInterpolator())
                .withEndAction(in).start();
    }

    /** The row as it was on the face this one takes over from: no slide, no fade. */
    private void showMusicNow(NowPlayingFeed.Track t) {
        musicShown = true;
        fill(t);
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) clock.getLayoutParams();
        lp.topMargin = top(clock, 641);
        clock.setLayoutParams(lp);
        row.setAlpha(1f);
        glowLevel = 1f;
    }

    /** Music starts or ends: the clock slides between the centre and above the row (Reduce Motion: a crossfade). */
    private void showMusic(boolean on) {
        if (musicShown == on) return;
        musicShown = on;
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) clock.getLayoutParams();
        int to = top(clock, on ? 641 : 729);
        float shift = to - lp.topMargin;
        Runnable move = () -> {
            lp.topMargin = to;
            clock.setLayoutParams(lp);
            clock.setTranslationY(0f);
        };
        if (!on) {
            shown = null;
            // The next song (or this one again) starts its lyrics afresh.
            lyricsKey = null;
            lyrics = null;
            lyricShown = -1;
            lyric.animate().cancel();
            lyric.setAlpha(0f);
        }
        row.animate().cancel();
        animateGlow(on ? 1f : 0f, on ? 300 : 0, on ? 500 : 400);
        if (on) {
            slide(shift, move, 0);
            row.animate().alpha(1f).setStartDelay(300).setDuration(500).start();
        } else {
            row.animate().alpha(0f).setStartDelay(0).setDuration(400).withEndAction(() -> slide(shift, move, 0)).start();
        }
        paint();
    }

    private void animateGlow(float to, long delay, long ms) {
        if (glowAnim != null) glowAnim.cancel();
        glowAnim = ValueAnimator.ofFloat(glowLevel, to);
        glowAnim.setStartDelay(delay);
        glowAnim.setDuration(ms);
        glowAnim.addUpdateListener(a -> {
            glowLevel = (float) a.getAnimatedValue();
            paint();
        });
        glowAnim.start();
    }

    private void slide(float shift, Runnable move, long delay) {
        if (!motion) {
            clock.animate().alpha(0f).setDuration(200).withEndAction(() -> {
                move.run();
                clock.animate().alpha(1f).setDuration(300).start();
            }).start();
            return;
        }
        clock.animate().translationY(shift).setStartDelay(delay).setDuration(800)
                .setInterpolator(new PathInterpolator(0.2f, 0.8f, 0.2f, 1f)).withEndAction(move).start();
    }

    // ---- views -------------------------------------------------------------------------------------------------------

    private int px(float tv) {
        return Math.round(tv * density / 2f);
    }

    private GradientDrawable glowShape() {
        GradientDrawable g = new GradientDrawable();
        g.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        g.setGradientCenter(232f / 1920f, 864f / 1080f);
        g.setGradientRadius(px(900));
        int c = CoverColours.NONE[0] & 0xFFFFFF;
        g.setColors(new int[]{0x38000000 | c, 0x14000000 | c, c});
        return g;
    }

    private TextView text(Typeface font, float tvPx, float tracking) {
        TextView t = new TextView(ctx);
        t.setTypeface(font);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(tvPx));
        t.setLetterSpacing(tracking);
        t.setIncludeFontPadding(false);
        t.setSingleLine(true);
        return t;
    }

    /** The top margin that puts this text's baseline at TV y {@code baseline}. */
    private int top(TextView t, float baseline) {
        Paint.FontMetrics fm = t.getPaint().getFontMetrics();
        return Math.round(px(baseline) + fm.ascent);
    }

    private FrameLayout.LayoutParams at(float x, int topPx) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.START);
        lp.leftMargin = px(x);
        lp.topMargin = topPx;
        return lp;
    }

    private static FrameLayout.LayoutParams match() {
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    private static void setText(TextView v, CharSequence s) {
        if (!TextUtils.equals(v.getText(), s)) v.setText(s);
    }
}
