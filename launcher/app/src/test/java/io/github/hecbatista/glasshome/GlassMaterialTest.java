package io.github.hecbatista.glasshome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/** The glass colour, against the values the emulation page fitted to Apple's renders (its own formula, in Python). */
public class GlassMaterialTest {
    private static String rgb(int c) {
        return ((c >> 16) & 0xFF) + "," + ((c >> 8) & 0xFF) + "," + (c & 0xFF);
    }

    @Test public void darkGlass() {
        assertEquals("40,40,45", rgb(GlassMaterial.vib(0xFF0B0B0F, 0.12f, 1.30f)));    // over the Dark background
        assertEquals("255,26,39", rgb(GlassMaterial.vib(0xFFE50914, 0.12f, 1.30f)));   // over Netflix's red glow
    }

    @Test public void lightGlassKeepsTheGlowsColour() {
        assertEquals("246,248,252", rgb(GlassMaterial.vib(0xFFECEEF3, 0.56f, 1.90f)));
        assertEquals("255,128,137", rgb(GlassMaterial.vib(0xFFE50914, 0.56f, 1.90f)));  // pink, as on Apple's Light glass
    }
}
