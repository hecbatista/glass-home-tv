package io.github.hecbatista.glasshome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class PausedCoverTest {
    @Test
    public void playingIsFullSizeAndUndimmed() {
        assertEquals(1f, PausedCover.scaleAt(0f), 1e-6);
        assertEquals(0f, PausedCover.dimAt(0f), 1e-6);
    }

    @Test
    public void pausedShrinksAndDims() {
        assertEquals(PausedCover.SCALE, PausedCover.scaleAt(1f), 1e-6);
        assertEquals(PausedCover.DIM, PausedCover.dimAt(1f), 1e-6);
    }

    @Test
    public void theBounceBackGrowsPastFullSizeWithoutBrightening() {
        assertEquals(1f + (1f - PausedCover.SCALE) * 0.1f, PausedCover.scaleAt(-0.1f), 1e-6);
        assertEquals(0f, PausedCover.dimAt(-0.1f), 1e-6);
    }

    @Test
    public void theDimFadesTowardTheSurface() {
        assertEquals(0xFF000000, PausedCover.dimColour(false));
        assertEquals(0xFFECEEF3, PausedCover.dimColour(true));
    }
}
