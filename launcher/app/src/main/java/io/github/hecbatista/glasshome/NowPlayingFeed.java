package io.github.hecbatista.glasshome;

import android.content.ComponentName;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextUtils;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * What's playing, for screens other than Home (the StandBy screensaver): the TV's media sessions and, with
 * spotify_connect, the Spotify account on other devices, chosen by the same rules as the Now Playing panel
 * (NowPlayingChoice). It hands a {@link Track} to its listener whenever the song, its cover or its play state
 * changes, and null when nothing is playing or paused.
 */
final class NowPlayingFeed {
    /** One song as the screensaver shows it. */
    static final class Track {
        final String title, artist;
        final Bitmap cover;
        final boolean playing;
        /** SpotifyPlayback.sourceIcon's name: "tv" for a session on this TV. */
        final String source;
        /** For the lyrics: the Now Playing panel's key for this song (LrcLib.CACHE), its album and length (0: unknown). */
        final String lyricsKey, album;
        final long durationMs;

        Track(String title, String artist, Bitmap cover, boolean playing, String source) {
            this(title, artist, cover, playing, source, null, "", 0);
        }

        Track(String title, String artist, Bitmap cover, boolean playing, String source, String lyricsKey,
              String album, long durationMs) {
            this.title = title;
            this.artist = artist;
            this.cover = cover;
            this.playing = playing;
            this.source = source;
            this.lyricsKey = lyricsKey;
            this.album = album == null ? "" : album;
            this.durationMs = durationMs;
        }

        boolean sameAs(Track o) {
            return o != null && Objects.equals(title, o.title) && Objects.equals(artist, o.artist)
                    && cover == o.cover && playing == o.playing && Objects.equals(source, o.source);
        }
    }

    interface Listener {
        void onTrack(Track track);
    }

    private static final long POLL_PLAYING_MS = 5_000, POLL_IDLE_MS = 15_000, POLL_BACKOFF_MS = 30_000;
    private static final Object REMOTE_KEY = "spotify-account";

    private final Context ctx;
    private final Listener listener;
    private final boolean spotifyConnect;
    private final Handler main = new Handler(Looper.getMainLooper());
    private MediaSessionManager sessions;
    private final Map<MediaSession.Token, MediaController> followed = new HashMap<>();
    private List<MediaController> active = new ArrayList<>();
    private MediaController controller;
    private SpotifyApi spotify;
    private HandlerThread pollThread;
    private Handler poller;
    private SpotifyPlayback remote;
    private boolean remoteShown;
    private String remoteArtUrl;
    private Bitmap remoteArt;
    private Track last;
    private boolean started;
    /** Where the song shown is now: the session's last position, or Spotify's as of its last answer. */
    private final PlaybackClock clock = new PlaybackClock();
    /** When the last Spotify answer arrived (elapsedRealtime). */
    private long remoteAt;

    private final MediaSessionManager.OnActiveSessionsChangedListener changed = this::pick;
    private final MediaController.Callback callback = new MediaController.Callback() {
        @Override public void onMetadataChanged(MediaMetadata metadata) { pick(active); }
        @Override public void onPlaybackStateChanged(PlaybackState state) { pick(active); }
        @Override public void onSessionDestroyed() { pick(activeSessions()); }
    };
    private final Runnable poll = this::pollOnce;

    NowPlayingFeed(Context ctx, boolean spotifyConnect, Listener listener) {
        this.ctx = ctx;
        this.spotifyConnect = spotifyConnect;
        this.listener = listener;
    }

    void start() {
        if (started) return;
        started = true;
        sessions = ctx.getSystemService(MediaSessionManager.class);
        try {
            ComponentName me = new ComponentName(ctx, NowPlayingListener.class);
            sessions.addOnActiveSessionsChangedListener(changed, me, main);
            pick(sessions.getActiveSessions(me));
        } catch (SecurityException e) {
            sessions = null;   // no media access: Spotify alone, if linked
        }
        spotify = spotifyConnect ? SpotifyLink.api(ctx) : null;
        if (spotify != null) {
            pollThread = new HandlerThread("standby-now-playing");
            pollThread.start();
            poller = new Handler(pollThread.getLooper());
            poller.post(poll);
        }
    }

