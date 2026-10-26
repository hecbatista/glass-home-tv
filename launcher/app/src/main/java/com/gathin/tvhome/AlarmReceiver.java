package com.gathin.tvhome;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;

/**
 * An alarm's sunrise begins, or a snooze ends (AlarmScheduler): wake the screen from standby and show the alarm
 * screen, then set the next daily alarm. Not exported: only AlarmManager's PendingIntents reach it.
 */
public final class AlarmReceiver extends BroadcastReceiver {
    static final String ACTION_SUNRISE = "com.gathin.tvhome.action.ALARM_SUNRISE";

    @Override
    public void onReceive(Context ctx, Intent intent) {
        if (!ACTION_SUNRISE.equals(intent.getAction())) return;
        long alarmAt = intent.getLongExtra(AlarmScheduler.EXTRA_ALARM_AT, 0);
        if (alarmAt == 0) return;
        PowerManager pm = ctx.getSystemService(PowerManager.class);
        // Was the TV on already (and not dimmed by sleep mode)? Then someone is up: no sunrise, it rings at the alarm
        // time over whatever plays.
        // The night screensaver counts as off too: the sunrise replaces it.
        boolean wasOn = pm.isInteractive() && !SleepMode.isOn() && !StandbyScene.showingNight();
        boolean daily = !intent.getBooleanExtra(AlarmScheduler.EXTRA_SNOOZED, false)
                && !intent.getBooleanExtra(AlarmScheduler.EXTRA_TEST, false);
        long sunriseAt = intent.getLongExtra(AlarmScheduler.EXTRA_SUNRISE_AT, alarmAt);
        if (daily) AlarmScheduler.scheduleAfter(ctx, alarmAt);
        if (wasOn && alarmAt - System.currentTimeMillis() > 60_000L && sunriseAt < alarmAt) {
            // Someone is watching: no sunrise over their show. It rings at the alarm time instead.
            AlarmScheduler.ringAt(ctx, alarmAt, intent.getBooleanExtra(AlarmScheduler.EXTRA_TEST, false));
            return;
        }
        // Turns the panel on from standby; the alarm screen keeps it on from here (its window flags).
        @SuppressWarnings("deprecation")
        PowerManager.WakeLock wake = pm.newWakeLock(PowerManager.FULL_WAKE_LOCK
                | PowerManager.ACQUIRE_CAUSES_WAKEUP | PowerManager.ON_AFTER_RELEASE, "tvhome:alarm");
        if (!wasOn) wake.acquire(10_000);
        SleepMode.clear();   // a dimmed TV gets the sunrise as intended
        StandbyScene.endCurrent();   // and the screensaver ends before the alarm's screen
        ctx.startActivity(new Intent(ctx, AlarmActivity.class).putExtras(intent)
                .putExtra(AlarmActivity.EXTRA_WAS_ON, wasOn)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }
}
