package io.github.hecbatista.glasshome;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * A test alarm over ADB, so the sunrise can be checked without waiting for morning:
 *
 *     am broadcast -n io.github.hecbatista.glasshome/.AlarmTestReceiver --ei in 30 --ei sunrise 60
 *
 * rings 30 s from now after a 60 s sunrise; {@code --ez sleep true} dims the screen as held Menu does. Protected by WRITE_SECURE_SETTINGS, which only the shell and the system
 * hold, like SpotifyLink.
 */
public final class AlarmTestReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        if (intent.getBooleanExtra("sleep", false)) {
            // Sleep mode's dimming without holding the remote's Menu button (ADB can't hold a key for 800 ms).
            SleepMode.dim(ctx.getApplicationContext());
            return;
        }
        int in = Math.max(5, Math.min(3600, intent.getIntExtra("in", 30)));
        int sunrise = Math.max(0, Math.min(3600, intent.getIntExtra("sunrise", 60)));
        AlarmScheduler.scheduleTest(ctx, in, sunrise);
    }
}
