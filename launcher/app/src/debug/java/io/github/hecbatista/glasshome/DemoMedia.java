package io.github.hecbatista.glasshome;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.media.MediaMetadata;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.SystemClock;

/**
 * Debug builds only (src/debug): a pretend player for the emulator, which has no Spotify. It publishes a real media
 * session, so the Now Playing panel, its lyrics (LRCLIB, for a real song) and NowPlayingFeed show it exactly as they
 * would a song on the TV:
 *
 *     am broadcast -n io.github.hecbatista.glasshome/.DemoMedia --es title "Blinding Lights" --es artist "The Weeknd" \
 *         --el duration 200040 --el pos 60000 [--ez playing false] [--es art /data/local/tmp/cover.jpg]
 *
 * {@code --ez stop true} ends the session. Without {@code art} the cover is a gradient drawn from the title. The
 * panel's buttons pause, play and seek it. Protected by WRITE_SECURE_SETTINGS like AlarmTestReceiver, and never in
 * a release APK.
 */
public final class DemoMedia extends BroadcastReceiver {
    private static MediaSession session;
    private static PlaybackState state;

    @Override
    public void onReceive(Context ctx, Intent intent) {
        Context app = ctx.getApplicationContext();
        if (intent.getBooleanExtra("stop", false)) {
            if (session != null) session.release();
            session = null;
            setResultData("stopped");
            return;
        }
        if (session == null) {
            session = new MediaSession(app, "DemoMedia");
            session.setCallback(new MediaSession.Callback() {
                @Override public void onPlay() { update(true, position()); }
                @Override public void onPause() { update(false, position()); }
                @Override public void onSeekTo(long pos) { update(state.getState() == PlaybackState.STATE_PLAYING, pos); }
            });
        }
        String title = text(intent, "title", "Blinding Lights"), artist = text(intent, "artist", "The Weeknd");
        String art = intent.getStringExtra("art");
        Bitmap cover = art != null ? BitmapFactory.decodeFile(art) : null;
        session.setMetadata(new MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, title)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, artist)
                .putString(MediaMetadata.METADATA_KEY_ALBUM, text(intent, "album", ""))
                .putLong(MediaMetadata.METADATA_KEY_DURATION, intent.getLongExtra("duration", 200_040L))
                .putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, cover != null ? cover : gradient(title))
                .build());
        session.setActive(true);
        update(intent.getBooleanExtra("playing", true), intent.getLongExtra("pos", 0L));
        setResultData((state.getState() == PlaybackState.STATE_PLAYING ? "playing " : "paused ") + title);
    }

    private static void update(boolean playing, long pos) {
        state = new PlaybackState.Builder()
                .setActions(PlaybackState.ACTION_PLAY_PAUSE | PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE
                        | PlaybackState.ACTION_SEEK_TO | PlaybackState.ACTION_SKIP_TO_NEXT
                        | PlaybackState.ACTION_SKIP_TO_PREVIOUS)
                .setState(playing ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED, pos, playing ? 1f : 0f,
                        SystemClock.elapsedRealtime())
                .build();
        session.setPlaybackState(state);
    }

    /** Where the song is now, carried forward from the last state while it plays. */
    private static long position() {
        if (state == null) return 0;
        long since = SystemClock.elapsedRealtime() - state.getLastPositionUpdateTime();
        return state.getPosition() + (long) (since * state.getPlaybackSpeed());
    }

    private static String text(Intent intent, String key, String fallback) {
        String s = intent.getStringExtra(key);
        return s == null ? fallback : s;
    }

    /** A 600 px square cover in two colours picked from the title, so different songs look different. */
    private static Bitmap gradient(String title) {
        float hue = (title.hashCode() & 0xffff) % 360;
        int a = android.graphics.Color.HSVToColor(new float[]{hue, 0.7f, 0.9f});
        int b = android.graphics.Color.HSVToColor(new float[]{(hue + 60) % 360, 0.8f, 0.4f});
        Bitmap bmp = Bitmap.createBitmap(600, 600, Bitmap.Config.ARGB_8888);
        Paint p = new Paint();
        p.setShader(new LinearGradient(0, 0, 600, 600, a, b, Shader.TileMode.CLAMP));
        new Canvas(bmp).drawRect(0, 0, 600, 600, p);
        return bmp;
    }
}
