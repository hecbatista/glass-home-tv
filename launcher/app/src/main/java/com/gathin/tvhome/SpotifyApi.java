package com.gathin.tvhome;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The Spotify Web API calls Glass Home's spotify_connect needs: what the account is playing on any device, and
 * play, pause, next and previous there. It signs in with a refresh token from launcher/spotify-link.py (PKCE, no client
 * secret), keeps the access token it gets for its hour, and stores the refresh token Spotify may rotate. Blocking:
 * call it off the main thread. The transport and the clock are swappable so the JVM tests can play Spotify.
 */
final class SpotifyApi {
    static final String API = "https://api.spotify.com/v1", TOKEN_URL = "https://accounts.spotify.com/api/token";

    /** An HTTP exchange: method, URL, headers, an optional form body; the status and the body come back. */
    interface Transport {
        Response send(String method, String url, Map<String, String> headers, String body) throws IOException;
    }

    static final class Response {
        final int code;
        final String body;
        /** Seconds from a Retry-After header, or 0. */
        final int retryAfter;

        Response(int code, String body, int retryAfter) {
            this.code = code;
            this.body = body;
            this.retryAfter = retryAfter;
        }
    }

    interface Clock {
        long now();
    }

    /** Where the refresh token lives between runs (the launcher's private preferences on the TV). */
    interface TokenStore {
        void saveRefreshToken(String token);
    }

    /** Why the last call gave nothing: the panel shows only songs, these are for the logs and the Mac's check. */
    enum Status { OK, NOTHING_PLAYING, NOT_LINKED, SIGNED_OUT, PREMIUM_REQUIRED, RATE_LIMITED, OFFLINE, ERROR }

    /** Spotify's addresses; a test points them at its own fake server. */
    private final String api, tokenUrl;
    private final String clientId;
    private String refreshToken;
    private final Transport transport;
    private final Clock clock;
    private final TokenStore store;
    private String accessToken;
    private long accessExpires, quietUntil;
    private Status status = Status.OK;

    SpotifyApi(String clientId, String refreshToken, Transport transport, Clock clock, TokenStore store) {
        this(API, TOKEN_URL, clientId, refreshToken, transport, clock, store);
    }

    SpotifyApi(String api, String tokenUrl, String clientId, String refreshToken, Transport transport, Clock clock,
               TokenStore store) {
        this.api = api;
        this.tokenUrl = tokenUrl;
        this.clientId = clientId;
        this.refreshToken = refreshToken;
        this.transport = transport;
        this.clock = clock;
        this.store = store;
    }

    Status status() {
        return status;
    }

    boolean linked() {
        return clientId != null && !clientId.isEmpty() && refreshToken != null && !refreshToken.isEmpty();
    }

    /** What plays on the account now, or null (see status()). */
    synchronized SpotifyPlayback playback() {
        Response r = call("GET", api + "/me/player?additional_types=episode", null);
        if (r == null) return null;
        if (r.code == 204 || r.code == 202) {
            status = Status.NOTHING_PLAYING;
            return null;
        }
        if (r.code != 200) return failed(r);
        SpotifyPlayback p = SpotifyPlayback.parse(r.body);
        status = p == null ? Status.NOTHING_PLAYING : Status.OK;
        return p;
    }

    enum Action { PLAY, PAUSE, NEXT, PREVIOUS }

    /** Sends a control to the device playing (Premium only); true when Spotify took it. */
    synchronized boolean control(Action action, String deviceId) {
        String q = deviceId == null || deviceId.isEmpty() ? "" : "?device_id=" + enc(deviceId);
        Response r;
        switch (action) {
            case PLAY: r = call("PUT", api + "/me/player/play" + q, ""); break;
            case PAUSE: r = call("PUT", api + "/me/player/pause" + q, ""); break;
            case NEXT: r = call("POST", api + "/me/player/next" + q, ""); break;
            default: r = call("POST", api + "/me/player/previous" + q, ""); break;
        }
        if (r == null) return false;
        if (r.code >= 200 && r.code < 300) {
            status = Status.OK;
            return true;
        }
        failed(r);
        return false;
    }

