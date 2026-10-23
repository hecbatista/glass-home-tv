package com.gathin.tvhome;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * What the owner's Spotify account is playing on any device, from the Web API's GET /v1/me/player (Glass Home's
 * spotify_connect). A song, or a podcast episode; null when nothing is playing anywhere (204, or no item).
 */
final class SpotifyPlayback {
    final boolean playing;
    final String title, artist, artUrl, device, deviceId, deviceType, itemId;
    /** For the lyrics card: the album, the song's length and how far in it was when Spotify answered. */
    final String album;
    final long durationMs, progressMs;

    SpotifyPlayback(boolean playing, String title, String artist, String artUrl, String device, String deviceId,
                    String deviceType, String itemId) {
        this(playing, title, artist, artUrl, device, deviceId, deviceType, itemId, "", 0, 0);
    }

    SpotifyPlayback(boolean playing, String title, String artist, String artUrl, String device, String deviceId,
                    String deviceType, String itemId, String album, long durationMs, long progressMs) {
        this.album = album;
        this.durationMs = durationMs;
        this.progressMs = progressMs;
        this.playing = playing;
        this.title = title;
        this.artist = artist;
        this.artUrl = artUrl;
        this.device = device;
        this.deviceId = deviceId;
        this.deviceType = deviceType;
        this.itemId = itemId;
    }

    /** The cover closest to this many pixels and at least as large, else the largest there is. */
    static final int ART_PX = 300;

    /** Parses /v1/me/player. An empty body (204) or no item (an ad, a private session) is null. */
    static SpotifyPlayback parse(String body) {
        if (body == null || body.trim().isEmpty()) return null;
        try {
            JSONObject o = new JSONObject(body);
            JSONObject item = o.optJSONObject("item");
            if (item == null) return null;
            String title = item.optString("name", "");
            if (title.isEmpty()) return null;
            String artist, album = "";
            JSONArray images;
            JSONArray artists = item.optJSONArray("artists");
            if (artists != null) {
                // A song: its artists, joined as Spotify shows them.
                StringBuilder names = new StringBuilder();
                for (int i = 0; i < artists.length(); i++) {
                    String n = artists.optJSONObject(i) == null ? "" : artists.optJSONObject(i).optString("name", "");
                    if (n.isEmpty()) continue;
                    if (names.length() > 0) names.append(", ");
                    names.append(n);
                }
                artist = names.toString();
                JSONObject albumObject = item.optJSONObject("album");
                images = albumObject == null ? null : albumObject.optJSONArray("images");
                album = albumObject == null ? "" : albumObject.optString("name", "");
            } else {
                // A podcast episode: its show, and the episode's own art.
                JSONObject show = item.optJSONObject("show");
                artist = show == null ? "" : show.optString("name", "");
                images = item.optJSONArray("images");
                if ((images == null || images.length() == 0) && show != null) images = show.optJSONArray("images");
            }
            JSONObject device = o.optJSONObject("device");
            return new SpotifyPlayback(o.optBoolean("is_playing", false), title, artist, pickImage(images, ART_PX),
                    device == null ? "" : device.optString("name", ""),
                    device == null ? "" : device.optString("id", ""),
                    device == null ? "" : device.optString("type", ""),
                    item.optString("id", ""), album, item.optLong("duration_ms", 0), o.optLong("progress_ms", 0));
        } catch (JSONException e) {
            return null;
        }
    }

    /** The smallest image at least {@code px} wide, else the widest; Spotify lists 640, 300 and 64. */
    static String pickImage(JSONArray images, int px) {
        if (images == null) return null;
        String best = null, widest = null;
        int bestW = Integer.MAX_VALUE, widestW = -1;
        for (int i = 0; i < images.length(); i++) {
            JSONObject img = images.optJSONObject(i);
            if (img == null) continue;
            String url = img.optString("url", "");
            if (url.isEmpty()) continue;
            int w = img.optInt("width", 0);
            if (w >= px && w < bestW) {
                best = url;
                bestW = w;
            }
            if (w > widestW) {
                widest = url;
                widestW = w;
            }
        }
        return best != null ? best : widest;
    }

    /** The Material Symbols icon for where the music plays (see sourceIcon). */
    String sourceIcon() {
        return sourceIcon(deviceType, device);
    }

    /**
     * The icon for a Spotify device, by the type Spotify reports and, for Amazon's Echos, the name: an Echo Show
     * reports itself as a Speaker, but it has a screen. One of the ic_src_* drawables' names.
     */
    static String sourceIcon(String type, String name) {
        String t = type == null ? "" : type.toLowerCase(java.util.Locale.ROOT);
        String n = name == null ? "" : name.toLowerCase(java.util.Locale.ROOT);
        if (n.contains("echo show")) return "smart_display";
        switch (t) {
            case "smartphone": return "smartphone";
            case "computer": return "laptop_mac";
            case "tablet": return "tablet_mac";
            case "tv": case "stb": case "castvideo": return "tv";
            case "speaker": case "avr": case "castaudio": case "audiodongle": return "speaker";
            case "automobile": return "directions_car";
            case "gameconsole": return "sports_esports";
            default: return n.contains("echo") ? "speaker" : "devices";
        }
    }

    /**
     * The second line under the title: the artist. The source icon beside the buttons already says where it plays,
     * so the device's name shows only when there is no artist (a podcast episode without one, say).
     */
    String subtitle() {
        return artist.isEmpty() ? device : artist;
    }
}
