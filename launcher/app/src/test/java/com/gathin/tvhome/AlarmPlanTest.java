package com.gathin.tvhome;

import org.junit.Test;

import java.util.Calendar;
import java.util.TimeZone;

import static org.junit.Assert.assertEquals;

public class AlarmPlanTest {
    private static final TimeZone NY = TimeZone.getTimeZone("America/New_York");

    private static long at(int year, int month, int day, int hour, int minute) {
        Calendar c = Calendar.getInstance(NY);
        c.clear();
        c.set(year, month - 1, day, hour, minute, 0);
        return c.getTimeInMillis();
    }

    @Test public void anEveningAlarmSetForMorningRingsTomorrow() {
        AlarmPlan p = AlarmPlan.next(at(2026, 10, 25, 23, 10), 7 * 60, 20, AlarmPlan.EVERY_DAY, NY);
        assertEquals(at(2026, 10, 26, 7, 0), p.alarmAt);
        assertEquals(at(2026, 10, 26, 6, 40), p.sunriseAt);
    }

    @Test public void anAlarmLaterTodayRingsToday() {
        AlarmPlan p = AlarmPlan.next(at(2026, 10, 25, 5, 0), 7 * 60, 30, AlarmPlan.EVERY_DAY, NY);
        assertEquals(at(2026, 10, 25, 7, 0), p.alarmAt);
        assertEquals(at(2026, 10, 25, 6, 30), p.sunriseAt);
    }

    @Test public void theCurrentMinuteMeansTomorrow() {
        AlarmPlan p = AlarmPlan.next(at(2026, 10, 25, 7, 0) + 20_000, 7 * 60, 20, AlarmPlan.EVERY_DAY, NY);
        assertEquals(at(2026, 10, 26, 7, 0), p.alarmAt);
    }

    @Test public void aLateAlarmStartsItsSunriseNow() {
        long now = at(2026, 10, 25, 6, 50);
        AlarmPlan p = AlarmPlan.next(now, 7 * 60, 20, AlarmPlan.EVERY_DAY, NY);
        assertEquals(at(2026, 10, 25, 7, 0), p.alarmAt);
        assertEquals(now, p.sunriseAt);
    }

    @Test public void theClocksChangingKeepsTheWallTime() {
        // US clocks fall back on 1 November 2026: 7:00 the next morning is still 7:00 on the wall.
        AlarmPlan p = AlarmPlan.next(at(2026, 10, 31, 22, 0), 7 * 60, 20, AlarmPlan.EVERY_DAY, NY);
        assertEquals(at(2026, 11, 1, 7, 0), p.alarmAt);
    }

    @Test public void progressRunsFromSunriseToAlarm() {
        AlarmPlan p = AlarmPlan.test(1_000_000L, 0, 100);
        assertEquals(0f, p.progress(1_000_000L), 0f);
        assertEquals(0.5f, p.progress(1_050_000L), 0.0001f);
        assertEquals(1f, p.progress(1_200_000L), 0f);
        assertEquals(0f, p.progress(900_000L), 0f);
    }

    @Test public void weekdaysSkipTheWeekend() {
        // Friday 23 October 2026 at 23:00: the next weekday alarm is Monday the 26th.
        AlarmPlan p = AlarmPlan.next(at(2026, 10, 23, 23, 0), 7 * 60, 20, AlarmPlan.WEEKDAYS, NY);
        assertEquals(at(2026, 10, 26, 7, 0), p.alarmAt);
    }

    @Test public void weekendsSkipTheWeek() {
        // Monday 26 October 2026: the next weekend alarm is Saturday the 31st.
        AlarmPlan p = AlarmPlan.next(at(2026, 10, 26, 8, 0), 9 * 60, 0, AlarmPlan.WEEKENDS, NY);
        assertEquals(at(2026, 10, 31, 9, 0), p.alarmAt);
        assertEquals(p.alarmAt, p.sunriseAt);   // sunrise off
    }

    @Test public void neverRingsOnTheNextMatchingTime() {
        AlarmPlan p = AlarmPlan.next(at(2026, 10, 24, 23, 0), 7 * 60, 20, AlarmPlan.NEVER, NY);
        assertEquals(at(2026, 10, 25, 7, 0), p.alarmAt);
    }
}
