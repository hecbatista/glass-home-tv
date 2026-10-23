package com.gathin.tvhome;

import org.junit.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.function.Function;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class SpotifyApiTest {
    /** Plays Spotify: scripted answers in order, and a log of every request the client made. */
    private static final class FakeSpotify implements SpotifyApi.Transport {
        final Deque<Object> answers = new ArrayDeque<>();
        final List<String> requests = new ArrayList<>();
        final List<String> bodies = new ArrayList<>();
        final List<Map<String, String>> headers = new ArrayList<>();

        FakeSpotify answer(int code, String body) { answers.add(new SpotifyApi.Response(code, body, 0)); return this; }
        FakeSpotify answer(int code, String body, int retryAfter) {
            answers.add(new SpotifyApi.Response(code, body, retryAfter));
            return this;
        }
        FakeSpotify offline() { answers.add(new IOException("no route to host")); return this; }
        FakeSpotify token(String access, String rotated) {
            return answer(200, "{\"access_token\":\"" + access + "\",\"token_type\":\"Bearer\",\"expires_in\":3600"
                    + (rotated == null ? "" : ",\"refresh_token\":\"" + rotated + "\"") + "}");
        }

        @Override
        public SpotifyApi.Response send(String method, String url, Map<String, String> h, String body) throws IOException {
            requests.add(method + " " + url);
            bodies.add(body);
            headers.add(h);
            Object next = answers.poll();
            if (next == null) throw new AssertionError("unexpected request: " + method + " " + url);
            if (next instanceof IOException) throw (IOException) next;
            return (SpotifyApi.Response) next;
        }
    }

    private long now = 1_000_000;
    private final List<String> saved = new ArrayList<>();

    private SpotifyApi api(FakeSpotify fake) {
        return new SpotifyApi("client123", "refresh-1", fake, () -> now, saved::add);
    }

    private static final String TOKEN = "POST " + SpotifyApi.TOKEN_URL;
    private static final String PLAYER = "GET " + SpotifyApi.API + "/me/player?additional_types=episode";

    @Test public void signsInOnceAndReusesTheTokenForItsHour() {
        FakeSpotify fake = new FakeSpotify().token("access-1", null)
                .answer(200, SpotifyPlaybackTest.fixture("player-track.json"))
                .answer(200, SpotifyPlaybackTest.fixture("player-track.json"));
        SpotifyApi api = api(fake);
        assertEquals("Nights", api.playback().title);
        now += 30 * 60_000;
        assertEquals("Nights", api.playback().title);
        assertEquals(List.of(TOKEN, PLAYER, PLAYER), fake.requests);
        assertEquals("grant_type=refresh_token&refresh_token=refresh-1&client_id=client123", fake.bodies.get(0));
        assertEquals("Bearer access-1", fake.headers.get(1).get("Authorization"));
        assertEquals(SpotifyApi.Status.OK, api.status());
    }

    @Test public void anExpiredTokenIsRenewedAndARotatedRefreshTokenIsKept() {
        FakeSpotify fake = new FakeSpotify().token("access-1", null).answer(204, "")
                .token("access-2", "refresh-2").answer(204, "");
        SpotifyApi api = api(fake);
        assertNull(api.playback());
        assertEquals(SpotifyApi.Status.NOTHING_PLAYING, api.status());
        now += 59 * 60_000;   // past the hour less the minute's margin
        assertNull(api.playback());
        assertEquals(List.of(TOKEN, PLAYER, TOKEN, PLAYER), fake.requests);
        assertEquals(List.of("refresh-2"), saved);
        assertEquals("Bearer access-2", fake.headers.get(3).get("Authorization"));
    }

    @Test public void a401RenewsOnceAndRetries() {
        FakeSpotify fake = new FakeSpotify().token("access-1", null).answer(401, "{\"error\":{\"status\":401}}")
                .token("access-2", null).answer(200, SpotifyPlaybackTest.fixture("player-episode.json"));
        assertEquals("Episode 212: Synths", api(fake).playback().title);
        assertEquals(List.of(TOKEN, PLAYER, TOKEN, PLAYER), fake.requests);
    }

    @Test public void twoRefusalsInARowMeanSignedOut() {
        FakeSpotify fake = new FakeSpotify().token("a", null).answer(401, "").token("b", null).answer(401, "");
        SpotifyApi api = api(fake);
        assertNull(api.playback());
        assertEquals(SpotifyApi.Status.SIGNED_OUT, api.status());
    }

    @Test public void aRevokedLinkIsSignedOut() {
        FakeSpotify fake = new FakeSpotify().answer(400, "{\"error\":\"invalid_grant\",\"error_description\":\"Refresh token revoked\"}");
        SpotifyApi api = api(fake);
        assertNull(api.playback());
        assertEquals(SpotifyApi.Status.SIGNED_OUT, api.status());
    }

    @Test public void slowDownIsHonouredWithoutCallingAgain() {
        FakeSpotify fake = new FakeSpotify().token("a", null).answer(429, "", 10)
                .answer(200, SpotifyPlaybackTest.fixture("player-track.json"));
        SpotifyApi api = api(fake);
        assertNull(api.playback());
        assertEquals(SpotifyApi.Status.RATE_LIMITED, api.status());
        now += 9_000;
        assertNull(api.playback());               // still within Retry-After: no request goes out
        assertEquals(2, fake.requests.size());
        now += 2_000;
        assertEquals("Nights", api.playback().title);
    }

    @Test public void offlineIsReportedAndTheNextPollRecovers() {
        FakeSpotify fake = new FakeSpotify().offline().token("a", null).answer(204, "");
        SpotifyApi api = api(fake);
        assertNull(api.playback());
        assertEquals(SpotifyApi.Status.OFFLINE, api.status());
        assertNull(api.playback());
        assertEquals(SpotifyApi.Status.NOTHING_PLAYING, api.status());
    }

    @Test public void controlsGoToTheDeviceThatPlays() {
        FakeSpotify fake = new FakeSpotify().token("a", null)
                .answer(204, "").answer(204, "").answer(204, "").answer(204, "");
        SpotifyApi api = api(fake);
        assertTrue(api.control(SpotifyApi.Action.PAUSE, "dev 1"));
        assertTrue(api.control(SpotifyApi.Action.PLAY, "dev 1"));
        assertTrue(api.control(SpotifyApi.Action.NEXT, "dev 1"));
        assertTrue(api.control(SpotifyApi.Action.PREVIOUS, null));
        assertEquals(List.of(TOKEN,
                "PUT " + SpotifyApi.API + "/me/player/pause?device_id=dev+1",
                "PUT " + SpotifyApi.API + "/me/player/play?device_id=dev+1",
                "POST " + SpotifyApi.API + "/me/player/next?device_id=dev+1",
                "POST " + SpotifyApi.API + "/me/player/previous"), fake.requests);
        // An empty body, so the request carries Content-Length: 0, and no JSON content type for it.
        assertEquals("", fake.bodies.get(1));
        assertFalse(fake.headers.get(1).containsKey("Content-Type"));
    }

    @Test public void controlsWithoutPremiumAreRefused() {
        FakeSpotify fake = new FakeSpotify().token("a", null)
                .answer(403, "{\"error\":{\"status\":403,\"message\":\"Player command failed: Premium required\"}}");
        SpotifyApi api = api(fake);
        assertFalse(api.control(SpotifyApi.Action.NEXT, "d"));
        assertEquals(SpotifyApi.Status.PREMIUM_REQUIRED, api.status());
    }

    @Test public void withoutALinkNothingIsAsked() {
        FakeSpotify fake = new FakeSpotify();
        SpotifyApi api = new SpotifyApi("client123", "", fake, () -> now, saved::add);
        assertNull(api.playback());
        assertEquals(SpotifyApi.Status.NOT_LINKED, api.status());
        assertTrue(fake.requests.isEmpty());
    }

    /** A request as the local fake Spotify received it. */
    private static final class Request {
        String method, path, body;
        final Map<String, String> headers = new LinkedHashMap<>();
    }

    /** An answer from the local fake Spotify: status, extra headers, body (null for none). */
    private static final class Reply {
        final int code;
        final String body;
        final Map<String, String> headers = new LinkedHashMap<>();

        Reply(int code, String body) {
            this.code = code;
            this.body = body;
        }
    }

    /**
     * A tiny HTTP/1.1 server on a loopback port, one connection per request (Connection: close). The JDK's own
     * com.sun.net.httpserver is not on Android's test classpath; plain sockets are.
     */
    private static final class LocalServer implements AutoCloseable {
        final ServerSocket socket = new ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"));
        final Thread thread;

        LocalServer(Function<Request, Reply> handler) throws IOException {
            thread = new Thread(() -> {
                while (!socket.isClosed()) {
                    try (Socket c = socket.accept()) {
                        serve(c, handler);
                    } catch (IOException e) {
                        return;
                    }
                }
            });
            thread.setDaemon(true);
            thread.start();
        }

        String base() {
            return "http://127.0.0.1:" + socket.getLocalPort();
        }

        private static void serve(Socket c, Function<Request, Reply> handler) throws IOException {
            InputStream in = c.getInputStream();
            BufferedReader lines = new BufferedReader(new InputStreamReader(in, StandardCharsets.ISO_8859_1));
            String start = lines.readLine();
            if (start == null) return;
            Request r = new Request();
            String[] parts = start.split(" ");
            r.method = parts[0];
            r.path = parts[1].split("\\?")[0];
            for (String h; (h = lines.readLine()) != null && !h.isEmpty(); ) {
                int colon = h.indexOf(':');
                r.headers.put(h.substring(0, colon).trim().toLowerCase(), h.substring(colon + 1).trim());
            }
            int length = Integer.parseInt(r.headers.getOrDefault("content-length", "0"));
            char[] body = new char[length];
            for (int read = 0; read < length; ) read += lines.read(body, read, length - read);
            r.body = new String(body);
            Reply reply = handler.apply(r);
            byte[] bytes = reply.body == null ? new byte[0] : reply.body.getBytes(StandardCharsets.UTF_8);
            StringBuilder head = new StringBuilder("HTTP/1.1 " + reply.code + " X\r\n");
            for (Map.Entry<String, String> h : reply.headers.entrySet()) {
                head.append(h.getKey()).append(": ").append(h.getValue()).append("\r\n");
            }
            if (reply.code != 204) head.append("Content-Type: application/json\r\nContent-Length: ").append(bytes.length).append("\r\n");
            head.append("Connection: close\r\n\r\n");
            OutputStream out = c.getOutputStream();
            out.write(head.toString().getBytes(StandardCharsets.ISO_8859_1));
            if (reply.code != 204) out.write(bytes);
            out.flush();
        }

        @Override
        public void close() throws IOException {
            socket.close();
        }
    }

    /**
     * The real transport over real HTTP, against a fake Spotify on this machine: the same requests the TV makes,
     * through HttpURLConnection (form posts, empty PUTs, error bodies, Retry-After, 204s).
     */
    @Test public void theRealTransportAgainstALocalFakeSpotify() throws Exception {
        List<String> seen = new ArrayList<>();
        int[] playerCalls = {0};
        try (LocalServer server = new LocalServer(r -> {
            if (r.path.equals("/api/token")) {
                seen.add("token " + r.body);
                return new Reply(200, "{\"access_token\":\"live-access\",\"expires_in\":3600,\"refresh_token\":\"refresh-2\"}");
            }
            String length = r.headers.get("content-length");
            seen.add(r.method + " " + r.path + " " + r.headers.get("authorization") + (length == null ? "" : " len=" + length));
            if (!r.path.equals("/v1/me/player")) return new Reply(204, null);
            playerCalls[0]++;
            if (playerCalls[0] == 1) return new Reply(200, SpotifyPlaybackTest.fixture("player-track.json"));
            if (playerCalls[0] == 2) {
                Reply slow = new Reply(429, "{\"error\":{\"status\":429,\"message\":\"API rate limit exceeded\"}}");
                slow.headers.put("Retry-After", "7");
                return slow;
            }
            return new Reply(204, null);
        })) {
            String base = server.base();
            SpotifyApi api = new SpotifyApi(base + "/v1", base + "/api/token", "client123", "refresh-1",
                    new SpotifyApi.UrlTransport(), () -> now, saved::add);
            SpotifyPlayback p = api.playback();
            assertEquals("Nights", p.title);
            assertEquals("Frank Ocean", p.subtitle());
            assertTrue(api.control(SpotifyApi.Action.PAUSE, p.deviceId));
            assertNull(api.playback());
            assertEquals(SpotifyApi.Status.RATE_LIMITED, api.status());
            now += 8_000;
            assertNull(api.playback());
            assertEquals(SpotifyApi.Status.NOTHING_PLAYING, api.status());
            assertEquals(List.of("refresh-2"), saved);
            assertEquals("token grant_type=refresh_token&refresh_token=refresh-1&client_id=client123", seen.get(0));
            assertEquals("GET /v1/me/player Bearer live-access", seen.get(1));
            assertEquals("PUT /v1/me/player/pause Bearer live-access len=0", seen.get(2));
        }
    }
}
