package io.github.hecbatista.glasshome;

import android.view.KeyEvent;

/**
 * Keys on the StandBy screensaver: volume passes through and leaves it showing (turning music down at night
 * shouldn't light the room), play/pause, next and previous act on the song without ending it, and any other key ends
 * it. Pure, tested on the JVM.
 */
final class StandbyKeys {
    enum Kind { PASS, MEDIA, END }

    private StandbyKeys() {}

    static Kind of(KeyEvent event) {
        return of(event.getKeyCode());
    }

    static Kind of(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_VOLUME_UP:
            case KeyEvent.KEYCODE_VOLUME_DOWN:
            case KeyEvent.KEYCODE_VOLUME_MUTE:
                return Kind.PASS;
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
            case KeyEvent.KEYCODE_MEDIA_PLAY:
            case KeyEvent.KEYCODE_MEDIA_PAUSE:
            case KeyEvent.KEYCODE_MEDIA_NEXT:
            case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
            case KeyEvent.KEYCODE_MEDIA_REWIND:
                return Kind.MEDIA;
            default:
                return Kind.END;
        }
    }
}
