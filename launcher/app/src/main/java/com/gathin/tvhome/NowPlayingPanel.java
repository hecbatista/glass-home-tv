package com.gathin.tvhome;

import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Outline;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Glass Home's Now Playing panel (config now_playing), Control Center style: the cover, the song and its artist from
 * whichever app is playing, with previous, play/pause and next. It reads the media sessions through
 * NowPlayingListener's access and shows only while a session is playing or paused; without the access it stays hidden.
 * With spotify_connect and a Spotify link it also asks the Web API what the account plays on other devices.
 */
final class NowPlayingPanel {
    /** Where focus goes from the panel's buttons: up to the top bar, down to the dock. */
    interface Exits {
        void up();
        void down();
    }

    private final Context ctx;
    private final float density;
    private final boolean light;
    private final Exits exits;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final FrameLayout root;
    private final LinearLayout panel;
    /** The top bar's buttons: the panel sits halfway between their bottom edge and the dock band. */
    private View above;
    private final ImageView art;
    private final TextView title, artist;
    private final ImageButton prev, playPause, next;
    /** The bottom-right corner: an icon for where the music plays, its glyph this far from both edges. */
    private final ImageView source;
    private static final int SOURCE_ICON_DP = 20, BUTTON_DP = 42;
    private String sourceShown;
    private Boolean playIconShown;
    private final int artPx;
    private MediaSessionManager sessions;
    private MediaController controller;
    /** The active sessions, and the ones this panel follows (all of them; see NowPlayingChoice). */
    private List<MediaController> active = new ArrayList<>();
    private final Map<MediaSession.Token, MediaController> followed = new HashMap<>();
    private boolean started, playing;
    /** placeAbove has run: until then the panel's place is unknown, and a song waits to show. */
    private boolean placed;
    /** Focus is in the rows under the dock, which scroll up into the panel's place: it steps aside meanwhile. */
    private boolean suppressed;
    /** The dock tiles' top edge the panel was last placed above, so a change in its own height keeps the gap. */
    private float dockTop;
    /** How far the dock band reaches above the tiles: 15dp, or the even padding's 22dp (setBandReach). */
    private float bandReach;
    /** The song whose cover is shown, and the cover bitmap the app sent for it (the app may send it a moment later). */
    private String artKey;
    private Bitmap artSource;

    /** spotify_connect: the account's playback on any device, polled off the main thread while Home is in front. */
    private final boolean spotifyConnect;
    private SpotifyApi spotify;
    private android.os.HandlerThread pollThread;
    private Handler poller;
    /** The last answer from Spotify (main thread), whether the panel shows it, and its cover (by URL). */
    private SpotifyPlayback remote;
    private boolean remoteShown;
    private Bitmap remoteArt;
    private String remoteArtUrl;
    /** Poll pace: every few seconds while something plays, slower while nothing does, longer when Spotify asks. */
    private static final long POLL_PLAYING_MS = 5_000, POLL_IDLE_MS = 15_000, POLL_BACKOFF_MS = 30_000;
    private static final Object REMOTE_KEY = "spotify-account";
    private final Runnable poll = this::pollOnce;
    /** When the last Spotify answer arrived (elapsedRealtime), to carry its position forward. */
    private long remoteAt;

