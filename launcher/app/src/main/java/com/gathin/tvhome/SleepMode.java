package com.gathin.tvhome;

import android.animation.ValueAnimator;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

/**
 * Sleep mode (config sleep_mode): over whatever plays, the backlight goes to the panel's dimmest and an 80% black
 * layer covers the picture, while the show or music keeps playing. Held ≡ Menu starts it (SleepKey, in
 * HomeKeyService); the first button press only wakes the screen, volume keys pass through without waking it. One
 * overlay window owned by the remote helper; positions are TV px at 1920×1080 (.design-reviews/alarm-and-sleep.md).
 */
final class SleepMode {
    private static final float LAYER = 0.80f;
    private static final long IN_MS = 2000, OUT_MS = 300;
    /** Nothing playing and no key for this long: the TV goes to standby, before the screensaver lights the room. */
    private static final long IDLE_MS = 5 * 60_000L;
    private static final long CHECK_MS = 15_000;

    private static SleepMode current;
    private static long swallowDownTime = -1;

    private final Context ctx;
    private final WindowManager wm;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final FrameLayout root;
    private final View layer;
    private final View badge;
    private final WindowManager.LayoutParams lp;
    private final float startBrightness;
    private ValueAnimator anim;
    private long quietSince = -1;
    private final BroadcastReceiver screenOff = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            // The TV turned off (its own timer, the power button): it must wake normal, not dimmed.
            remove();
        }
    };
    private final Runnable watch = this::watchPlayback;

    private SleepMode(Context ctx) {
        this.ctx = ctx;
        wm = ctx.getSystemService(WindowManager.class);
        float density = ctx.getResources().getDisplayMetrics().density;
        root = new FrameLayout(ctx);
        layer = new View(ctx);
        layer.setBackgroundColor(0xFF000000);
        layer.setAlpha(0f);
        root.addView(layer, new FrameLayout.LayoutParams(-1, -1));
        badge = badge(ctx, density);
        root.addView(badge, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        lp = new WindowManager.LayoutParams(-1, -1, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.TRANSLUCENT);
        startBrightness = Settings.System.getInt(ctx.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, 128) / 255f;
        lp.screenBrightness = startBrightness;
    }

    static boolean isOn() {
        return current != null;
    }

    /** Dims the screen over 2 s with the brief "Sleep Mode" badge. */
    static void dim(Context ctx) {
        if (current != null) return;
        SleepMode s = new SleepMode(ctx);
        try {
            s.wm.addView(s.root, s.lp);
        } catch (RuntimeException e) {
            return;   // no "draw over other apps": glass-install.sh grants it
        }
        current = s;
        s.ctx.registerReceiver(s.screenOff, new IntentFilter(Intent.ACTION_SCREEN_OFF));
        s.fadeIn();
        s.main.postDelayed(s.watch, CHECK_MS);
    }

    /** Removes the dim at once (the alarm's sunrise, the TV turning off). */
    static void clear() {
        if (current != null) current.remove();
    }

    /**
     * A key while dimmed. Volume passes through and doesn't wake. Any other key wakes the screen and is consumed with
     * the rest of its press, so in the dark it never also pauses or skips the show. False passes the key on.
     */
    static boolean onKey(KeyEvent event) {
        if (event.getDownTime() == swallowDownTime) {
            if (event.getAction() == KeyEvent.ACTION_UP) swallowDownTime = -1;
            return true;
        }
        if (current == null) return false;
        int code = event.getKeyCode();
        if (code == KeyEvent.KEYCODE_VOLUME_UP || code == KeyEvent.KEYCODE_VOLUME_DOWN
                || code == KeyEvent.KEYCODE_VOLUME_MUTE) return false;
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            swallowDownTime = event.getDownTime();
            current.wake();
        }
        return true;
    }

    private void fadeIn() {
        animate(0f, LAYER, startBrightness, Sunrise.FLOOR, IN_MS, null);
        // The badge: in over 200 ms, held, out from 1.6 s to 2 s.
        badge.setAlpha(0f);
        badge.animate().alpha(1f).setDuration(200).withEndAction(() ->
                badge.animate().alpha(0f).setStartDelay(1400).setDuration(400).start()).start();
    }

    private void wake() {
        badge.animate().cancel();
        badge.setAlpha(0f);
        animate(layer.getAlpha(), 0f, lp.screenBrightness, startBrightness, OUT_MS, this::remove);
    }

    private void animate(float fromLayer, float toLayer, float fromLight, float toLight, long ms, Runnable done) {
        if (anim != null) anim.cancel();
        anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(ms);
        anim.setInterpolator(new DecelerateInterpolator());
        anim.addUpdateListener(a -> {
            float t = (float) a.getAnimatedValue();
            layer.setAlpha(fromLayer + (toLayer - fromLayer) * t);
            float light = fromLight + (toLight - fromLight) * t;
            if (Math.abs(light - lp.screenBrightness) > 0.004f || t == 1f) {
                lp.screenBrightness = light;
                if (root.isAttachedToWindow()) wm.updateViewLayout(root, lp);
            }
        });
        if (done != null) {
            anim.addListener(new android.animation.AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(android.animation.Animator animation) {
                    done.run();
                }
            });
        }
        anim.start();
    }

    /**
     * Keeps the screen on only while something plays (Netflix and HBO Max hold it themselves; music on the TV may
     * not). With nothing playing for five minutes, the TV goes to standby through the remote helper.
     */
    private void watchPlayback() {
        if (current != this) return;
        boolean playing = playing();
        boolean keep = (lp.flags & WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) != 0;
        if (playing != keep) {
            if (playing) lp.flags |= WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;
            else lp.flags &= ~WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;
            wm.updateViewLayout(root, lp);
        }
        long now = SystemClock.elapsedRealtime();
        if (playing) {
            quietSince = -1;
        } else if (quietSince < 0) {
            quietSince = now;
        } else if (now - quietSince >= IDLE_MS) {
            remove();
            HomeKeyService.lockScreen();
            return;
        }
        main.postDelayed(watch, CHECK_MS);
    }

    private boolean playing() {
        try {
            MediaSessionManager sessions = ctx.getSystemService(MediaSessionManager.class);
            List<MediaController> list = sessions.getActiveSessions(new ComponentName(ctx, NowPlayingListener.class));
            for (MediaController c : list) {
                PlaybackState s = c.getPlaybackState();
                if (s != null && s.getState() == PlaybackState.STATE_PLAYING) return true;
            }
        } catch (SecurityException e) {
            // No media access: assume it plays, and leave the screen to the app and the TV's timers.
            return true;
        }
        // An app that plays without a media session (or doesn't mark it PLAYING) still makes sound: never turn the
        // TV off in the middle of a show because of a missing session.
        return ctx.getSystemService(android.media.AudioManager.class).isMusicActive();
    }

    private void remove() {
        if (current != this) return;
        current = null;
        main.removeCallbacksAndMessages(null);
        if (anim != null) anim.cancel();
        try {
            ctx.unregisterReceiver(screenOff);
        } catch (IllegalArgumentException ignored) {
        }
        if (root.isAttachedToWindow()) wm.removeView(root);
    }

    /** Material Symbols "bedtime" at 96 px over "Sleep Mode", on a soft dark scrim so it reads over a bright frame. */
    private static View badge(Context ctx, float density) {
        int px48 = Math.round(48 * density);   // 96 TV px
        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        GradientDrawable scrim = new GradientDrawable();
        scrim.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        scrim.setGradientRadius(180 * density);
        scrim.setColors(new int[]{0x99000000, 0x00000000});
        box.setBackground(scrim);
        int size = Math.round(360 * density);
        box.setMinimumWidth(size);
        box.setMinimumHeight(size);
        ImageView icon = new ImageView(ctx);
        Drawable d = ctx.getDrawable(R.drawable.ic_bedtime).mutate();
        d.setTint(0xCCFFFFFF);
        icon.setImageDrawable(d);
        box.addView(icon, new LinearLayout.LayoutParams(px48, px48));
        TextView label = new TextView(ctx);
        label.setText(R.string.sleep_mode);
        label.setTypeface(ctx.getResources().getFont(R.font.inter_semibold));
        label.setTextSize(TypedValue.COMPLEX_UNIT_PX, 20 * density);
        label.setTextColor(0xCCFFFFFF);
        label.setIncludeFontPadding(false);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(-2, -2);
        llp.topMargin = Math.round(12 * density);
        box.addView(label, llp);
        box.setAlpha(0f);
        return box;
    }
}
