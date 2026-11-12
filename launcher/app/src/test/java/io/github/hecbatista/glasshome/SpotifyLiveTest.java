package io.github.hecbatista.glasshome;

import org.json.JSONObject;
import org.junit.Assume;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertNotEquals;

/**
 * The TV's Spotify client against the real Spotify, with the Mac's link from `spotify-link.py login`. Skipped unless
 * SPOTIFY_LIVE=1, so ordinary test runs never go to the network:
 *
 *     SPOTIFY_LIVE=1 ./gradlew testReleaseUnitTest --tests io.github.hecbatista.glasshome.SpotifyLiveTest
 *
 * A refresh token Spotify rotates is written back to ~/.cleanyourtv/spotify.json, so the Mac's link keeps working.
 */
public class SpotifyLiveTest {
    @Test public void whatTheAccountPlaysNow() throws Exception {
        Assume.assumeTrue("set SPOTIFY_LIVE=1 to run against the real Spotify", "1".equals(System.getenv("SPOTIFY_LIVE")));
        Path file = Paths.get(System.getProperty("user.home"), ".cleanyourtv", "spotify.json");
        Assume.assumeTrue("run spotify-link.py login first", Files.exists(file));
        JSONObject link = new JSONObject(new String(Files.readAllBytes(file), StandardCharsets.UTF_8));
        SpotifyApi api = new SpotifyApi(link.getString("client_id"), link.getString("refresh_token"),
                new SpotifyApi.UrlTransport(), System::currentTimeMillis, rotated -> {
                    try {
                        link.put("refresh_token", rotated);
                        Files.write(file, link.toString(2).getBytes(StandardCharsets.UTF_8));
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    }
                });
        SpotifyPlayback p = api.playback();
        System.out.println("status: " + api.status());
        if (p != null) {
            System.out.println((p.playing ? "Playing" : "Paused") + "\n  " + p.title + "\n  " + p.subtitle()
                    + "\n  cover: " + p.artUrl + "\n  device id present: " + !p.deviceId.isEmpty());
        }
        assertNotEquals("the link was refused; run spotify-link.py login again", SpotifyApi.Status.SIGNED_OUT, api.status());
        assertNotEquals(SpotifyApi.Status.NOT_LINKED, api.status());
    }
}
