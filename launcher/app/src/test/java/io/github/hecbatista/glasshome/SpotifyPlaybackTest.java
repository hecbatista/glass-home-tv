package io.github.hecbatista.glasshome;

import org.json.JSONArray;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class SpotifyPlaybackTest {
    static String fixture(String name) {
        try (InputStream in = SpotifyPlaybackTest.class.getResourceAsStream("/spotify/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    @Test public void aSongOnAPhone() {
        SpotifyPlayback p = SpotifyPlayback.parse(fixture("player-track.json"));
        assertTrue(p.playing);
        assertEquals("Nights", p.title);
        assertEquals("Frank Ocean", p.artist);
        assertEquals("iPhone", p.device);
        assertEquals("5fbb3ba6aa454b5534c4ba43a8c7e8e45a63ad0e", p.deviceId);
        // The 300px cover: big enough for the panel's 91dp art at 2x, a fifth of the 640px one's bytes.
        assertEquals("https://i.scdn.co/image/ab67616d00001e02c5649add07ed3720be9d5526", p.artUrl);
        assertEquals("Frank Ocean", p.subtitle());
    }

    @Test public void aPausedPodcastOnAMacShowsItsShowAndEpisodeArt() {
        SpotifyPlayback p = SpotifyPlayback.parse(fixture("player-episode.json"));
        assertFalse(p.playing);
        assertEquals("Episode 212: Synths", p.title);
        assertEquals("Song Exploder", p.artist);
        assertEquals("https://i.scdn.co/image/episode640", p.artUrl);
        assertEquals("Song Exploder", p.subtitle());
    }

    @Test public void anAdNothingAndBrokenAnswersShowNothing() {
        assertNull(SpotifyPlayback.parse(fixture("player-ad.json")));
        assertNull(SpotifyPlayback.parse(""));
        assertNull(SpotifyPlayback.parse(null));
        assertNull(SpotifyPlayback.parse("{not json"));
        assertNull(SpotifyPlayback.parse("{\"is_playing\":true,\"item\":{\"name\":\"\"}}"));
    }

    @Test public void severalArtistsAreJoined() {
        SpotifyPlayback p = SpotifyPlayback.parse("{\"is_playing\":true,\"device\":{\"name\":\"Kitchen\"},"
                + "\"item\":{\"id\":\"x\",\"name\":\"Solar Eclipse\",\"artists\":[{\"name\":\"Drake\"},"
                + "{\"name\":\"Don Toliver\"}],\"album\":{\"images\":[]}}}");
        assertEquals("Drake, Don Toliver", p.artist);
        assertNull(p.artUrl);
        assertEquals("Drake, Don Toliver", p.subtitle());
    }

    @Test public void withoutAnArtistTheDeviceNamesTheLine() {
        SpotifyPlayback p = SpotifyPlayback.parse("{\"is_playing\":true,\"device\":{\"name\":\"Kitchen\"},"
                + "\"item\":{\"id\":\"x\",\"name\":\"Untitled\",\"artists\":[],\"album\":{\"images\":[]}}}");
        assertEquals("Kitchen", p.subtitle());
    }

    @Test public void eachDeviceGetsItsIcon() {
        // The fixtures: an iPhone (Smartphone) and a MacBook Pro (Computer).
        assertEquals("smartphone", SpotifyPlayback.parse(fixture("player-track.json")).sourceIcon());
        assertEquals("laptop_mac", SpotifyPlayback.parse(fixture("player-episode.json")).sourceIcon());
        // This account's devices as Spotify lists them: an Echo Show reports itself as a Speaker.
        assertEquals("smart_display", SpotifyPlayback.sourceIcon("Speaker", "Kitchen Echo Show"));
        assertEquals("tv", SpotifyPlayback.sourceIcon("TV", "Bedroom Fire TV"));
        assertEquals("smartphone", SpotifyPlayback.sourceIcon("Smartphone", "iPhone"));
        assertEquals("speaker", SpotifyPlayback.sourceIcon("Speaker", "Kitchen Echo Dot"));
        assertEquals("speaker", SpotifyPlayback.sourceIcon("Unknown", "Echo Pop"));
        assertEquals("tablet_mac", SpotifyPlayback.sourceIcon("Tablet", "iPad"));
        assertEquals("tv", SpotifyPlayback.sourceIcon("CastVideo", "Living Room"));
        assertEquals("directions_car", SpotifyPlayback.sourceIcon("Automobile", "Car"));
        assertEquals("sports_esports", SpotifyPlayback.sourceIcon("GameConsole", "PS5"));
        assertEquals("devices", SpotifyPlayback.sourceIcon("", ""));
        assertEquals("devices", SpotifyPlayback.sourceIcon(null, null));
    }

    @Test public void theCoverIsTheSmallestBigEnoughElseTheBiggest() throws Exception {
        JSONArray images = new JSONArray("[{\"url\":\"a\",\"width\":640},{\"url\":\"b\",\"width\":300},"
                + "{\"url\":\"c\",\"width\":64}]");
        assertEquals("b", SpotifyPlayback.pickImage(images, 300));
        assertEquals("a", SpotifyPlayback.pickImage(images, 301));
        assertEquals("a", SpotifyPlayback.pickImage(new JSONArray("[{\"url\":\"a\",\"width\":640}]"), 1000));
        assertNull(SpotifyPlayback.pickImage(new JSONArray("[]"), 300));
    }
}