    private SpotifyPlayback failed(Response r) {
        if (r.code == 403) status = Status.PREMIUM_REQUIRED;
        else if (r.code == 429) {
            status = Status.RATE_LIMITED;
            quietUntil = clock.now() + Math.max(1, r.retryAfter) * 1000L;
        } else status = Status.ERROR;
        return null;
    }

    /** One API call with a fresh access token; a 401 refreshes once and tries again. Null when it could not be made. */
    private Response call(String method, String url, String body) {
        if (!linked()) {
            status = Status.NOT_LINKED;
            return null;
        }
        if (clock.now() < quietUntil) {
            status = Status.RATE_LIMITED;
            return null;
        }
        try {
            for (int attempt = 0; attempt < 2; attempt++) {
                if (accessToken == null || clock.now() >= accessExpires) {
                    if (!refresh()) return null;
                }
                Map<String, String> headers = new LinkedHashMap<>();
                headers.put("Authorization", "Bearer " + accessToken);
                // Controls go with an empty body (Content-Length: 0, which Spotify requires on PUT and POST).
                if (body != null && !body.isEmpty()) headers.put("Content-Type", "application/json");
                Response r = transport.send(method, url, headers, body);
                if (r.code != 401) return r;
                accessToken = null;   // expired early or revoked: one refresh, then give up
            }
            status = Status.SIGNED_OUT;
            return null;
        } catch (IOException e) {
            status = Status.OFFLINE;
            return null;
        }
    }

    /** Trades the refresh token for an access token; keeps a rotated refresh token. */
    private boolean refresh() throws IOException {
        String form = "grant_type=refresh_token&refresh_token=" + enc(refreshToken) + "&client_id=" + enc(clientId);
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/x-www-form-urlencoded");
        Response r = transport.send("POST", tokenUrl, headers, form);
        if (r.code == 429) {
            failed(r);
            return false;
        }
        if (r.code != 200) {
            // 400 invalid_grant: the link was revoked or the token replaced; spotify-link.py push links again.
            status = r.code == 400 ? Status.SIGNED_OUT : Status.ERROR;
            return false;
        }
        try {
            JSONObject o = new JSONObject(r.body);
            accessToken = o.getString("access_token");
            // A minute early, so a call never goes out with a token about to lapse.
            accessExpires = clock.now() + Math.max(60, o.optLong("expires_in", 3600) - 60) * 1000L;
            String rotated = o.optString("refresh_token", "");
            if (!rotated.isEmpty() && !rotated.equals(refreshToken)) {
                refreshToken = rotated;
                if (store != null) store.saveRefreshToken(rotated);
            }
            return true;
        } catch (JSONException e) {
            status = Status.ERROR;
            return false;
        }
    }

    private static String enc(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    /** The real transport, over HttpURLConnection, with short timeouts: a slow network must not hold the poller. */
    static final class UrlTransport implements Transport {
        @Override
        public Response send(String method, String url, Map<String, String> headers, String body) throws IOException {
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            try {
                c.setRequestMethod(method);
                c.setConnectTimeout(6000);
                c.setReadTimeout(8000);
                for (Map.Entry<String, String> h : headers.entrySet()) c.setRequestProperty(h.getKey(), h.getValue());
                if (body != null) {
                    c.setDoOutput(true);
                    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                    c.setFixedLengthStreamingMode(bytes.length);
                    try (OutputStream out = c.getOutputStream()) {
                        out.write(bytes);
                    }
                }
                int code = c.getResponseCode();
                InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
                String text = "";
                if (in != null) {
                    try (InputStream stream = in) {
                        ByteArrayOutputStream buf = new ByteArrayOutputStream();
                        byte[] chunk = new byte[8192];
                        for (int n; (n = stream.read(chunk)) > 0; ) buf.write(chunk, 0, n);
                        text = buf.toString("UTF-8");
                    }
                }
                int retry = 0;
                try {
                    String h = c.getHeaderField("Retry-After");
                    if (h != null) retry = Integer.parseInt(h.trim());
                } catch (NumberFormatException ignored) {
                }
                return new Response(code, text, retry);
            } finally {
                c.disconnect();
            }
        }
    }
}
