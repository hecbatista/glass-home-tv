package io.github.hecbatista.glasshome;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import java.util.TimeZone;

/**
 * Glass Home's alarm (config alarm_clock) in AlarmManager. The daily alarm is one alarm-clock alarm at the sunrise's
 * start, which wakes the TV from standby (AlarmReceiver); a snooze is a second one, so snoozing never moves the daily
 * alarm. The choices live in Launcher settings' preferences; every change, a reboot, a clock or time zone change and
 * an update call {@link #schedule} again.
 */
final class AlarmScheduler {
    static final String ON_KEY = "alarm_on";
    static final String MINUTE_KEY = "alarm_minute_of_day";
    static final String REPEAT_KEY = "alarm_repeat";
    static final String SUNRISE_KEY = "alarm_sunrise_minutes";
    static final String SOUND_KEY = "alarm_sound";
    static final int DEFAULT_MINUTE = 7 * 60;
    static final int DEFAULT_SUNRISE = 20;
    static final int[] SUNRISE_CHOICES = {0, 10, 20, 30};

    static final String EXTRA_ALARM_AT = "io.github.hecbatista.glasshome.extra.ALARM_AT";
    static final String EXTRA_SUNRISE_AT = "io.github.hecbatista.glasshome.extra.SUNRISE_AT";
    /** Rings that went unanswered so far (auto-snoozes); after the third the alarm gives up. */
    static final String EXTRA_RINGS = "io.github.hecbatista.glasshome.extra.RINGS";
    static final String EXTRA_SNOOZED = "io.github.hecbatista.glasshome.extra.SNOOZED";
    static final String EXTRA_TEST = "io.github.hecbatista.glasshome.extra.TEST";

    static final long SNOOZE_MS = 9 * 60_000L;
    private static final int DAILY = 0, SNOOZE = 1;

    private AlarmScheduler() {}

    static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE);
    }

    static boolean isOn(Context ctx) {
        return prefs(ctx).getBoolean(ON_KEY, false);
    }

    static int minuteOfDay(Context ctx) {
        return prefs(ctx).getInt(MINUTE_KEY, DEFAULT_MINUTE);
    }

    static int repeat(Context ctx) {
        return prefs(ctx).getInt(REPEAT_KEY, AlarmPlan.EVERY_DAY);
    }

    static int sunriseMinutes(Context ctx) {
        return prefs(ctx).getInt(SUNRISE_KEY, DEFAULT_SUNRISE);
    }

    static int sound(Context ctx) {
        return prefs(ctx).getInt(SOUND_KEY, AlarmTones.CHIMES);
    }

    /** The next alarm after {@code after} by the saved choices, or null when it is off. */
    static AlarmPlan planned(Context ctx, long after) {
        if (!isOn(ctx)) return null;
        return AlarmPlan.next(after, minuteOfDay(ctx), sunriseMinutes(ctx), repeat(ctx), TimeZone.getDefault());
    }

    /** Sets (or clears) the daily alarm from the saved choices. */
    static void schedule(Context ctx) {
        scheduleAfter(ctx, System.currentTimeMillis());
    }

    /**
     * The daily alarm, counting from {@code after}. The receiver passes the alarm it is ringing, so the alarm it is
     * running (still ahead of the clock during its sunrise) isn't found again.
     */
    static void scheduleAfter(Context ctx, long after) {
        AlarmManager am = ctx.getSystemService(AlarmManager.class);
        am.cancel(fireIntent(ctx, DAILY, null));
        AlarmPlan plan = Config.load(ctx).alarmClock ? planned(ctx, after) : null;
        if (plan != null) set(ctx, am, DAILY, plan, ring(plan, 0, false, false));
    }

    /** Snoozed (by a key, or after a ring nobody answered): rings again in nine minutes, with no sunrise. */
    static void snooze(Context ctx, int unanswered, boolean test) {
        long at = System.currentTimeMillis() + SNOOZE_MS;
        AlarmPlan plan = new AlarmPlan(at, at);
        set(ctx, ctx.getSystemService(AlarmManager.class), SNOOZE, plan, ring(plan, unanswered, true, test));
    }

    /** A ring at {@code alarmAt} with no sunrise: the TV was on when the sunrise was due, or turned off during it. */
    static void ringAt(Context ctx, long alarmAt, boolean test) {
        AlarmPlan plan = new AlarmPlan(alarmAt, alarmAt);
        set(ctx, ctx.getSystemService(AlarmManager.class), SNOOZE, plan, ring(plan, 0, false, test));
    }

    static void cancelSnooze(Context ctx) {
        ctx.getSystemService(AlarmManager.class).cancel(fireIntent(ctx, SNOOZE, null));
    }

    /** A test run from AlarmTestReceiver: the sunrise in {@code inSeconds}, lasting {@code sunriseSeconds}. */
    static void scheduleTest(Context ctx, int inSeconds, int sunriseSeconds) {
        AlarmPlan plan = AlarmPlan.test(System.currentTimeMillis(), inSeconds, sunriseSeconds);
        set(ctx, ctx.getSystemService(AlarmManager.class), SNOOZE, plan, ring(plan, 0, false, true));
    }

    /** After a ring is stopped or given up: Repeat Never turns the alarm off. */
    static void finished(Context ctx, boolean test) {
        if (test) return;
        if (repeat(ctx) == AlarmPlan.NEVER) {
            prefs(ctx).edit().putBoolean(ON_KEY, false).apply();
            schedule(ctx);
        }
    }

    static Intent ring(AlarmPlan plan, int unanswered, boolean snoozed, boolean test) {
        return new Intent().putExtra(EXTRA_ALARM_AT, plan.alarmAt).putExtra(EXTRA_SUNRISE_AT, plan.sunriseAt)
                .putExtra(EXTRA_RINGS, unanswered).putExtra(EXTRA_SNOOZED, snoozed).putExtra(EXTRA_TEST, test);
    }

    private static void set(Context ctx, AlarmManager am, int slot, AlarmPlan plan, Intent extras) {
        // An alarm-clock alarm: exact, allowed in idle, and the system treats it as the user's alarm.
        PendingIntent show = PendingIntent.getActivity(ctx, slot, new Intent(ctx, AlarmSettingsActivity.class),
                PendingIntent.FLAG_IMMUTABLE);
        am.setAlarmClock(new AlarmManager.AlarmClockInfo(plan.sunriseAt, show), fireIntent(ctx, slot, extras));
    }

    private static PendingIntent fireIntent(Context ctx, int slot, Intent extras) {
        Intent i = new Intent(ctx, AlarmReceiver.class).setAction(AlarmReceiver.ACTION_SUNRISE);
        if (extras != null) i.putExtras(extras);
        return PendingIntent.getBroadcast(ctx, slot, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }
}
