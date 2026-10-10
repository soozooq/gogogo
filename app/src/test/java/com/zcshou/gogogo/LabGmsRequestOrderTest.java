package com.zcshou.gogogo;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LabGmsRequestOrderTest {
    @Test public void firstRequestStartsFromOne() {
        assertEquals(1L, LabGmsRequestOrder.next(0L));
    }

    @Test public void nextIdAlwaysAdvancesForNormalRequests() {
        long enable = LabGmsRequestOrder.next(3L);
        long disable = LabGmsRequestOrder.next(enable);
        assertEquals(4L, enable);
        assertEquals(5L, disable);
    }

    @Test public void currentTaskCallbackIsAccepted() {
        assertTrue(LabGmsRequestOrder.isCurrent(42L, 42L));
    }

    @Test public void enableCallbackCannotOverwriteSubsequentDisableRequest() {
        long enable = LabGmsRequestOrder.next(20L);
        long disable = LabGmsRequestOrder.next(enable);
        assertFalse(LabGmsRequestOrder.isCurrent(enable, disable));
        assertTrue(LabGmsRequestOrder.isCurrent(disable, disable));
    }

    @Test public void earlierDisableCallbackCannotOverwriteLateEnableCleanup() {
        long initialDisable = 100L;
        long secondDisable = LabGmsRequestOrder.next(initialDisable);
        assertFalse(LabGmsRequestOrder.isCurrent(initialDisable, secondDisable));
        assertTrue(LabGmsRequestOrder.isCurrent(secondDisable, secondDisable));
    }

    @Test public void missingTokenIsNeverAccepted() {
        assertFalse(LabGmsRequestOrder.isCurrent(0L, 10L));
        assertFalse(LabGmsRequestOrder.isCurrent(10L, 0L));
        assertFalse(LabGmsRequestOrder.isCurrent(-1L, -1L));
    }

    @Test public void callbacksFromOlderSessionAreIgnored() {
        long oldSessionToken = 13L;
        long nextSessionToken = LabGmsRequestOrder.next(oldSessionToken);
        assertFalse(LabGmsRequestOrder.isCurrent(oldSessionToken, nextSessionToken));
    }

    @Test public void corruptSavedCounterStartsConservatively() {
        assertEquals(1L, LabGmsRequestOrder.next(-100L));
    }

    @Test public void overflowResetsRequestCounter() {
        assertEquals(1L, LabGmsRequestOrder.next(Long.MAX_VALUE));
    }
}
