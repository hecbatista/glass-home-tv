package com.gathin.tvhome;

import android.app.Activity;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;

/**
 * The clock screensaver shown by Home itself after home_idle_clock_minutes without input. Some TVs' own
 * screens (CVTE's live TV) do not keep the screen on, so the system screensaver would cut into programmes;
 * this one only ever starts from Home. Any key returns to Home. Glass Home's Night Clock (Home's clock pressed)
 * opens it too, with the night face forced (StandbyFace.forceNight).
 */
public class ClockActivity extends Activity {

    private ClockScene scene;
    /** standby_clock: the StandBy face, as the system screensaver shows it. */
    private StandbyScene standby;
    private boolean standbyStarted;
    /** A key went down on this screen: only its release ends it, never the release of the OK that opened it. */
    private boolean keyDown;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Config cfg = Config.load(this);
        if (cfg.standbyClock) {
            standby = new StandbyScene(this, getWindow(), cfg.spotifyConnect, cfg.animations && !cfg.reducedMotion,
                    StandbyLyric.enabled(this, cfg), this::finish);
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
        if (standby == null) {
            scene.start();
            return;
        }
        // The backlight changes only once Home has faded to black (onEnterAnimationComplete): during the fade a step
        // in brightness would read as a jump. The fallback covers firmware that never reports the animation's end.
        getWindow().getDecorView().postDelayed(this::startStandby, 1000);
    }

    @Override
    public void onEnterAnimationComplete() {
        super.onEnterAnimationComplete();
        if (standby != null) startStandby();
    }

    private void startStandby() {
        if (standbyStarted || isFinishing()) return;
        standbyStarted = true;
        standby.start();
    }

    @Override
    protected void onPause() {
        if (standby != null) {
            if (standbyStarted) standby.stop();
        } else {
            scene.stop();
        }
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
        if (event.getAction() == KeyEvent.ACTION_DOWN) keyDown = true;
        else if (event.getAction() == KeyEvent.ACTION_UP && keyDown) {
            StandbyFace.clearForcedNight();   // Night Clock ends with the key that ends it
            finish();
        }
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
