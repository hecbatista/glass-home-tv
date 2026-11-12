package com.gathin.tvhome;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.format.DateFormat;
import android.text.style.ImageSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.Calendar;
import java.util.Date;

/**
 * Glass Home's "Alarm and Sleep" screen (alarm_clock, sleep_mode), opened from Customize Home: the alarm and its
 * time, Repeat, the sunrise's length, the sound, a test, the Sleep Mode switch and Screensaver Lyrics. Styled like
 * Customize Home (GlassSettings). The time is edited inside its own row: OK, then Up and Down per segment, OK saves.
 */
public final class AlarmSettingsActivity extends Activity {
    private static final int[] REPEAT_LABELS = {R.string.alarm_every_day, R.string.alarm_weekdays,
            R.string.alarm_weekends, R.string.alarm_never};
    private static final int[] SOUND_LABELS = {R.string.alarm_chimes, R.string.alarm_harp, R.string.alarm_light_only};

    private SharedPreferences prefs;
    private GlassSettings glass;
    private AlarmSound preview;
    private float density;
    private boolean light, use24;

    private TextView alarmRow, timeHint, sleepRow, sleepHint, lyricsRow;
    /** Screensaver Lyrics' default (config standby_lyrics) until the switch is used. */
    private boolean lyricsDefault;
    private LinearLayout timeRow, segments;
    private TextView timeValue;
    private TextView[] segment;
    private final TextView[] repeatChoices = new TextView[4];
    private final TextView[] sunriseChoices = new TextView[4];
    private final TextView[] soundChoices = new TextView[3];

