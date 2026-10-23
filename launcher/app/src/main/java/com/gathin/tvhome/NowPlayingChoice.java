package com.gathin.tvhome;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * The Now Playing panel's rules, apart from Android's media classes so a JVM test can run them: which session the
 * panel shows, and which sessions it follows. It follows every active session, not only the one it shows: a session
 * that is open but stopped (Spotify after a song ends) can start playing again without the list of sessions changing,
 * and only that session's own callback says so.
 */
final class NowPlayingChoice {
    /** android.media.session.PlaybackState's values for the states the panel cares about. */
    static final int NONE = 0, STOPPED = 1, PAUSED = 2, PLAYING = 3, BUFFERING = 6, CONNECTING = 8;

    /** One media session as the panel sees it: a stable key, its playback state, and whether it names a song. */
    static final class Session {
        final Object key;
        final int state;
        final boolean hasTitle;

        Session(Object key, int state, boolean hasTitle) {
            this.key = key;
            this.state = state;
            this.hasTitle = hasTitle;
        }
    }

    private NowPlayingChoice() {}

    /**
     * The session to show, as an index into {@code sessions}, or -1 for none: the first one playing, else the first
     * paused (or buffering) one with a song, else the one already shown ({@code shownKey}) while it is still active,
     * so the panel does not flicker away between songs.
     */
    static int choose(List<Session> sessions, Object shownKey) {
        for (int i = 0; i < sessions.size(); i++) {
            if (sessions.get(i).state == PLAYING) return i;
        }
        for (int i = 0; i < sessions.size(); i++) {
            Session s = sessions.get(i);
            if ((s.state == PAUSED || s.state == BUFFERING) && s.hasTitle) return i;
        }
        if (shownKey != null) {
            for (int i = 0; i < sessions.size(); i++) {
                if (shownKey.equals(sessions.get(i).key)) return i;
            }
        }
        return -1;
    }

    /** choose()'s answer when the Spotify account on another device wins (spotify_connect), or nothing does. */
    static final int REMOTE = -2, NOTHING = -1;

    /**
     * choose() with the Spotify account's playback on any device as one more source: a session playing on the TV
     * first, then the account playing elsewhere, then a paused song on the TV. The account paused elsewhere only stays
     * while the panel already shows it ({@code remoteShown}), so a phone paused hours ago does not take the panel.
     * Returns an index into {@code sessions}, {@link #REMOTE} or {@link #NOTHING}.
     */
    static int chooseWithRemote(List<Session> sessions, Object shownKey, Session remote, boolean remoteShown) {
        for (int i = 0; i < sessions.size(); i++) {
            if (sessions.get(i).state == PLAYING) return i;
        }
        boolean remoteUsable = remote != null && remote.hasTitle;
        if (remoteUsable && remote.state == PLAYING) return REMOTE;
        for (int i = 0; i < sessions.size(); i++) {
            Session s = sessions.get(i);
            if ((s.state == PAUSED || s.state == BUFFERING) && s.hasTitle) return i;
        }
        if (remoteUsable && remoteShown) return REMOTE;
        if (shownKey != null) {
            for (int i = 0; i < sessions.size(); i++) {
                if (shownKey.equals(sessions.get(i).key)) return i;
            }
        }
        return NOTHING;
    }

    /** Whether the chosen session belongs on screen: it names a song and is playing, paused or about to play. */
    static boolean visible(Session s) {
        return s != null && s.hasTitle && (s.state == PLAYING || s.state == PAUSED || s.state == BUFFERING
                || s.state == CONNECTING);
    }

    /** Keys in {@code active} not followed yet: the sessions to start following. */
    static <K> List<K> toFollow(Collection<K> followed, Collection<K> active) {
        List<K> out = new ArrayList<>();
        for (K k : active) if (!followed.contains(k)) out.add(k);
        return out;
    }

    /** Keys followed but no longer active: the sessions to stop following. */
    static <K> List<K> toDrop(Collection<K> followed, Collection<K> active) {
        List<K> out = new ArrayList<>();
        for (K k : followed) if (!active.contains(k)) out.add(k);
        return out;
    }
}
