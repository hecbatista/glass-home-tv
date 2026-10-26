package com.gathin.tvhome;

import android.app.Activity;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;

/**
 * The clock screensaver shown by Home itself after home_idle_clock_minutes without input. Some TVs' own
 * screens (CVTE's live TV) do not keep the screen on, so the system screensaver would cut into programmes;
 * this one only ever starts from Home. Any key returns to Home.
 */
public class ClockActivity extends Activity {

    private ClockScene scene;
    /** standby_clock: the StandBy face, as the system screensaver shows it. */
    private StandbyScene standby;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Config cfg = Config.load(this);
        if (cfg.standbyClock) {
            standby = new StandbyScene(this, getWindow(), cfg.spotifyConnect, cfg.animations && !cfg.reducedMotion,
                    this::finish);
            setContentView(standby.view());
        } else {
            setContentView(R.layout.dream_clock);
            scene = new ClockScene(getWindow().getDecorView());
        }
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (standby != null) standby.start();
        else scene.start();
    }

    @Override
    protected void onPause() {
        if (standby != null) standby.stop();
        else scene.stop();
        super.onPause();
        finish();   // never left behind in the back stack
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (standby != null) {
            StandbyKeys.Kind kind = StandbyKeys.of(event);
            if (kind == StandbyKeys.Kind.PASS) return super.dispatchKeyEvent(event);
            if (kind == StandbyKeys.Kind.MEDIA) {
                if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                    standby.mediaKey(event.getKeyCode());
                }
                return true;
            }
        }
        // The key that wakes the clock should not also act on Home, so it is consumed here.
        if (event.getAction() == KeyEvent.ACTION_UP) finish();
        return true;
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent event) {
        finish();
        return true;
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }
}
