package com.gathin.tvhome;

import java.util.Calendar;
import java.util.TimeZone;

/**
 * When Glass Home's alarm (config alarm_clock) fires: the next time the clock reads the chosen hour and minute on a
 * day the Repeat choice allows, and the sunrise that starts that many minutes before it. Pure maths on epoch
 * milliseconds and a time zone, so it is tested on the JVM; AlarmScheduler hands the result to AlarmManager.
 */
final class AlarmPlan {
    /** Repeat choices, in Launcher settings' order. NEVER rings on the next matching time, then turns itself off. */
    static final int EVERY_DAY = 0, WEEKDAYS = 1, WEEKENDS = 2, NEVER = 3;

    /** The alarm itself (the tones start), and when the screen wakes for the sunrise; both epoch ms. */
    final long alarmAt, sunriseAt;

    AlarmPlan(long alarmAt, long sunriseAt) {
        this.alarmAt = alarmAt;
        this.sunriseAt = sunriseAt;
    }

    /**
     * The next alarm at {@code minuteOfDay} (0–1439) after {@code now} on a day {@code repeat} allows, with a sunrise
     * of {@code sunriseMinutes} (0 for none). An alarm less than a minute away counts as the next day's, so setting the
     * time you are looking at doesn't ring at once. If the sunrise would have started already (the alarm was set
     * late), it starts now.
     */
    static AlarmPlan next(long now, int minuteOfDay, int sunriseMinutes, int repeat, TimeZone zone) {
        Calendar c = Calendar.getInstance(zone);
        c.setTimeInMillis(now);
        c.set(Calendar.HOUR_OF_DAY, minuteOfDay / 60);
        c.set(Calendar.MINUTE, minuteOfDay % 60);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        if (c.getTimeInMillis() < now + 60_000L) c.add(Calendar.DAY_OF_MONTH, 1);
        for (int i = 0; i < 7 && !allows(repeat, c.get(Calendar.DAY_OF_WEEK)); i++) c.add(Calendar.DAY_OF_MONTH, 1);
        long alarm = c.getTimeInMillis();
        return new AlarmPlan(alarm, Math.max(now, alarm - sunriseMinutes * 60_000L));
    }

    static boolean allows(int repeat, int dayOfWeek) {
        boolean weekend = dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY;
        if (repeat == WEEKDAYS) return !weekend;
        if (repeat == WEEKENDS) return weekend;
        return true;
    }

    /** A test run: the sunrise starts {@code inSeconds} from now and lasts {@code sunriseSeconds}. */
    static AlarmPlan test(long now, int inSeconds, int sunriseSeconds) {
        long sunrise = now + inSeconds * 1000L;
        return new AlarmPlan(sunrise + sunriseSeconds * 1000L, sunrise);
    }

    /** How far through the sunrise {@code now} is: 0 at its start, 1 at the alarm (and with no sunrise). */
    float progress(long now) {
        if (alarmAt <= sunriseAt) return 1f;
        return Math.max(0f, Math.min(1f, (now - sunriseAt) / (float) (alarmAt - sunriseAt)));
    }
}
