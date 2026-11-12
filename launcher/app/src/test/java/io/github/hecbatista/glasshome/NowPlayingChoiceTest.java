package io.github.hecbatista.glasshome;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class NowPlayingChoiceTest {
    /**
     * The panel's wiring with Android's media classes taken out: the TV's sessions, the ones the panel follows, and
     * what it shows. A session's own change only reaches the panel through a callback on that session, so a change
     * in a session the panel does not follow is lost, as it is on the TV.
     */
    private static final class Home {
        final Map<String, NowPlayingChoice.Session> tv = new LinkedHashMap<>();
        final Set<String> followed = new HashSet<>();
        final boolean followAll;
        String shown;

        Home(boolean followAll) {
            this.followAll = followAll;
        }

        /** MediaSessionManager's listener: the list of active sessions changed. */
        void sessionsChanged() {
            pick();
        }

        /** A session's playback state or song changed; only a followed session's callback fires. */
        void sessionChanged(String key, int state, boolean hasTitle) {
            tv.put(key, new NowPlayingChoice.Session(key, state, hasTitle));
            if (followed.contains(key)) pick();
        }

        private void pick() {
            List<Object> keys = new ArrayList<>(tv.keySet());
            for (Object k : NowPlayingChoice.toDrop(new ArrayList<Object>(followed), keys)) followed.remove(k);
            List<NowPlayingChoice.Session> sessions = new ArrayList<>(tv.values());
            int at = NowPlayingChoice.choose(sessions, shown);
            NowPlayingChoice.Session s = at < 0 ? null : sessions.get(at);
            shown = s == null ? null : (String) s.key;
            if (followAll) {
                for (Object k : NowPlayingChoice.toFollow(new ArrayList<Object>(followed), keys)) followed.add((String) k);
            } else {
                // The panel before this change: a callback on the shown session only.
                followed.clear();
                if (shown != null) followed.add(shown);
            }
            if (!NowPlayingChoice.visible(s)) shown = null;
        }
    }

    private static NowPlayingChoice.Session session(String key, int state, boolean title) {
        return new NowPlayingChoice.Session(key, state, title);
    }

    @Test public void spotifyStartingFromStoppedShowsWithoutReopeningHome() {
        // The reported case: Home is up, Spotify's session is open but stopped, and music starts from a phone.
        Home home = new Home(true);
        home.tv.put("spotify", session("spotify", NowPlayingChoice.STOPPED, true));
        home.tv.put("alexa", session("alexa", NowPlayingChoice.NONE, false));
        home.sessionsChanged();
        assertNull("a stopped session is not shown", home.shown);
        assertTrue("but it is followed", home.followed.contains("spotify"));

        home.sessionChanged("spotify", NowPlayingChoice.PLAYING, true);
        assertEquals("spotify", home.shown);
    }

    @Test public void followingOnlyTheShownSessionMissedIt() {
        // The same steps with the old wiring: Spotify's change never reaches the panel until Home is reopened.
        Home home = new Home(false);
        home.tv.put("spotify", session("spotify", NowPlayingChoice.STOPPED, true));
        home.sessionsChanged();
        home.sessionChanged("spotify", NowPlayingChoice.PLAYING, true);
        assertNull(home.shown);
        home.sessionsChanged();   // Home reopened
        assertEquals("spotify", home.shown);
    }

    @Test public void stoppingHidesThePanelAndPlayingAgainBringsItBack() {
        Home home = new Home(true);
        home.tv.put("spotify", session("spotify", NowPlayingChoice.PLAYING, true));
        home.sessionsChanged();
        assertEquals("spotify", home.shown);
        home.sessionChanged("spotify", NowPlayingChoice.STOPPED, true);
        assertNull(home.shown);
        home.sessionChanged("spotify", NowPlayingChoice.PLAYING, true);
        assertEquals("spotify", home.shown);
    }

    @Test public void aSecondAppStartingTakesThePanel() {
        Home home = new Home(true);
        home.tv.put("spotify", session("spotify", NowPlayingChoice.PAUSED, true));
        home.tv.put("music", session("music", NowPlayingChoice.STOPPED, true));
        home.sessionsChanged();
        assertEquals("spotify", home.shown);
        home.sessionChanged("music", NowPlayingChoice.PLAYING, true);
        assertEquals("music", home.shown);
    }

    @Test public void playingWinsOverPausedAndAPausedSongNeedsATitle() {
        List<NowPlayingChoice.Session> list = Arrays.asList(
                session("a", NowPlayingChoice.PAUSED, true), session("b", NowPlayingChoice.PLAYING, true));
        assertEquals(1, NowPlayingChoice.choose(list, null));
        assertEquals(-1, NowPlayingChoice.choose(
                Arrays.asList(session("a", NowPlayingChoice.PAUSED, false)), null));
    }

    @Test public void theShownSessionStaysBetweenSongs() {
        // Skipping passes through other states (skipping, buffering): the panel keeps the session it shows.
        List<NowPlayingChoice.Session> list = Arrays.asList(
                session("alexa", NowPlayingChoice.NONE, false), session("spotify", 10, true));
        assertEquals(1, NowPlayingChoice.choose(list, "spotify"));
        assertEquals(-1, NowPlayingChoice.choose(list, null));
        assertFalse(NowPlayingChoice.visible(session("spotify", NowPlayingChoice.STOPPED, true)));
        assertTrue(NowPlayingChoice.visible(session("spotify", NowPlayingChoice.BUFFERING, true)));
    }

    // ---- spotify_connect: the account on another device -------------------------------------------------------

    private static final String ACCOUNT = "spotify-account";

    @Test public void thePhonePlayingShowsWhenTheTvIsQuiet() {
        List<NowPlayingChoice.Session> tv = Arrays.asList(session("tv-spotify", NowPlayingChoice.STOPPED, true));
        assertEquals(NowPlayingChoice.REMOTE, NowPlayingChoice.chooseWithRemote(tv, null,
                session(ACCOUNT, NowPlayingChoice.PLAYING, true), false));
    }

    @Test public void theTvPlayingWinsOverThePhone() {
        List<NowPlayingChoice.Session> tv = Arrays.asList(session("tv-spotify", NowPlayingChoice.PLAYING, true));
        assertEquals(0, NowPlayingChoice.chooseWithRemote(tv, null,
                session(ACCOUNT, NowPlayingChoice.PLAYING, true), false));
    }

    @Test public void thePhonePlayingWinsOverAPausedSongOnTheTv() {
        List<NowPlayingChoice.Session> tv = Arrays.asList(session("tv-spotify", NowPlayingChoice.PAUSED, true));
        assertEquals(NowPlayingChoice.REMOTE, NowPlayingChoice.chooseWithRemote(tv, null,
                session(ACCOUNT, NowPlayingChoice.PLAYING, true), false));
    }

    @Test public void aPhonePausedLongAgoDoesNotTakeThePanel() {
        List<NowPlayingChoice.Session> none = new ArrayList<>();
        assertEquals(NowPlayingChoice.NOTHING, NowPlayingChoice.chooseWithRemote(none, null,
                session(ACCOUNT, NowPlayingChoice.PAUSED, true), false));
        // But pausing it from the panel keeps it there, so Play is one press away.
        assertEquals(NowPlayingChoice.REMOTE, NowPlayingChoice.chooseWithRemote(none, null,
                session(ACCOUNT, NowPlayingChoice.PAUSED, true), true));
    }

    @Test public void aPausedTvSongWinsOverAPausedPhone() {
        List<NowPlayingChoice.Session> tv = Arrays.asList(session("tv-spotify", NowPlayingChoice.PAUSED, true));
        assertEquals(0, NowPlayingChoice.chooseWithRemote(tv, null,
                session(ACCOUNT, NowPlayingChoice.PAUSED, true), true));
    }

    @Test public void withoutTheAccountItIsTheTvAlone() {
        List<NowPlayingChoice.Session> tv = Arrays.asList(session("tv-spotify", NowPlayingChoice.PAUSED, true));
        assertEquals(0, NowPlayingChoice.chooseWithRemote(tv, null, null, false));
        assertEquals(NowPlayingChoice.NOTHING, NowPlayingChoice.chooseWithRemote(new ArrayList<>(), null, null, true));
    }

    @Test public void sessionsThatEndAreDropped() {
        Home home = new Home(true);
        home.tv.put("spotify", session("spotify", NowPlayingChoice.PLAYING, true));
        home.sessionsChanged();
        home.tv.remove("spotify");
        home.sessionsChanged();
        assertNull(home.shown);
        assertTrue(home.followed.isEmpty());
    }
}
