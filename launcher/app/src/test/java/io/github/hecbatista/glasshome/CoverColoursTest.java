package io.github.hecbatista.glasshome;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class CoverColoursTest {
    private static int[] fill(int... colours) {
        int[] px = new int[24 * 24];
        for (int i = 0; i < px.length; i++) px[i] = 0xFF000000 | colours[i * colours.length / px.length];
        return px;
    }

    private static float hue(int c) {
        return CoverColours.hsv((c >> 16) & 0xFF, (c >> 8) & 0xFF, c & 0xFF)[0];
    }

    private static float value(int c) {
        return CoverColours.hsv((c >> 16) & 0xFF, (c >> 8) & 0xFF, c & 0xFF)[2];
    }

    @Test public void theTwoStrongestHuesLightTheFields() {
        int[] c = CoverColours.pick(fill(0xD02040, 0xD02040, 0xD02040, 0x2040D0));
        assertTrue("red first: " + hue(c[0]), hue(c[0]) > 330 || hue(c[0]) < 20);
        assertTrue("blue second: " + hue(c[1]), hue(c[1]) > 200 && hue(c[1]) < 250);
    }

    @Test public void aDarkCoverIsLiftedAndTheBaseStaysDark() {
        int[] c = CoverColours.pick(fill(0x301010, 0x101030));
        assertTrue(value(c[0]) >= 0.74f);
        assertTrue(value(c[3]) <= 0.13f);
    }

    @Test public void aGreyCoverStaysGrey() {
        int[] c = CoverColours.pick(fill(0x808080, 0x202020, 0xE0E0E0));
        for (int colour : c) assertEquals(0f, CoverColours.hsv((colour >> 16) & 0xFF, (colour >> 8) & 0xFF, colour & 0xFF)[1], 0.02f);
    }

    @Test public void everyColourIsOpaque() {
        for (int colour : CoverColours.pick(fill(0x12AB34))) assertEquals(0xFF, colour >>> 24);
        assertEquals(4, CoverColours.NONE.length);
        assertTrue(Arrays.stream(CoverColours.NONE).allMatch(x -> x >>> 24 == 0xFF));
    }

    @Test public void hsvRoundTrips() {
        for (int c : new int[]{0xFF0000, 0x00FF00, 0x0000FF, 0x7F3FBF, 0xFFFFFF, 0x000000}) {
            float[] h = CoverColours.hsv((c >> 16) & 0xFF, (c >> 8) & 0xFF, c & 0xFF);
            assertEquals(Integer.toHexString(c), c, CoverColours.fromHsv(h[0], h[1], h[2]));
        }
    }
}
