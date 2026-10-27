package com.gathin.tvhome;

import android.service.dreams.DreamService;
import android.view.KeyEvent;

/**
 * Screensaver: the launcher's thin clock and date on black, over a slow ambient glow (ClockScene).
 * No network, no images: cheap enough for a 1 GB TV.
 */
public class ClockDream extends DreamService {

    private ClockScene scene;
    /** standby_clock: the StandBy face instead of the drifting clock. */
    private StandbyScene standby;

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setFullscreen(true);
        Config cfg = Config.load(this);
        if (cfg.standbyClock) {
            // Interactive, so volume and the media keys can work without ending it (dispatchKeyEvent).
            setInteractive(true);
            setScreenBright(true);   // its own backlight: 0.50 by day, the dimmest at night
            standby = new StandbyScene(this, getWindow(), cfg.spotifyConnect, cfg.animations && !cfg.reducedMotion,
                    this::finish);
            setContentView(standby.view());
            return;
        }
        setInteractive(false);   // any key wakes the TV back to where it was
        setScreenBright(false);
        setContentView(R.layout.dream_clock);
        scene = new ClockScene(getWindow().getDecorView());
    }

    @Override
    public void onDreamingStarted() {
        super.onDreamingStarted();
        if (standby != null) standby.start();
        else scene.start();
    }

    @Override
    public void onDreamingStopped() {
        if (standby != null) standby.stop();
        else scene.stop();
        super.onDreamingStopped();
    }

    @Override
    public void onDetachedFromWindow() {
        if (scene != null) scene.stop();
        if (standby != null) standby.stop();
        super.onDetachedFromWindow();
    }

    /** The StandBy face's keys (StandbyKeys): volume and media keys keep it; any other key ends it. */
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (standby == null) return super.dispatchKeyEvent(event);
        switch (StandbyKeys.of(event)) {
            case PASS:
                return super.dispatchKeyEvent(event);
            case MEDIA:
                if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                    standby.mediaKey(event.getKeyCode());
                }
                return true;
            default:
                if (event.getAction() == KeyEvent.ACTION_UP) {
                    SleepMode.clear();   // one press brings the picture back from sleep mode too
                    StandbyFace.clearForcedNight();
                    finish();
                }
                return true;
        }
    }
}
