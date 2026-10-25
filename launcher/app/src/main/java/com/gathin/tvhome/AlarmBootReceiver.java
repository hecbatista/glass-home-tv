package com.gathin.tvhome;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Sets the alarm again after what clears or moves it: a reboot, an update of Glass Home, a clock or time zone change.
 * Only reschedules from the saved choices, so another app sending it can't make the TV ring.
 */
public final class AlarmBootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        AlarmScheduler.schedule(ctx);
    }
}