    /** The time being edited, minute of the day; -1 when not editing. */
    private int editing = -1;
    private int activeSegment;
    private long lastStep;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = AlarmScheduler.prefs(this);
        density = getResources().getDisplayMetrics().density;
        Config cfg = Config.load(this);
        light = Appearance.isLight(cfg.backgroundColor);
        use24 = DateFormat.is24HourFormat(this);
        glass = new GlassSettings(this, cfg.backgroundColor);
        preview = new AlarmSound(this);
        setContentView(build(cfg));
        refresh();
        alarmRow.requestFocus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (editing < 0) refresh();
    }

    @Override
    protected void onPause() {
        super.onPause();
        preview.stopNow();
    }

    private int dp(float v) {
        return Math.round(v * density);
    }

    // ---- layout ------------------------------------------------------------------------------------------------

    private View build(Config cfg) {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));
        TextView title = new TextView(this);
        content.addView(title, wrapRow());
        glass.styleScreen(scroll, content, title, cfg.backgroundColor);
        title.setText(R.string.alarm_and_sleep);

        alarmRow = add(content, 14);
        glass.rowPill(alarmRow, false);
        alarmRow.setOnClickListener(v -> {
            boolean on = !AlarmScheduler.isOn(this);
            prefs.edit().putBoolean(AlarmScheduler.ON_KEY, on).apply();
            changed();
        });

        timeRow = new LinearLayout(this);
        content.addView(timeRow, row(12));
        TextView timeLabel = new TextView(this);
        timeLabel.setText(R.string.alarm_time);
        timeValue = glass.valueRow(timeRow, timeLabel, false, 0xFF);
        segments = new LinearLayout(this);
        segments.setOrientation(LinearLayout.HORIZONTAL);
        segments.setGravity(Gravity.CENTER_VERTICAL);
        segments.setVisibility(View.GONE);
        timeRow.addView(segments, new LinearLayout.LayoutParams(-2, -2));
        timeRow.setOnClickListener(v -> startEditing());

        timeHint = new TextView(this);
        content.addView(timeHint, hintLp(7));
        glass.hint(timeHint);
        timeHint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);

        section(content, R.string.alarm_repeat, repeatChoices, REPEAT_LABELS, i -> {
            prefs.edit().putInt(AlarmScheduler.REPEAT_KEY, i).apply();
            changed();
        });
        section(content, R.string.alarm_sunrise, sunriseChoices, null, i -> {
            prefs.edit().putInt(AlarmScheduler.SUNRISE_KEY, AlarmScheduler.SUNRISE_CHOICES[i]).apply();
            changed();
        });
        section(content, R.string.alarm_sound_label, soundChoices, SOUND_LABELS, i -> {
            prefs.edit().putInt(AlarmScheduler.SOUND_KEY, i).apply();
            preview.preview(i);
            refresh();
        });

        TextView test = add(content, 18);
        test.setText(R.string.alarm_test);
        glass.rowPill(test, true);
        test.setOnClickListener(v -> testAlarm());

        sleepRow = add(content, 18);
        glass.rowPill(sleepRow, false);
        sleepRow.setOnClickListener(v -> {
            boolean on = !prefs.getBoolean(HomeKeyService.SLEEP_KEY, false);
            prefs.edit().putBoolean(HomeKeyService.SLEEP_KEY, on).apply();
            HomeKeyService.setSleepMode(on);
            refresh();
        });
        sleepRow.setVisibility(cfg.sleepMode ? View.VISIBLE : View.GONE);
        // The hint under the last row explains the ≡ hold: scroll it into view with its row.
        sleepRow.setOnFocusChangeListener((v, focused) -> {
            if (focused) scroll.post(() -> scroll.smoothScrollTo(0, content.getHeight()));
        });
        sleepHint = new TextView(this);
        content.addView(sleepHint, hintLp(7));
        glass.hint(sleepHint);
        sleepHint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        sleepHint.setVisibility(cfg.sleepMode ? View.VISIBLE : View.GONE);

        // Screensaver Lyrics: the line being sung beside the song on the StandBy face, the screen's last row.
        lyricsDefault = cfg.standbyLyrics;
        boolean lyricsOffered = cfg.lyrics && cfg.standbyClock;
        lyricsRow = add(content, 18);
        glass.rowPill(lyricsRow, false);
        lyricsRow.setOnClickListener(v -> {
            prefs.edit().putBoolean(StandbyLyric.KEY, !prefs.getBoolean(StandbyLyric.KEY, lyricsDefault)).apply();
            refresh();
        });
        lyricsRow.setVisibility(lyricsOffered ? View.VISIBLE : View.GONE);
        lyricsRow.setOnFocusChangeListener((v, focused) -> {
            if (focused) scroll.post(() -> scroll.smoothScrollTo(0, content.getHeight()));
        });
        TextView lyricsHint = new TextView(this);
        content.addView(lyricsHint, hintLp(7));
        glass.hint(lyricsHint);
        lyricsHint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        lyricsHint.setText(R.string.standby_lyrics_hint);
        lyricsHint.setVisibility(lyricsOffered ? View.VISIBLE : View.GONE);
        return scroll;
    }

    private interface Choice {
        void chosen(int index);
    }

    private void section(LinearLayout content, int label, TextView[] views, int[] labels, Choice choice) {
        TextView name = new TextView(this);
        content.addView(name, wrapRow());
        glass.section(name, label);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        content.addView(row, wrapRow());
        for (int i = 0; i < views.length; i++) {
            TextView t = new TextView(this);
            t.setFocusable(true);
            t.setClickable(true);
            final int index = i;
            t.setOnClickListener(v -> choice.chosen(index));
            row.addView(t, new LinearLayout.LayoutParams(0, dp(48), 1f));
            views[i] = t;
            if (labels != null) t.setText(labels[i]);
        }
        glass.choiceRow(row, 12);
    }

    private TextView add(LinearLayout content, int topDp) {
        TextView t = new TextView(this);
        t.setFocusable(true);
        t.setClickable(true);
        content.addView(t, row(topDp));
        return t;
    }

    private LinearLayout.LayoutParams row(int topDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(48));
        lp.topMargin = dp(topDp);
        return lp;
    }

    private static LinearLayout.LayoutParams wrapRow() {
        return new LinearLayout.LayoutParams(-1, -2);
    }

    private LinearLayout.LayoutParams hintLp(int topDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(topDp);
        return lp;
    }

    // ---- state -------------------------------------------------------------------------------------------------

    private void changed() {
        AlarmScheduler.schedule(this);
        refresh();
    }

    private void refresh() {
        boolean on = AlarmScheduler.isOn(this);
        alarmRow.setText(getString(R.string.alarm_state, getString(on ? R.string.on : R.string.off)));
        timeValue.setText(format(AlarmScheduler.minuteOfDay(this)));
        timeHint.setText(on ? ringsIn() : getString(R.string.alarm_off_hint));
        String[] sunrise = {getString(R.string.off), getString(R.string.alarm_minutes, 10),
                getString(R.string.alarm_minutes, 20), getString(R.string.alarm_minutes, 30)};
        CharSequence[] repeat = new CharSequence[4], sound = new CharSequence[3];
        for (int i = 0; i < 4; i++) repeat[i] = getString(REPEAT_LABELS[i]);
        for (int i = 0; i < 3; i++) sound[i] = getString(SOUND_LABELS[i]);
        label(repeatChoices, repeat, AlarmScheduler.repeat(this));
        int sunriseIndex = 0;
        for (int i = 0; i < 4; i++) if (AlarmScheduler.SUNRISE_CHOICES[i] == AlarmScheduler.sunriseMinutes(this)) sunriseIndex = i;
        label(sunriseChoices, sunrise, sunriseIndex);
        label(soundChoices, sound, AlarmScheduler.sound(this));
        boolean sleep = prefs.getBoolean(HomeKeyService.SLEEP_KEY, false);
        sleepRow.setText(getString(R.string.sleep_mode_state, getString(sleep ? R.string.on : R.string.off)));
        boolean lyrics = prefs.getBoolean(StandbyLyric.KEY, lyricsDefault);
        lyricsRow.setText(getString(R.string.standby_lyrics_state, getString(lyrics ? R.string.on : R.string.off)));
        sleepHint.setText(HomeKeyService.isRunning() && Settings.canDrawOverlays(this) ? sleepHintText()
                : getString(R.string.sleep_mode_needs));
    }

    private void label(TextView[] views, CharSequence[] labels, int chosen) {
        for (int i = 0; i < views.length; i++) glass.label(views[i], labels[i], i == chosen, 0);
    }

    /** "Rings in 8 hours 20 minutes. The screen starts to brighten at 6:40 AM." */
    private String ringsIn() {
        AlarmPlan plan = AlarmScheduler.planned(this, System.currentTimeMillis());
        if (plan == null) return getString(R.string.alarm_off_hint);
        long minutes = Math.max(1, Math.round((plan.alarmAt - System.currentTimeMillis()) / 60_000.0));
        String when;
        if (minutes > 24 * 60) {
            when = getString(R.string.alarm_rings_on, DateFormat.format("EEEE", plan.alarmAt), time(plan.alarmAt));
        } else if (minutes < 60) {
            when = getString(R.string.alarm_rings_in,
                    getResources().getQuantityString(R.plurals.alarm_minutes_long, (int) minutes, (int) minutes));
        } else {
            long h = minutes / 60, m = minutes % 60;
            String hours = getResources().getQuantityString(R.plurals.alarm_hours, (int) h, (int) h);
            when = getString(R.string.alarm_rings_in, m == 0 ? hours : hours + " "
                    + getResources().getQuantityString(R.plurals.alarm_minutes_long, (int) m, (int) m));
        }
        if (plan.sunriseAt < plan.alarmAt) when += " " + getString(R.string.alarm_brightens_at, time(plan.sunriseAt));
        return when;
    }

    private String time(long at) {
        return DateFormat.getTimeFormat(this).format(new Date(at));
    }

    private String format(int minuteOfDay) {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, minuteOfDay / 60);
        c.set(Calendar.MINUTE, minuteOfDay % 60);
        return time(c.getTimeInMillis());
    }

    /** "Hold [≡] for a second in any app to dim the screen. …", the Menu glyph from Material Symbols inline. */
    private CharSequence sleepHintText() {
        String text = getString(R.string.sleep_mode_hint);
        int at = text.indexOf("%1$s");
        SpannableStringBuilder s = new SpannableStringBuilder(text.replace("%1$s", "≡"));
        if (at >= 0) {
            Drawable menu = getDrawable(R.drawable.ic_menu).mutate();
            menu.setTint(sleepHint.getCurrentTextColor());
            int size = Math.round(sleepHint.getTextSize() * 0.9f);
            menu.setBounds(0, 0, size, size);
            s.setSpan(new ImageSpan(menu, ImageSpan.ALIGN_BASELINE), at, at + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return s;
    }

    private void testAlarm() {
        long now = System.currentTimeMillis();
        AlarmPlan plan = AlarmPlan.test(now, 0, 30);
        startActivity(new Intent(this, AlarmActivity.class).putExtras(AlarmScheduler.ring(plan, 0, false, true)));
    }

    // ---- the time, edited in place -------------------------------------------------------------------------------

    private void startEditing() {
        editing = AlarmScheduler.minuteOfDay(this);
        activeSegment = 0;
        timeValue.setVisibility(View.GONE);
        segments.setVisibility(View.VISIBLE);
        timeHint.setText(R.string.alarm_editing_hint);
        drawSegments();
    }

    private void stopEditing(boolean save) {
        if (save) {
            prefs.edit().putInt(AlarmScheduler.MINUTE_KEY, editing).putBoolean(AlarmScheduler.ON_KEY, true).apply();
        }
        editing = -1;
        segments.setVisibility(View.GONE);
        timeValue.setVisibility(View.VISIBLE);
        changed();
    }

    /** "[7] : [00] [AM]" (or "[07] : [05]"): the active segment as a capsule in the pill's inverse colours. */
    private void drawSegments() {
        int count = use24 ? 2 : 3;
        if (segment == null) {
            segment = new TextView[4];
            for (int i = 0; i < 4; i++) {
                TextView t = new TextView(this);
                t.setTypeface(getResources().getFont(R.font.inter_semibold));
                t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f);
                t.setFontFeatureSettings("tnum");
                t.setIncludeFontPadding(false);
                t.setGravity(Gravity.CENTER);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, dp(32));
                if (i > 0) lp.setMarginStart(dp(i == 1 || i == 2 ? 2 : 6));
                segments.addView(t, lp);
                segment[i] = t;
            }
            segment[1].setText(":");
        }
        int hour = editing / 60, minute = editing % 60;
        segment[0].setText(use24 ? String.format("%02d", hour) : String.valueOf(hour % 12 == 0 ? 12 : hour % 12));
        segment[2].setText(String.format("%02d", minute));
        segment[3].setText(hour < 12 ? DateFormat.format("a", amPm(0)) : DateFormat.format("a", amPm(12)));
        segment[3].setVisibility(use24 ? View.GONE : View.VISIBLE);
        int pill = light ? Appearance.INK_DARK : 0xFFFFFFFF;
        int ink = light ? 0xFFFFFFFF : Appearance.INK_DARK;
        int[] index = {0, -1, 1, 2};
        for (int i = 0; i < 4; i++) {
            TextView t = segment[i];
            boolean active = index[i] == activeSegment && index[i] < count;
            t.setPadding(i == 1 ? 0 : dp(8), 0, i == 1 ? 0 : dp(8), 0);
            if (active) {
                GradientDrawable capsule = new GradientDrawable();
                capsule.setCornerRadius(dp(16));
                capsule.setColor(ink);
                t.setBackground(capsule);
                t.setTextColor(pill);
            } else {
                t.setBackground(null);
                t.setTextColor(i == 1 ? Appearance.withAlphaOf(0x9E000000, ink) : ink);
            }
        }
    }

    private static long amPm(int hour) {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, hour);
        return c.getTimeInMillis();
    }

    private void step(int direction) {
        int hour = editing / 60, minute = editing % 60;
        if (activeSegment == 0) {
            hour = use24 ? (hour + direction + 24) % 24 : stepHour12(hour, direction);
        } else if (activeSegment == 1) {
            minute = (minute + 5 * direction + 60) % 60;
            minute -= minute % 5;
        } else {
            hour = (hour + 12) % 24;
        }
        editing = hour * 60 + minute;
        drawSegments();
    }

    /** The 12-hour clock's hour up or down, wrapping 12 → 1 and keeping AM or PM. */
    private static int stepHour12(int hour, int direction) {
        int h12 = hour % 12 == 0 ? 12 : hour % 12;
        h12 = (h12 - 1 + direction + 12) % 12 + 1;
        boolean pm = hour >= 12;
        return (h12 % 12) + (pm ? 12 : 0);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (editing < 0) return super.dispatchKeyEvent(event);
        int code = event.getKeyCode();
        boolean down = event.getAction() == KeyEvent.ACTION_DOWN;
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (down) {
                    // A held key repeats every 120 ms after 400 ms.
                    long t = event.getEventTime();
                    boolean repeat = event.getRepeatCount() > 0;
                    if (!repeat || (t - event.getDownTime() >= 400 && t - lastStep >= 120)) {
                        lastStep = t;
                        step(code == KeyEvent.KEYCODE_DPAD_UP ? 1 : -1);
                    }
                }
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (down && event.getRepeatCount() == 0) {
                    int last = use24 ? 1 : 2;
                    activeSegment = Math.max(0, Math.min(last, activeSegment + (code == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1)));
                    drawSegments();
                }
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                if (!down && !event.isCanceled()) stopEditing(true);
                return true;
            case KeyEvent.KEYCODE_BACK:
            case KeyEvent.KEYCODE_ESCAPE:
                if (!down && !event.isCanceled()) stopEditing(false);
                return true;
            default:
                return super.dispatchKeyEvent(event);
        }
    }
}
