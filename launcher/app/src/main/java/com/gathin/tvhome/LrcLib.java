package com.gathin.tvhome;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Finds synced lyrics on LRCLIB (lrclib.net, free, no key) for the Now Playing panel's lyrics card. Blocking: call it
 * off the main thread. It asks for the exact song first (title, artist, album, length), then searches by title and
 * artist and keeps the closest length; Spotify-style extras such as "(feat. …)" or "- Remastered" are tried without.
 */
final class LrcLib {
    static final String BASE = "https://lrclib.net/api";
    /** LRCLIB asks clients to say who they are. */
    static final String USER_AGENT = "GlassHome/1.0 (personal Fire TV launcher; github.com/Gathin23/cleanyourtv)";
    /** How far a search result's length may be from the song's and still count as the same recording. */
    static final int DURATION_SLACK_S = 3;

    /** What a lookup found: synced lyrics, an instrumental, or nothing usable (none, or plain text only). */
    static final class Result {
        final Lyrics lyrics;
        final boolean instrumental;

        Result(Lyrics lyrics, boolean instrumental) {
            this.lyrics = lyrics;
            this.instrumental = instrumental;
        }

        static final Result NONE = new Result(null, false);
    }

    /**
     * Lookups already made, by song key (the player and the song, as the Now Playing panel keys them): synced lyrics,
     * or none, so a song without them is not asked again. Shared by the panel and the StandBy face, so a song looked
     * up on Home costs the screensaver nothing. Main thread only.
     */
    static final Map<String, Result> CACHE = new LinkedHashMap<String, Result>(32, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Result> e) { return size() > 40; }
    };

    private final String base;
    private final SpotifyApi.Transport transport;

    LrcLib(String base, SpotifyApi.Transport transport) {
        this.base = base;
        this.transport = transport;
    }

    LrcLib() {
        this(BASE, new SpotifyApi.UrlTransport());
    }

    /** Lyrics for this song; durationMs 0 and an empty album when the player does not say. Never null. */
    Result find(String title, String artist, String album, long durationMs) throws IOException {
        if (title == null || title.trim().isEmpty() || artist == null || artist.trim().isEmpty()) return Result.NONE;
        String mainArtist = firstArtist(artist);
        int seconds = (int) Math.round(durationMs / 1000.0);
        for (String t : titles(title)) {
            if (album != null && !album.trim().isEmpty() && seconds > 0) {
                Result exact = get(t, mainArtist, album.trim(), seconds);
                if (exact != null) return exact;
            }
            Result found = search(t, mainArtist, seconds);
            if (found != null) return found;
        }
        return Result.NONE;
    }

    /** /api/get: the exact song, or null when LRCLIB has no such record (404). */
    private Result get(String title, String artist, String album, int seconds) throws IOException {
        SpotifyApi.Response r = send(base + "/get?track_name=" + enc(title) + "&artist_name=" + enc(artist)
                + "&album_name=" + enc(album) + "&duration=" + seconds);
        if (r.code != 200) return null;
        try {
            return resultOf(new JSONObject(r.body));
        } catch (JSONException e) {
            return null;
        }
    }

    /** /api/search: the closest-length record with synced lyrics (or an instrumental), or null. */
    private Result search(String title, String artist, int seconds) throws IOException {
        SpotifyApi.Response r = send(base + "/search?track_name=" + enc(title) + "&artist_name=" + enc(artist));
        if (r.code != 200) return null;
        try {
            JSONArray list = new JSONArray(r.body);
            JSONObject best = null;
            double bestGap = Double.MAX_VALUE;
            for (int i = 0; i < list.length(); i++) {
                JSONObject o = list.optJSONObject(i);
                if (o == null) continue;
                boolean usable = o.optBoolean("instrumental", false) || !o.optString("syncedLyrics", "").trim().isEmpty();
                if (!usable) continue;
                double gap = seconds > 0 ? Math.abs(o.optDouble("duration", 0) - seconds) : 0;
                if (seconds > 0 && gap > DURATION_SLACK_S) continue;
                if (gap < bestGap) {
                    best = o;
                    bestGap = gap;
                }
            }
            return best == null ? null : resultOf(best);
        } catch (JSONException e) {
            return null;
        }
    }

    private static Result resultOf(JSONObject o) {
        if (o.optBoolean("instrumental", false)) return new Result(null, true);
        Lyrics l = Lyrics.parseLrc(o.isNull("syncedLyrics") ? null : o.optString("syncedLyrics", null));
        return l == null ? null : new Result(l, false);
    }

    private SpotifyApi.Response send(String url) throws IOException {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("User-Agent", USER_AGENT);
        return transport.send("GET", url, headers, null);
    }

    private static final Pattern EXTRAS = Pattern.compile(
            "\\s*([(\\[](feat\\.?|ft\\.?|with)\\s[^)\\]]*[)\\]]|\\s-\\s.*(remaster|version|edit|mix|live|mono|stereo).*$)",
            Pattern.CASE_INSENSITIVE);

    /** The title as given, then without "(feat. …)", "[with …]" or "- 2011 Remaster" style extras, if that differs. */
    static String[] titles(String title) {
        String full = title.trim();
        String bare = EXTRAS.matcher(full).replaceAll("").trim();
        return bare.isEmpty() || bare.equalsIgnoreCase(full) ? new String[]{full} : new String[]{full, bare};
    }

    /** The first of several artists ("Drake, Don Toliver" or "A & B" or "A feat. B"): how LRCLIB files most songs. */
    static String firstArtist(String artist) {
        String a = artist.trim();
        String lower = a.toLowerCase(Locale.ROOT);
        int cut = a.length();
        for (String sep : new String[]{", ", " & ", " feat. ", " ft. ", " x ", " with "}) {
            int at = lower.indexOf(sep);
            if (at > 0 && at < cut) cut = at;
        }
        return a.substring(0, cut).trim();
    }

    private static String enc(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }
}