    void stop() {
        if (!started) return;
        started = false;
        if (sessions != null) sessions.removeOnActiveSessionsChangedListener(changed);
        for (MediaController c : followed.values()) c.unregisterCallback(callback);
        followed.clear();
        if (pollThread != null) {
            poller.removeCallbacksAndMessages(null);
            pollThread.quitSafely();
            pollThread = null;
            poller = null;
        }
        main.removeCallbacksAndMessages(null);
    }

    /** The song shown's position now, in ms, from its player's last report. */
    long positionAt(long elapsedRealtime) {
        return clock.positionAt(elapsedRealtime);
    }

    /**
     * Play/pause, next or previous from the remote, on the song shown: the session's own controls for this TV, the
     * Spotify account's for another device. False for other keys.
     */
    boolean control(int keyCode) {
        SpotifyApi.Action action;
        switch (keyCode) {
            case android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
            case android.view.KeyEvent.KEYCODE_MEDIA_PLAY:
            case android.view.KeyEvent.KEYCODE_MEDIA_PAUSE:
                boolean playing = last != null && last.playing;
                action = playing ? SpotifyApi.Action.PAUSE : SpotifyApi.Action.PLAY;
                break;
            case android.view.KeyEvent.KEYCODE_MEDIA_NEXT:
            case android.view.KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                action = SpotifyApi.Action.NEXT;
                break;
            case android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS:
            case android.view.KeyEvent.KEYCODE_MEDIA_REWIND:
                action = SpotifyApi.Action.PREVIOUS;
                break;
            default:
                return false;
        }
        if (remoteShown && remote != null && spotify != null && poller != null) {
            SpotifyApi api = spotify;
            String device = remote.deviceId;
            poller.post(() -> {
                api.control(action, device);
                poller.removeCallbacks(poll);
                poller.postDelayed(poll, 700);
            });
        } else if (controller != null) {
            MediaController.TransportControls c = controller.getTransportControls();
            if (action == SpotifyApi.Action.PLAY) c.play();
            else if (action == SpotifyApi.Action.PAUSE) c.pause();
            else if (action == SpotifyApi.Action.NEXT) c.skipToNext();
            else c.skipToPrevious();
        }
        return true;
    }

    private List<MediaController> activeSessions() {
        try {
            return sessions == null ? null : sessions.getActiveSessions(new ComponentName(ctx, NowPlayingListener.class));
        } catch (SecurityException e) {
            return null;
        }
    }

    private void pick(List<MediaController> list) {
        if (!started) return;
        active = list == null ? new ArrayList<>() : new ArrayList<>(list);
        Map<MediaSession.Token, MediaController> byToken = new HashMap<>();
        for (MediaController c : active) byToken.put(c.getSessionToken(), c);
        for (MediaSession.Token t : NowPlayingChoice.toDrop(followed.keySet(), byToken.keySet())) {
            followed.remove(t).unregisterCallback(callback);
        }
        for (MediaSession.Token t : NowPlayingChoice.toFollow(followed.keySet(), byToken.keySet())) {
            MediaController c = byToken.get(t);
            c.registerCallback(callback, main);
            followed.put(t, c);
        }
        List<NowPlayingChoice.Session> list2 = new ArrayList<>();
        for (MediaController c : active) list2.add(new NowPlayingChoice.Session(c.getSessionToken(), stateOf(c), titleOf(c) != null));
        NowPlayingChoice.Session account = remote == null ? null : new NowPlayingChoice.Session(REMOTE_KEY,
                remote.playing ? NowPlayingChoice.PLAYING : NowPlayingChoice.PAUSED, true);
        int at = NowPlayingChoice.chooseWithRemote(list2, controller == null ? null : controller.getSessionToken(),
                account, remoteShown);
        remoteShown = at == NowPlayingChoice.REMOTE;
        controller = at < 0 ? null : active.get(at);
        publish();
    }

