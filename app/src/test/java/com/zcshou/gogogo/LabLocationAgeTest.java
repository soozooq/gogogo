package com.zcshou.gogogo;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LabLocationAgeTest {
    @Test
    public void freshLocationIsTimedFromFixNotCallback() {
        assertEquals(600000L,
                LabLocationAge.fixAgeMs(900_000_000_000L, 300_000_000_000L));
        assertEquals(0L, LabLocationAge.callbackAgeMs(10000L, 10000L));
        assertFalse(LabLocationAge.isRecent(600000L, 30000L));
    }

    @Test
    public void zeroTimestampMeansUnknownNotFresh() {
        assertEquals(-1L, LabLocationAge.fixAgeMs(900_000_000L, 0L));
        assertFalse(LabLocationAge.isRecent(-1L, 1000L));
    }

    @Test
    public void futureFixTimestampMeansUnknownNotZeroAge() {
        assertEquals(-1L, LabLocationAge.fixAgeMs(1_000_000_000L, 1_100_000_000L));
        assertEquals(-1L, LabLocationAge.callbackAgeMs(1000L, 1001L));
    }

    @Test
    public void rebootOrInvalidClocksCannotBecomeFresh() {
        assertEquals(-1L, LabLocationAge.fixAgeMs(0L, 100L));
        assertEquals(-1L, LabLocationAge.fixAgeMs(-1L, 100L));
        assertEquals(-1L, LabLocationAge.callbackAgeMs(300L, -1L));
    }

    @Test
    public void thresholdsIncludeTheirBoundary() {
        assertTrue(LabLocationAge.isRecent(1000L, 1000L));
        assertFalse(LabLocationAge.isRecent(1001L, 1000L));
        assertEquals(1000L,
                LabLocationAge.fixAgeMs(1_000_000_001L, 1L));
    }
}
