package com.soozooq.tianwaitian.fixture;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class GuestUiV1Test {
    @Test public void counterLabelUsesRestoredValue() {
        assertEquals("来自独立 APK 的点击计数：42", GuestUiV1.counterLabel(42));
    }

    @Test public void zeroClicksIsValid() {
        assertEquals(0, GuestUiV1.requireValidClicks(0));
    }

    @Test public void positiveRestoredClicksRemainUnchanged() {
        assertEquals(345, GuestUiV1.requireValidClicks(345));
    }

    @Test(expected = IllegalArgumentException.class)
    public void negativeRestoredStateRejected() {
        GuestUiV1.requireValidClicks(-1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void unreasonablyLargeRestoredStateRejected() {
        GuestUiV1.requireValidClicks(100001);
    }
}