    private void publish() {
        Track t = null;
        if (remoteShown && remote != null) {
            t = new Track(remote.title, remote.artist.isEmpty() ? remote.device : remote.artist,
                    remote.artUrl != null && remote.artUrl.equals(remoteArtUrl) ? remoteArt : null, remote.playing,
                    remote.sourceIcon(), "spotify\n" + remote.itemId + "\n" + remote.title, remote.album,
                    remote.durationMs);
            clock.set(remote.progressMs, remoteAt, remote.playing ? 1f : 0f);
        } else if (controller != null && NowPlayingChoice.visible(new NowPlayingChoice.Session(
                controller.getSessionToken(), stateOf(controller), titleOf(controller) != null))) {
            MediaMetadata m = controller.getMetadata();
            CharSequence by = m.getText(MediaMetadata.METADATA_KEY_ARTIST);
            if (TextUtils.isEmpty(by)) by = m.getText(MediaMetadata.METADATA_KEY_ALBUM_ARTIST);
            if (TextUtils.isEmpty(by) && m.getDescription() != null) by = m.getDescription().getSubtitle();
            int state = stateOf(controller);
            // The panel's lyrics key: the package, the title and the artist line, as it builds them.
            String song = titleOf(controller);
            t = new Track(song, by == null ? "" : by.toString(), coverOf(m),
                    state == PlaybackState.STATE_PLAYING || state == PlaybackState.STATE_BUFFERING, "tv",
                    controller.getPackageName() + "\n" + song + "\n" + by, m.getString(MediaMetadata.METADATA_KEY_ALBUM),
                    m.getLong(MediaMetadata.METADATA_KEY_DURATION));
            PlaybackState ps = controller.getPlaybackState();
            if (ps != null) {
                clock.set(ps.getPosition(), ps.getLastPositionUpdateTime(),
                        state == PlaybackState.STATE_PLAYING ? ps.getPlaybackSpeed() : 0f);
            }
        }
        if (t == null ? last == null : t.sameAs(last)) return;
        last = t;
        listener.onTrack(t);
    }

    private void pollOnce() {
        SpotifyApi api = spotify;
        Handler h = poller;
        if (api == null || h == null) return;
        SpotifyPlayback p = api.playback();
        String url = p == null ? null : p.artUrl;
        Bitmap cover = url != null && !url.equals(remoteArtUrl) ? download(url) : null;
        main.post(() -> {
            if (!started) return;
            remote = p;
            remoteAt = SystemClock.elapsedRealtime();
            if (url != null && !url.equals(remoteArtUrl)) {
                remoteArtUrl = url;
                remoteArt = cover;
            }
            pick(active);
        });
        h.postDelayed(poll, api.status() == SpotifyApi.Status.RATE_LIMITED ? POLL_BACKOFF_MS
                : p != null && p.playing ? POLL_PLAYING_MS : POLL_IDLE_MS);
    }

    private static int stateOf(MediaController c) {
        PlaybackState s = c.getPlaybackState();
        return s == null ? PlaybackState.STATE_NONE : s.getState();
    }

    private static String titleOf(MediaController c) {
        MediaMetadata m = c.getMetadata();
        if (m == null) return null;
        CharSequence t = m.getText(MediaMetadata.METADATA_KEY_TITLE);
        if (TextUtils.isEmpty(t) && m.getDescription() != null) t = m.getDescription().getTitle();
        return TextUtils.isEmpty(t) ? null : t.toString();
    }

    private static Bitmap coverOf(MediaMetadata m) {
        Bitmap bmp = m.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
        if (bmp == null) bmp = m.getBitmap(MediaMetadata.METADATA_KEY_ART);
        if (bmp == null) bmp = m.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON);
        if (bmp == null && m.getDescription() != null) bmp = m.getDescription().getIconBitmap();
        return bmp;
    }

    private static Bitmap download(String url) {
        if (!url.startsWith("https://")) return null;
        java.net.HttpURLConnection c = null;
        try {
            c = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
            c.setConnectTimeout(6000);
            c.setReadTimeout(8000);
            if (c.getResponseCode() != 200) return null;
            try (InputStream in = c.getInputStream()) {
                return BitmapFactory.decodeStream(in);
            }
        } catch (Exception e) {
            return null;
        } finally {
            if (c != null) c.disconnect();
        }
    }
}
