package com.gathin.tvhome;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.format.DateFormat;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.util.Date;
import java.util.Locale;

/**
 * Glass Home's alarm screen (config alarm_clock), in the style of iPhone's StandBy: a 600 px clock over a sky and a
 * rising sun. During the sunrise the colours run from night red to warm white while the backlight climbs from the
 * panel's dimmest; at the alarm time gentle tones start (AlarmSound) with Snooze and Stop. Positions are TV px at
 * 1920×1080, as in the design brief (.design-reviews/alarm-and-sleep.md).
 */
public final class AlarmActivity extends Activity {
    static final String EXTRA_WAS_ON = "com.gathin.tvhome.extra.WAS_ON";

    private static final long TICK_MS = 2000;
    private static final long RING_MS = 10 * 60_000L;
    private static final int GIVE_UP_AFTER = 3;
    private static final long REVEAL_MS = 10_000;

    private enum Phase { SUNRISE, RINGING, SNOOZED, ENDING }

    private final Handler main = new Handler(Looper.getMainLooper());
    private float density;
    private boolean motion;
    private AlarmSound sound;
    private AlarmPlan plan;
    private int unanswered;
    private boolean snoozedRing, test;
    private Phase phase = Phase.SUNRISE;
    private long ringStartedAt;

    private GradientDrawable sky, sun;
    private View sunView, veil;
    private TextView clock, date, status, snoozePill, stopPill;
    private ValueAnimator breathing;

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            update();
            main.postDelayed(this, TICK_MS);
        }
    };
    private final Runnable hideReveal = () -> {
        if (phase == Phase.SUNRISE) stopPill.setVisibility(View.GONE);
    };
    private final Runnable unansweredRing = this::autoSnooze;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        density = getResources().getDisplayMetrics().density;
        Config cfg = Config.load(this);
        motion = cfg.animations && !cfg.reducedMotion;
        sound = new AlarmSound(this);
        // Black and at the panel's dimmest before anything draws, so waking from standby never flashes.
        getWindow().getDecorView().setBackgroundColor(0xFF000000);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setBrightness(Sunrise.FLOOR);
        setContentView(build());
        start(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        start(intent);
    }

    private int px(float tv) {
        return Math.round(tv * density / 2f);
    }

    // ---- what happens ------------------------------------------------------------------------------------------

    private void start(Intent intent) {
        long alarmAt = intent.getLongExtra(AlarmScheduler.EXTRA_ALARM_AT, System.currentTimeMillis());
        long sunriseAt = intent.getLongExtra(AlarmScheduler.EXTRA_SUNRISE_AT, alarmAt);
        plan = new AlarmPlan(alarmAt, sunriseAt);
        unanswered = intent.getIntExtra(AlarmScheduler.EXTRA_RINGS, 0);
        snoozedRing = intent.getBooleanExtra(AlarmScheduler.EXTRA_SNOOZED, false);
        test = intent.getBooleanExtra(AlarmScheduler.EXTRA_TEST, false);
        main.removeCallbacksAndMessages(null);
        sound.stopNow();
        phase = Phase.SUNRISE;
        stopPill.setVisibility(View.GONE);
        snoozePill.setVisibility(View.GONE);
        showStatus(false);
        veil.animate().cancel();
        veil.setAlpha(1f);
        // A sunrise fades in over 4 s; without one (sunrise Off, a snooze) the clock comes up in 2.
        veil.animate().alpha(0f).setDuration(plan.alarmAt > plan.sunriseAt ? 4000 : 2000).start();
        update();
        main.postDelayed(tick, TICK_MS);
    }

    private void update() {
        long now = System.currentTimeMillis();
        float p = phase == Phase.SUNRISE ? plan.progress(now) : 1f;
        sky.setColors(new int[]{Sunrise.key(p, Sunrise.SKY), Sunrise.key(p, Sunrise.HORIZON)});
        int sunColour = Sunrise.key(p, Sunrise.SUN) & 0xFFFFFF;
        sun.setColors(new int[]{0xFF000000 | sunColour, 0x73000000 | sunColour, sunColour});
        int ink = Sunrise.key(p, Sunrise.CLOCK);
        for (TextView t : new TextView[]{clock, date, status}) t.setTextColor(ink);
        status.setCompoundDrawableTintList(ColorStateList.valueOf(ink));
        clock.setText(DateFormat.format(DateFormat.is24HourFormat(this) ? "HH:mm" : "h:mm", now));
        date.setText(DateFormat.format(DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEEMMMMd"), now)
                .toString().toUpperCase(Locale.getDefault()));
        if (phase == Phase.SUNRISE) {
            setBrightness(Sunrise.backlight(p));
            if (now >= plan.alarmAt) ring();
        } else if (phase == Phase.RINGING) {
            // 0.80 at the alarm time to full over a minute.
            setBrightness(Sunrise.TOP + (1f - Sunrise.TOP) * Math.min(1f, (now - ringStartedAt) / 60_000f));
        }
    }

    private void ring() {
        phase = Phase.RINGING;
        ringStartedAt = System.currentTimeMillis();
        main.removeCallbacks(hideReveal);
        sound.ring(AlarmScheduler.sound(this), snoozedRing);
        showStatus(false);
        snoozePill.setVisibility(View.VISIBLE);
        stopPill.setVisibility(View.VISIBLE);
        place(stopPill, 972);
        fadeIn(snoozePill);
        fadeIn(stopPill);
        snoozePill.requestFocus();
        if (motion) {
            breathing = ObjectAnimator.ofFloat(sunView, View.ALPHA, 1f, 0.85f);
            breathing.setDuration(2000);
            breathing.setRepeatMode(ValueAnimator.REVERSE);
            breathing.setRepeatCount(ValueAnimator.INFINITE);
            breathing.setInterpolator(new AccelerateDecelerateInterpolator());
            breathing.start();
        }
        main.postDelayed(unansweredRing, RING_MS);
    }

    /** Snooze, by a key: the tones fade, Stop stays, and the alarm rings again in nine minutes. */
    private void snooze() {
        endRinging(600, null);
        AlarmScheduler.snooze(this, unanswered, test);
        showSnoozed();
    }

    /** Ten minutes with nobody answering: snooze on their behalf, three rings in all, then give up. */
    private void autoSnooze() {
        if (phase != Phase.RINGING) return;
        if (unanswered + 1 >= GIVE_UP_AFTER) {
            giveUp();
            return;
        }
        endRinging(600, null);
        AlarmScheduler.snooze(this, unanswered + 1, test);
        showSnoozed();
    }

    private void showSnoozed() {
        phase = Phase.SNOOZED;
        setBrightness(1f);
        snoozePill.setVisibility(View.GONE);
        place(stopPill, 780);
        stopPill.requestFocus();
        showStatus(true);
    }

    /** Stop: the tones fade, the volume goes back, and Home takes over (Launcher settings for a test). */
    private void stop() {
        if (phase == Phase.ENDING) return;
        phase = Phase.ENDING;
        AlarmScheduler.cancelSnooze(this);
        AlarmScheduler.finished(this, test);
        endRinging(600, () -> {
            sound.restoreVolume();
            leave();
        });
    }

    /** Stop during the sunrise: today's ring is skipped; tomorrow's is already set. */
    private void skipToday() {
        phase = Phase.ENDING;
        AlarmScheduler.finished(this, test);
        leave();
    }

    /** Three unanswered rings: the tones fade over 4 s, the clock stays a minute, then the TV goes back to standby. */
    private void giveUp() {
        phase = Phase.ENDING;
        AlarmScheduler.finished(this, test);
        snoozePill.setVisibility(View.GONE);
        stopPill.setVisibility(View.GONE);
        endRinging(4000, sound::restoreVolume);
        main.postDelayed(() -> {
            setBrightness(WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE);
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            // The remote helper can put the TV to standby; without it the TV's own timers take it.
            HomeKeyService.lockScreen();
            finish();
        }, 64_000);
    }

    /** The ring ends: the sun stops breathing and the tones fade over {@code fadeMs}; {@code after} runs once silent. */
    private void endRinging(long fadeMs, Runnable after) {
        main.removeCallbacks(unansweredRing);
        if (breathing != null) {
            breathing.cancel();
            breathing = null;
            sunView.setAlpha(1f);
        }
        sound.fadeOut(fadeMs, after);
    }

    private void leave() {
        setBrightness(WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE);
        if (!test) {
            startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setPackage(getPackageName())
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        }
        finish();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    @Override
    protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        // Home while ringing snoozes, like Back.
        if (phase == Phase.RINGING) snooze();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (isFinishing()) return;
        if (phase == Phase.RINGING) {
            // The TV was turned off while it rang: that's a snooze too.
            snooze();
        } else if (phase == Phase.SUNRISE) {
            // Turned off during the sunrise: it still rings at the alarm time, waking the TV for it.
            AlarmScheduler.ringAt(this, plan.alarmAt, test);
            main.removeCallbacksAndMessages(null);
            finish();
        }
    }

    @Override
    protected void onDestroy() {
        main.removeCallbacksAndMessages(null);
        if (breathing != null) breathing.cancel();
        if (phase != Phase.ENDING) sound.stopNow();
        super.onDestroy();
    }

    // ---- keys ----------------------------------------------------------------------------------------------------

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int code = event.getKeyCode();
        if (code == KeyEvent.KEYCODE_VOLUME_UP || code == KeyEvent.KEYCODE_VOLUME_DOWN
                || code == KeyEvent.KEYCODE_VOLUME_MUTE) {
            return super.dispatchKeyEvent(event);
        }
        boolean up = event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled();
        boolean ok = code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER;
        switch (phase) {
            case SUNRISE:
                // Any key shows Stop; OK on it skips today's ring; Back or ten quiet seconds hide it.
                if (!up) return true;
                if (code == KeyEvent.KEYCODE_BACK && stopPill.getVisibility() == View.VISIBLE) {
                    stopPill.setVisibility(View.GONE);
                } else if (ok && stopPill.isFocused()) {
                    skipToday();
                } else {
                    place(stopPill, 780);
                    if (stopPill.getVisibility() != View.VISIBLE) fadeIn(stopPill);
                    stopPill.setVisibility(View.VISIBLE);
                    stopPill.requestFocus();
                }
                main.removeCallbacks(hideReveal);
                main.postDelayed(hideReveal, REVEAL_MS);
                return true;
            case RINGING:
                if (code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    if (!up) (code == KeyEvent.KEYCODE_DPAD_LEFT ? snoozePill : stopPill).requestFocus();
                    return true;
                }
                if (!up) return true;
                // Stop is always deliberate (Right, then OK); every other key snoozes, like iPhone's side button.
                if (ok && stopPill.isFocused()) stop();
                else snooze();
                return true;
            case SNOOZED:
                if (!up) return true;
                if (ok) stop();
                else if (code == KeyEvent.KEYCODE_BACK) leave();
                return true;
            default:
                return true;
        }
    }

    // ---- the screen ----------------------------------------------------------------------------------------------

    private void setBrightness(float value) {
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        if (lp.screenBrightness == value) return;
        lp.screenBrightness = value;
        getWindow().setAttributes(lp);
    }

    private View build() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF000000);
        View skyView = new View(this);
        sky = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xFF000000, 0xFF000000});
        skyView.setBackground(sky);
        root.addView(skyView, match());
        sunView = new View(this);
        sun = new GradientDrawable();
        sun.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        sun.setGradientCenter(0.5f, 1260f / 1080f);
        sun.setGradientRadius(px(1000));
        sunView.setBackground(sun);
        sunView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        root.addView(sunView, match());

        Typeface display = getResources().getFont(R.font.inter_display_semibold);
        Typeface semibold = getResources().getFont(R.font.inter_semibold);
        clock = text(display, 600, -0.03f);
        FrameLayout.LayoutParams clp = wrap(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        clp.topMargin = baselineTop(clock, 704);
        root.addView(clock, clp);

        date = caption(semibold);
        FrameLayout.LayoutParams dlp = wrap(Gravity.TOP | Gravity.START);
        dlp.leftMargin = px(112);
        dlp.topMargin = baselineTop(date, 160);
        root.addView(date, dlp);
        status = caption(semibold);
        status.setCompoundDrawablePadding(px(12));
        FrameLayout.LayoutParams slp = wrap(Gravity.TOP | Gravity.END);
        slp.rightMargin = px(1920 - 1808);
        slp.topMargin = baselineTop(status, 160);
        root.addView(status, slp);

        snoozePill = pill(R.string.alarm_snooze, semibold);
        stopPill = pill(R.string.alarm_stop, semibold);
        root.addView(snoozePill, pillAt(588));
        root.addView(stopPill, pillAt(972));
        snoozePill.setOnClickListener(v -> snooze());
        stopPill.setOnClickListener(v -> stop());

        veil = new View(this);
        veil.setBackgroundColor(0xFF000000);
        root.addView(veil, match());
        return root;
    }

    private void showStatus(boolean snoozed) {
        String text;
        int icon;
        if (snoozed) {
            text = getString(R.string.alarm_snoozed_until, time(System.currentTimeMillis() + AlarmScheduler.SNOOZE_MS));
            icon = R.drawable.ic_snooze;
        } else {
            text = test ? getString(R.string.alarm_test_caption) : time(plan.alarmAt);
            icon = R.drawable.ic_alarm;
        }
        status.setText(text.toUpperCase(Locale.getDefault()));
        Drawable d = getDrawable(icon).mutate();
        d.setBounds(0, 0, px(40), px(40));
        status.setCompoundDrawablesRelative(d, null, null, null);
    }

    private String time(long at) {
        return DateFormat.getTimeFormat(this).format(new Date(at));
    }

    private TextView text(Typeface font, float tvPx, float tracking) {
        TextView t = new TextView(this);
        t.setTypeface(font);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(tvPx));
        t.setLetterSpacing(tracking);
        t.setIncludeFontPadding(false);
        t.setSingleLine(true);
        return t;
    }

    private TextView caption(Typeface font) {
        TextView t = text(font, 40, 0.08f);
        t.setGravity(Gravity.CENTER_VERTICAL);
        return t;
    }

    /** A 360×96 pill: 40% black at rest so white words hold 7:1 over the full sun, Home's white pill when focused. */
    private TextView pill(int label, Typeface font) {
        TextView t = text(font, 34, -0.01f);
        t.setText(label);
        t.setGravity(Gravity.CENTER);
        t.setFocusable(true);
        GradientDrawable focused = new GradientDrawable();
        focused.setCornerRadius(px(48));
        focused.setColor(0xF5FFFFFF);
        GradientDrawable rest = new GradientDrawable();
        rest.setCornerRadius(px(48));
        rest.setColor(0x66000000);
        StateListDrawable bg = new StateListDrawable();
        bg.addState(new int[]{android.R.attr.state_focused}, focused);
        bg.addState(new int[]{}, rest);
        t.setBackground(bg);
        t.setTextColor(new ColorStateList(new int[][]{{android.R.attr.state_focused}, {}},
                new int[]{Appearance.INK_DARK, 0xFFFFFFFF}));
        t.setVisibility(View.GONE);
        return t;
    }

    private FrameLayout.LayoutParams pillAt(float x) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(px(360), px(96), Gravity.TOP | Gravity.START);
        lp.leftMargin = px(x);
        lp.topMargin = px(836);
        return lp;
    }

    private void place(View pill, float x) {
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) pill.getLayoutParams();
        if (lp.leftMargin == px(x)) return;
        lp.leftMargin = px(x);
        pill.setLayoutParams(lp);
    }

    private void fadeIn(View v) {
        if (!motion) {
            v.setAlpha(1f);
            return;
        }
        v.setAlpha(0f);
        v.animate().alpha(1f).setDuration(240).start();
    }

    /** The top margin that puts this text's baseline at TV y {@code baseline}. */
    private int baselineTop(TextView t, float baseline) {
        Paint.FontMetrics fm = t.getPaint().getFontMetrics();
        return Math.round(px(baseline) + fm.ascent);
    }

    private static FrameLayout.LayoutParams match() {
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    private static FrameLayout.LayoutParams wrap(int gravity) {
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, gravity);
    }
}
