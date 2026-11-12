package io.github.hecbatista.glasshome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class HomeAwakeTest {
    @Test public void theTvsChoiceWinsOverTheTheme() {
        assertEquals(60, HomeAwake.chosen(60, 30));
        assertEquals(HomeAwake.ALWAYS, HomeAwake.chosen(HomeAwake.ALWAYS, 0));
        assertEquals(0, HomeAwake.chosen(0, 120));
    }

    @Test public void nothingSavedFollowsTheTheme() {
        assertEquals(30, HomeAwake.chosen(Integer.MIN_VALUE, 30));
        assertEquals(0, HomeAwake.chosen(Integer.MIN_VALUE, 0));
    }

    @Test public void outOfRangeValuesFallBackToTheTv() {
        assertEquals(0, HomeAwake.chosen(-5, 99_999));
        assertEquals(120, HomeAwake.chosen(25 * 60, 120));
    }
}