    // ---- lyrics: the card's state ----
    private final boolean lyricsEnabled;
    private final LyricsCard lyricsCard;
    private final View divider;
    private final LrcLib lrclib = new LrcLib();
    private android.os.HandlerThread lyricsThread;
    private Handler lyricsLookup;
    /** The song the card is for, its lyrics once found (null while looking, or when there are none). */
    private String lyricsKey;
    private Lyrics lyrics;
    /** The lookup for the song now showing has finished, so no lyrics means it has none. */
    private boolean lyricsKnown;
    /** Lookups already made, by song: synced lyrics, or none (so a song without them is not asked again). */
    private final Map<String, LrcLib.Result> lyricsCache = new java.util.LinkedHashMap<String, LrcLib.Result>(32, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, LrcLib.Result> e) { return size() > 40; }
    };
    private final PlaybackClock clock = new PlaybackClock();
    /** The full-screen lyrics (LyricsScreen), opened with OK on the cover; null without lyrics. */
    private final LyricsScreen screen;
    /** The cover at its own size, for the full-screen view (the panel keeps a small copy). */
    private Bitmap fullArt;
    private static final long LYRICS_TICK_MS = 200;
    private final Runnable lyricsTick = new Runnable() {
        @Override public void run() {
            if (lyrics != null && panel.getVisibility() == View.VISIBLE) {
                lyricsCard.show(lyrics.indexAt(clock.positionAt(SystemClock.elapsedRealtime())));
            }
            if (lyrics != null && screen != null && screen.isOpen()) {
                screen.show(lyrics.indexAt(clock.positionAt(SystemClock.elapsedRealtime())));
            }
            main.postDelayed(this, LYRICS_TICK_MS);
        }
    };

    private final MediaSessionManager.OnActiveSessionsChangedListener sessionsChanged = list -> pick(list);

    /** How often a long title scrolls; the marquee only moves when the title does not fit. */
    private static final long MARQUEE_EVERY_MS = 12_000;
    private final Runnable marquee = new Runnable() {
        @Override public void run() {
            title.setSelected(false);
            title.setSelected(true);
            main.postDelayed(this, MARQUEE_EVERY_MS);
        }
    };

    private final MediaController.Callback callback = new MediaController.Callback() {
        // Registered on every active session: a change in any of them can change which one the panel shows.
        @Override public void onMetadataChanged(MediaMetadata metadata) { pick(active); }
        @Override public void onPlaybackStateChanged(PlaybackState state) { pick(active); }
        @Override public void onSessionDestroyed() { pick(activeSessions()); }
    };

    NowPlayingPanel(Context ctx, FrameLayout root, float density, boolean light, boolean spotifyConnect,
                    boolean lyricsEnabled, boolean animations, Exits exits) {
        this.ctx = ctx;
        this.lyricsEnabled = lyricsEnabled;
        this.spotifyConnect = spotifyConnect;
        this.density = density;
        this.light = light;
        this.exits = exits;
        this.root = root;
        bandReach = dp(15);
        int ink = Appearance.ink(light);
        artPx = dp(72);

        // The glass panel: the lyrics card and a hairline on top (only while the song has synced lyrics), the song row
        // under them. The panel's bottom stays on the dock, so the card grows it upward.
        panel = new LinearLayout(ctx);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackground(Appearance.glassPanel(panel, dp(26), density, light));
        panel.setVisibility(View.GONE);
        panel.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        lyricsCard = new LyricsCard(ctx, density, light, animations);
        lyricsCard.view().setVisibility(View.GONE);
        panel.addView(lyricsCard.view(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        divider = new View(ctx);
        divider.setBackgroundColor(Appearance.withAlphaOf(0x1F000000, ink));
        divider.setVisibility(View.GONE);
        LinearLayout.LayoutParams dividerLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(1)));
        dividerLp.setMarginStart(dp(14));
        dividerLp.setMarginEnd(dp(14));
        panel.addView(divider, dividerLp);
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(10), dp(10), dp(14), dp(10));
        panel.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        art = new ImageView(ctx);
        art.setScaleType(ImageView.ScaleType.CENTER_CROP);
        art.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(14));
            }
        });
        art.setClipToOutline(true);
        row.addView(art, new LinearLayout.LayoutParams(artPx, artPx));
        if (lyricsEnabled) {
            // OK on the cover opens the full-screen lyrics. Focused, it gets Home's white ring inside its corners.
            art.setFocusable(true);
            art.setContentDescription(ctx.getString(R.string.lyrics_open));
            GradientDrawable ring = new GradientDrawable();
            ring.setCornerRadius(dp(14));
            ring.setStroke(dp(3), light ? Appearance.INK_DARK : 0xF5FFFFFF);
            StateListDrawable focusRing = new StateListDrawable();
            focusRing.addState(new int[]{android.R.attr.state_focused}, ring);
            focusRing.addState(new int[]{}, new GradientDrawable());
            art.setForeground(focusRing);
            art.setOnClickListener(v -> openScreen());
        }

        LinearLayout text = new LinearLayout(ctx);
        text.setOrientation(LinearLayout.VERTICAL);
        // The buttons sit 8dp left of the text so their glyphs line up with it: a focused button's disc reaches past
        // the column, into the gap beside the cover, and must not be cut off there.
        text.setClipChildren(false);
        text.setClipToPadding(false);
        row.setClipChildren(false);
        row.setClipToPadding(false);
        panel.setClipChildren(false);
        panel.setClipToPadding(false);
        LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(dp(250), ViewGroup.LayoutParams.WRAP_CONTENT);
        textLp.setMarginStart(dp(14));
        row.addView(text, textLp);
        // The cover is a square as tall as the text column (title, artist and buttons), so it fills the panel's height
        // inside the same inset as its sides.
        text.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            int h = b - t;
            ViewGroup.LayoutParams a = art.getLayoutParams();
            if (h > 0 && (a.width != h || a.height != h)) {
                a.width = h;
                a.height = h;
                art.setLayoutParams(a);
            }
        });

        title = line(ink, 17, R.font.inter_semibold);
        // A title too long for the panel scrolls to its end now and then (marquee), instead of staying cut off.
        title.setEllipsize(TextUtils.TruncateAt.MARQUEE);
        title.setMarqueeRepeatLimit(1);
        title.setHorizontalFadingEdgeEnabled(true);
        title.setFadingEdgeLength(dp(18));
        artist = line(Appearance.withAlphaOf(0x9E000000, ink), 14, R.font.inter_medium);
        text.addView(title);
        text.addView(artist);

        LinearLayout controls = new LinearLayout(ctx);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.CENTER_VERTICAL);
        controls.setClipChildren(false);
        // As wide as the column (less the 8dp the buttons sit left of it), so the source icon lands in the corner.
        LinearLayout.LayoutParams controlsLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        controlsLp.topMargin = dp(4);
        controlsLp.setMarginStart(-dp(8));
        text.addView(controls, controlsLp);
        prev = button(R.drawable.ic_np_prev, R.string.np_previous, 26);
        playPause = button(R.drawable.ic_np_play, R.string.np_play, 28);
        next = button(R.drawable.ic_np_next, R.string.np_next, 26);
        controls.addView(prev);
        controls.addView(playPause);
        controls.addView(next);
        controls.addView(new View(ctx), new LinearLayout.LayoutParams(0, 1, 1f));
        // Where the music plays: this TV, or the device the Spotify account plays on (Material Symbols icons).
        source = new ImageView(ctx);
        source.setImageTintList(ColorStateList.valueOf(Appearance.withAlphaOf(0x8C000000, ink)));
        source.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        // Centred on the play/pause row; setSource sets its end margin so the glyph is as far from the panel's right
        // edge as from its bottom edge.
        LinearLayout.LayoutParams sourceLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sourceLp.gravity = Gravity.CENTER_VERTICAL;
        controls.addView(source, sourceLp);

        prev.setOnClickListener(v -> previous());
        next.setOnClickListener(v -> skipNext());
        playPause.setOnClickListener(v -> togglePlay());
        View.OnKeyListener keys = (v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP) { exits.up(); return true; }
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) { exits.down(); return true; }
            // The row's ends: stay on the panel instead of jumping to a tile or the top bar. Left of Previous is the
            // cover when it opens the full-screen lyrics.
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && v == prev && art.isFocusable()) {
                art.requestFocus();
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && v == art) {
                prev.requestFocus();
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) return v == prev || v == art;
            return keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && v == next;
        };
        art.setOnKeyListener(keys);
        prev.setOnKeyListener(keys);
        playPause.setOnKeyListener(keys);
        next.setOnKeyListener(keys);

        // Placed before its first layout, the panel only guesses its height; settle it once the real one is known.
        panel.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if (placed && b - t != ob - ot) place();
        });
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.END);
        // The same right edge as the top bar's buttons and the dock band: the header's 56dp padding.
        lp.setMarginEnd(dp(56));
        root.addView(panel, lp);
        screen = !lyricsEnabled ? null : new LyricsScreen(ctx, root, density, animations, new LyricsScreen.Controls() {
            @Override public void previous() { NowPlayingPanel.this.previous(); }
            @Override public void playPause() { togglePlay(); }
            @Override public void next() { skipNext(); }
            @Override public void closed() { if (panel.getVisibility() == View.VISIBLE) art.requestFocus(); }
        });
    }

    private void previous() {
        if (remoteShown) remoteControl(SpotifyApi.Action.PREVIOUS);
        else if (controller != null) controller.getTransportControls().skipToPrevious();
    }

    private void skipNext() {
        if (remoteShown) remoteControl(SpotifyApi.Action.NEXT);
        else if (controller != null) controller.getTransportControls().skipToNext();
    }

    private void togglePlay() {
        if (remoteShown) remoteControl(playing ? SpotifyApi.Action.PAUSE : SpotifyApi.Action.PLAY);
        else if (controller == null) return;
        else if (playing) controller.getTransportControls().pause();
        else controller.getTransportControls().play();
    }

    /** The full-screen lyrics for the song now showing. */
    private void openScreen() {
        if (screen == null) return;
        syncScreen();
        screen.setLyrics(lyrics, lyricsKnown);
        screen.open();
        if (lyrics != null) screen.show(lyrics.indexAt(clock.positionAt(SystemClock.elapsedRealtime())));
    }

    /** Copies the panel's song, buttons and cover to the full-screen view; it skips what has not changed. */
    private void syncScreen() {
        if (screen == null) return;
        screen.setSong(title.getText(), artist.getVisibility() == View.VISIBLE ? artist.getText() : "");
        screen.setPlaying(playing);
        screen.setSkips(prev.getAlpha(), next.getAlpha());
        screen.setCover(fullArt);
        if (source.getDrawable() instanceof android.graphics.drawable.BitmapDrawable) {
            screen.setSource(((android.graphics.drawable.BitmapDrawable) source.getDrawable()).getBitmap());
        }
    }

    boolean isScreenOpen() {
        return screen != null && screen.isOpen();
    }

    /** Keys while the full-screen lyrics are up (LyricsScreen.onKey); false lets OK reach the focused button. */
    boolean onScreenKey(KeyEvent event) {
        return screen != null && screen.isOpen() && screen.onKey(event);
    }

    private int dp(float v) {
        return Math.round(v * density);
    }

    private TextView line(int color, float sp, int font) {
        TextView t = new TextView(ctx);
        t.setTextColor(color);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTypeface(ctx.getResources().getFont(font));
        t.setLetterSpacing(-0.01f);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setIncludeFontPadding(false);
        t.setPadding(0, dp(2), 0, dp(2));
        return t;
    }

    /** A round button: no fill at rest, a solid disc when focused (white on dark, ink on light), the glyph inverted. */
    private ImageButton button(int icon, int label, int iconDp) {
        ImageButton b = new ImageButton(ctx);
        b.setImageResource(icon);
        b.setContentDescription(ctx.getString(label));
        b.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        int size = dp(42), pad = (size - dp(iconDp)) / 2;
        b.setPadding(pad, pad, pad, pad);
        GradientDrawable on = new GradientDrawable();
        on.setShape(GradientDrawable.OVAL);
        on.setColor(light ? Appearance.INK_DARK : 0xF5FFFFFF);
        StateListDrawable bg = new StateListDrawable();
        bg.addState(new int[]{android.R.attr.state_focused}, on);
        bg.addState(new int[]{}, new GradientDrawable());
        b.setBackground(bg);
        b.setImageTintList(new ColorStateList(new int[][]{{android.R.attr.state_focused}, {}},
                new int[]{light ? 0xFFFFFFFF : Appearance.INK_DARK, Appearance.ink(light)}));
        b.setFocusable(true);
        b.setLayoutParams(new LinearLayout.LayoutParams(size, size));
        return b;
    }

    /** Home is in front: follow the TV's media sessions. Without the media access the panel simply stays hidden. */
    void start() {
        if (started) return;
        started = true;
        main.postDelayed(marquee, 3_000);
        startPolling();
        if (lyricsEnabled) {
            lyricsThread = new android.os.HandlerThread("lyrics");
            lyricsThread.start();
            lyricsLookup = new Handler(lyricsThread.getLooper());
            main.post(lyricsTick);
        }
        sessions = ctx.getSystemService(MediaSessionManager.class);
        try {
            ComponentName listener = new ComponentName(ctx, NowPlayingListener.class);
            sessions.addOnActiveSessionsChangedListener(sessionsChanged, listener, main);
            pick(sessions.getActiveSessions(listener));
        } catch (SecurityException e) {
            sessions = null;
            panel.setVisibility(View.GONE);
        }
    }

    void stop() {
        if (!started) return;
        started = false;
        main.removeCallbacks(marquee);
        main.removeCallbacks(lyricsTick);
        if (lyricsThread != null) {
            lyricsLookup.removeCallbacksAndMessages(null);
            lyricsThread.quitSafely();
            lyricsThread = null;
            lyricsLookup = null;
        }
        stopPolling();
        if (screen != null) screen.close();
        if (sessions != null) sessions.removeOnActiveSessionsChangedListener(sessionsChanged);
        dropAll();
    }

    // ---- spotify_connect: the account on other devices --------------------------------------------------------

    /** Re-reads the link on every start, so `spotify-link.py push` takes effect the next time Home comes up. */
    private void startPolling() {
        if (!spotifyConnect) return;
        spotify = SpotifyLink.api(ctx);
        if (spotify == null) return;
        pollThread = new android.os.HandlerThread("spotify-now-playing");
        pollThread.start();
        poller = new Handler(pollThread.getLooper());
        poller.post(poll);
    }

    private void stopPolling() {
        if (pollThread != null) {
            poller.removeCallbacksAndMessages(null);
            pollThread.quitSafely();
            pollThread = null;
            poller = null;
        }
        remote = null;
        remoteShown = false;
    }

    /** Background: asks Spotify, fetches a new cover, hands both to the main thread and schedules the next ask. */
    private void pollOnce() {
        SpotifyApi api = spotify;
        Handler h = poller;
        if (api == null || h == null) return;
        SpotifyPlayback p = api.playback();
        Bitmap cover = null;
        String coverUrl = p == null ? null : p.artUrl;
        boolean newCover = coverUrl != null && !coverUrl.equals(remoteArtUrl);
        if (newCover) cover = download(coverUrl);
        final Bitmap fetched = cover;
        main.post(() -> {
            if (!started) return;
            remote = p;
            remoteAt = SystemClock.elapsedRealtime();
            if (newCover) {
                remoteArtUrl = coverUrl;
                remoteArt = fetched;
            }
            pick(active);
        });
        long next = api.status() == SpotifyApi.Status.RATE_LIMITED ? POLL_BACKOFF_MS
                : p != null && p.playing ? POLL_PLAYING_MS : POLL_IDLE_MS;
        h.postDelayed(poll, next);
    }

    /** A control for the device the account plays on; the panel flips play/pause at once and asks again after. */
    private void remoteControl(SpotifyApi.Action action) {
        SpotifyApi api = spotify;
        Handler h = poller;
        SpotifyPlayback p = remote;
        if (api == null || h == null || p == null) return;
        if (action == SpotifyApi.Action.PAUSE || action == SpotifyApi.Action.PLAY) {
            // Keep the lyrics where they are: the position now, not the one from the last answer.
            long now = SystemClock.elapsedRealtime();
            long at = clock.positionAt(now);
            remoteAt = now;
            remote = new SpotifyPlayback(action == SpotifyApi.Action.PLAY, p.title, p.artist, p.artUrl, p.device,
                    p.deviceId, p.deviceType, p.itemId, p.album, p.durationMs, at);
            show();
        }
        h.post(() -> {
            api.control(action, p.deviceId);
            // Spotify takes a moment to switch songs: ask again shortly, then keep the usual pace.
            h.removeCallbacks(poll);
            h.postDelayed(poll, 700);
        });
    }

    /** A cover from Spotify's image server, decoded small; null on any failure (the panel shows the music glyph). */
    private Bitmap download(String url) {
        if (url == null || !url.startsWith("https://")) return null;
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

    private List<MediaController> activeSessions() {
        try {
            return sessions == null ? null : sessions.getActiveSessions(new ComponentName(ctx, NowPlayingListener.class));
        } catch (SecurityException e) {
            return null;
        }
    }

    /** Follows every active session and shows the one NowPlayingChoice picks. */
    private void pick(List<MediaController> list) {
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
        List<NowPlayingChoice.Session> sessions = new ArrayList<>();
        for (MediaController c : active) sessions.add(sessionOf(c));
        NowPlayingChoice.Session account = remote == null ? null : new NowPlayingChoice.Session(REMOTE_KEY,
                remote.playing ? NowPlayingChoice.PLAYING : NowPlayingChoice.PAUSED, true);
        int at = NowPlayingChoice.chooseWithRemote(sessions, controller == null ? null : controller.getSessionToken(),
                account, remoteShown);
        remoteShown = at == NowPlayingChoice.REMOTE;
        controller = at < 0 ? null : active.get(at);
        show();
    }

    /** Stops following every session (Home is in the background). */
    private void dropAll() {
        for (MediaController c : followed.values()) c.unregisterCallback(callback);
        followed.clear();
        active = new ArrayList<>();
        controller = null;
        show();
    }

    private static NowPlayingChoice.Session sessionOf(MediaController c) {
        return new NowPlayingChoice.Session(c.getSessionToken(), stateOf(c), titleOf(c) != null);
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

    private void show() {
        if (remoteShown && remote != null) {
            showRemote();
            return;
        }
        if (controller == null) {
            hide();
            return;
        }
        int state = stateOf(controller);
        String song = titleOf(controller);
        if (!NowPlayingChoice.visible(sessionOf(controller))) {
            hide();
            return;
        }
        MediaMetadata m = controller.getMetadata();
        CharSequence by = m.getText(MediaMetadata.METADATA_KEY_ARTIST);
        if (TextUtils.isEmpty(by)) by = m.getText(MediaMetadata.METADATA_KEY_ALBUM_ARTIST);
        if (TextUtils.isEmpty(by) && m.getDescription() != null) by = m.getDescription().getSubtitle();
        setText(title, song);
        setText(artist, by);
        PlaybackState ps = controller.getPlaybackState();
        if (ps != null) {
            clock.set(ps.getPosition(), ps.getLastPositionUpdateTime(),
                    ps.getState() == PlaybackState.STATE_PLAYING ? ps.getPlaybackSpeed() : 0f);
        }
        lyricsFor(controller.getPackageName() + "\n" + song + "\n" + by, song, by == null ? "" : by.toString(),
                m.getString(MediaMetadata.METADATA_KEY_ALBUM), m.getLong(MediaMetadata.METADATA_KEY_DURATION));
        setSource("tv");   // a session on this TV
        artist.setVisibility(TextUtils.isEmpty(by) ? View.GONE : View.VISIBLE);
        playing = state == PlaybackState.STATE_PLAYING || state == PlaybackState.STATE_BUFFERING;
        showPlaying(playing);
        long actions = controller.getPlaybackState() == null ? 0 : controller.getPlaybackState().getActions();
        prev.setAlpha((actions & PlaybackState.ACTION_SKIP_TO_PREVIOUS) != 0 ? 1f : 0.35f);
        next.setAlpha((actions & PlaybackState.ACTION_SKIP_TO_NEXT) != 0 ? 1f : 0.35f);
        // Spotify sends a new song's title first and its cover in a later update: follow the bitmap too.
        String key = controller.getPackageName() + "\n" + song + "\n" + by;
        Bitmap cover = coverOf(m);
        if (!key.equals(artKey) || cover != artSource) {
            artKey = key;
            artSource = cover;
            setArt(m, cover);
        }
        if (screen != null && screen.isOpen()) syncScreen();
        if (panel.getVisibility() != View.VISIBLE && placed && !suppressed) {
            panel.setAlpha(0f);
            panel.setVisibility(View.VISIBLE);
            panel.animate().alpha(1f).setDuration(240).start();
        }
    }

    /** The account on another device: its song, its artist, the device's icon and its cover once downloaded. */
    private void showRemote() {
        SpotifyPlayback p = remote;
        setText(title, p.title);
        setText(artist, p.subtitle());
        clock.set(p.progressMs, remoteAt, p.playing ? 1f : 0f);
        lyricsFor("spotify\n" + p.itemId + "\n" + p.title, p.title, p.artist, p.album, p.durationMs);
        setSource(p.sourceIcon());
        artist.setVisibility(View.VISIBLE);
        playing = p.playing;
        showPlaying(playing);
        prev.setAlpha(1f);
        next.setAlpha(1f);
        String key = "spotify\n" + p.itemId + "\n" + p.artUrl;
        Bitmap cover = p.artUrl != null && p.artUrl.equals(remoteArtUrl) ? remoteArt : null;
        if (!key.equals(artKey) || cover != artSource) {
            artKey = key;
            artSource = cover;
            applyArt(cover);
        }
        if (screen != null && screen.isOpen()) syncScreen();
        if (panel.getVisibility() != View.VISIBLE && placed && !suppressed) {
            panel.setAlpha(0f);
            panel.setVisibility(View.VISIBLE);
            panel.animate().alpha(1f).setDuration(240).start();
        }
    }

    /** Shows the icon with this SpotifyPlayback.sourceIcon name in the corner. Named here, not looked up, so the
     *  release build's resource shrinker sees every one of them in use. */
    private void setSource(String icon) {
        int id;
        switch (icon) {
            case "smartphone": id = R.drawable.ic_src_smartphone; break;
            case "laptop_mac": id = R.drawable.ic_src_laptop_mac; break;
            case "tablet_mac": id = R.drawable.ic_src_tablet_mac; break;
            case "tv": id = R.drawable.ic_src_tv; break;
            case "speaker": id = R.drawable.ic_src_speaker; break;
            case "smart_display": id = R.drawable.ic_src_smart_display; break;
            case "directions_car": id = R.drawable.ic_src_directions_car; break;
            case "sports_esports": id = R.drawable.ic_src_sports_esports; break;
            default: id = R.drawable.ic_src_devices; break;
        }
        if (icon.equals(sourceShown)) return;
        sourceShown = icon;
        Bitmap glyph = trimmed(id);
        source.setImageBitmap(glyph);
        // Centred on the 42dp button row, the glyph's gap to the panel's bottom edge is half the row's spare height
        // plus the row's own distance from that edge; the end margin gives the right edge the same gap. The row ends
        // at the panel's end padding, and the panel's rim sits a few pixels in from both edges alike, hence the 3.5dp
        // (measured on the TV: 21dp and 21dp for the phone glyph).
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) source.getLayoutParams();
        lp.setMarginEnd((dp(BUTTON_DP) - glyph.getHeight()) / 2 - dp(3.5f));
        source.setLayoutParams(lp);
    }

    /**
     * The icon drawn at SOURCE_ICON_DP and cut to its visible glyph. Material Symbols keep different margins inside
     * their square (a phone is tall and narrow, a TV wide and short), so only the trimmed glyph can sit the same
     * distance from both edges whichever icon it is.
     */
    private Bitmap trimmed(int id) {
        int px = dp(SOURCE_ICON_DP);
        android.graphics.drawable.Drawable d = ctx.getDrawable(id).mutate();
        Bitmap full = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888);
        d.setBounds(0, 0, px, px);
        d.draw(new android.graphics.Canvas(full));
        int left = px, top = px, right = -1, bottom = -1;
        int[] row = new int[px];
        for (int y = 0; y < px; y++) {
            full.getPixels(row, 0, px, 0, y, px, 1);
            for (int x = 0; x < px; x++) {
                if ((row[x] >>> 24) < 24) continue;
                if (x < left) left = x;
                if (x > right) right = x;
                if (y < top) top = y;
                bottom = y;
            }
        }
        if (right < 0) return full;
        return Bitmap.createBitmap(full, left, top, right - left + 1, bottom - top + 1);
    }

    /**
     * The lyrics card for this song: from the cache, or looked up off the main thread. The card stays hidden until
     * synced lyrics turn up, and for songs that have none (or only plain text, which cannot follow the song).
     */
    private void lyricsFor(String key, String song, String by, String album, long durationMs) {
        if (!lyricsEnabled || lyricsLookup == null || key.equals(lyricsKey)) return;
        lyricsKey = key;
        lyricsKnown = false;
        showLyrics(null);
        LrcLib.Result cached = lyricsCache.get(key);
        if (cached != null) {
            lyricsKnown = true;
            showLyrics(cached.lyrics);
            return;
        }
        Handler h = lyricsLookup;
        h.post(() -> {
            LrcLib.Result found;
            try {
                found = lrclib.find(song, by, album, durationMs);
            } catch (java.io.IOException e) {
                found = null;   // offline: not cached, so the next showing of this song asks again
            }
            final LrcLib.Result result = found;
            main.post(() -> {
                if (result != null) lyricsCache.put(key, result);
                if (!key.equals(lyricsKey)) return;
                lyricsKnown = true;
                showLyrics(result == null ? null : result.lyrics);
            });
        });
    }

    /** Shows the card with these lyrics (fading it in), or hides it for null. */
    private void showLyrics(Lyrics l) {
        lyrics = l;
        lyricsCard.setLyrics(l);
        if (screen != null && screen.isOpen()) {
            screen.setLyrics(l, lyricsKnown);
            if (l != null) screen.show(l.indexAt(clock.positionAt(SystemClock.elapsedRealtime())));
        }
        boolean on = l != null;
        if (on == (lyricsCard.view().getVisibility() == View.VISIBLE)) return;
        divider.setVisibility(on ? View.VISIBLE : View.GONE);
        lyricsCard.view().setVisibility(on ? View.VISIBLE : View.GONE);
        if (on) {
            lyricsCard.show(l.indexAt(clock.positionAt(SystemClock.elapsedRealtime())));
            lyricsCard.view().setAlpha(0f);
            lyricsCard.view().animate().alpha(1f).setDuration(320).start();
        }
    }

    /**
     * Changes a line only when its text changes. The panel refreshes on every Spotify answer and session update, and
     * setText, even with the same words, restarts the title's scroll from the start: the glitch every few seconds.
     */
    private static void setText(TextView view, CharSequence text) {
        if (!TextUtils.equals(view.getText(), text)) view.setText(text);
    }

    /** The play/pause glyph, swapped only when the state flips (a refresh would otherwise reload it each time). */
    private void showPlaying(boolean on) {
        if (playIconShown != null && playIconShown == on) return;
        playIconShown = on;
        playPause.setImageResource(on ? R.drawable.ic_np_pause : R.drawable.ic_np_play);
        playPause.setContentDescription(ctx.getString(on ? R.string.np_pause : R.string.np_play));
    }

    private void hide() {
        // Nothing to show: the next song starts its lyrics afresh.
        if (lyricsKey != null) {
            lyricsKey = null;
            showLyrics(null);
        }
        if (screen != null) screen.close();
        boolean hadFocus = panel.hasFocus();
        panel.setVisibility(View.GONE);
        artKey = null;
        artSource = null;
        if (hadFocus) exits.down();
    }

    /** The cover from the metadata: a bitmap if the app sends one, else a content URI; a music glyph otherwise. */
    private static Bitmap coverOf(MediaMetadata m) {
        Bitmap bmp = m.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
        if (bmp == null) bmp = m.getBitmap(MediaMetadata.METADATA_KEY_ART);
        if (bmp == null) bmp = m.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON);
        if (bmp == null && m.getDescription() != null) bmp = m.getDescription().getIconBitmap();
        return bmp;
    }

    private void setArt(MediaMetadata m, Bitmap bmp) {
        if (bmp == null) {
            String uri = m.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI);
            if (uri == null) uri = m.getString(MediaMetadata.METADATA_KEY_ART_URI);
            if (uri == null && m.getDescription() != null && m.getDescription().getIconUri() != null) {
                uri = m.getDescription().getIconUri().toString();
            }
            bmp = load(uri);
        }
        applyArt(bmp);
    }

    /** The cover, or the music glyph on a soft tile when there is none. */
    private void applyArt(Bitmap bmp) {
        fullArt = bmp;
        if (screen != null && screen.isOpen()) screen.setCover(bmp);
        if (bmp != null) {
            art.setBackground(null);
            art.setPadding(0, 0, 0, 0);
            art.setImageTintList(null);
            art.setImageBitmap(bmp.getWidth() > artPx * 2 ? Bitmap.createScaledBitmap(bmp, artPx * 2,
                    Math.max(1, Math.round(artPx * 2f * bmp.getHeight() / bmp.getWidth())), true) : bmp);
        } else {
            GradientDrawable tile = new GradientDrawable();
            tile.setColor(light ? 0x14000000 : 0x24FFFFFF);
            art.setBackground(tile);
            int pad = dp(24);
            art.setPadding(pad, pad, pad, pad);
            art.setImageResource(R.drawable.ic_np_music);
            art.setImageTintList(ColorStateList.valueOf(Appearance.withAlphaOf(0x99000000, Appearance.ink(light))));
        }
    }

    /** A local cover only (content:, android.resource:, file:); Spotify's own covers come through download(). */
    private Bitmap load(String uri) {
        if (uri == null) return null;
        Uri u = Uri.parse(uri);
        String scheme = u.getScheme();
        if (!ContentResolver.SCHEME_CONTENT.equals(scheme) && !ContentResolver.SCHEME_ANDROID_RESOURCE.equals(scheme)
                && !ContentResolver.SCHEME_FILE.equals(scheme)) return null;
        try (InputStream in = ctx.getContentResolver().openInputStream(u)) {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inSampleSize = 2;
            return in == null ? null : BitmapFactory.decodeStream(in, null, o);
        } catch (Exception e) {
            return null;
        }
    }

    void setAbove(View view) {
        above = view;
    }

    /**
     * Just above the dock band (which reaches 15dp above the tiles), level with the shelf's name; never closer to the
     * top bar's buttons than to the band. Before the top bar is laid out, 30dp clear of the band.
     */
    private void place() {
        float h = panel.getHeight() > 0 ? panel.getHeight() : dp(92);
        float bandTop = dockTop - bandReach;
        float y = bandTop - dp(30) - h;
        if (above != null && above.getHeight() > 0) {
            android.graphics.Rect r = new android.graphics.Rect(0, 0, above.getWidth(), above.getHeight());
            root.offsetDescendantRectToMyCoords(above, r);
            // Anchored 16dp above the band, beside the shelf's name; where the room is tighter than that, the same gap
            // above and below instead.
            float gap = Math.min(dp(16), (bandTop - r.bottom - h) / 2f);
            y = bandTop - gap - h;
        }
        panel.setY(Math.round(y));
    }

    boolean isShown() {
        return panel.getVisibility() == View.VISIBLE && !suppressed;
    }

    /** Fades the panel out while focus is in the rows under the dock, and back in (if a song is up) after. */
    void setSuppressed(boolean suppress) {
        if (suppressed == suppress) return;
        suppressed = suppress;
        panel.animate().cancel();
        if (suppress) {
            if (panel.getVisibility() == View.VISIBLE) {
                panel.animate().alpha(0f).setDuration(160).withEndAction(() -> {
                    if (suppressed) panel.setVisibility(View.INVISIBLE);
                }).start();
            }
        } else {
            if (panel.getVisibility() == View.INVISIBLE) panel.setVisibility(View.GONE);
            show();
        }
    }

    void focus() {
        playPause.requestFocus();
    }

    /** The dock band reaches this far above the tiles (dock_even_padding draws it further out). */
    void setBandReach(float px) {
        bandReach = px;
        if (placed) place();
    }

    /** Sits above the dock, on the right of the shelf's name: its bottom on the same line as the shelf's. */
    void placeAbove(float dockTop) {
        this.dockTop = dockTop;
        place();
        if (!placed) {
            placed = true;
            show();
        }
    }
}
