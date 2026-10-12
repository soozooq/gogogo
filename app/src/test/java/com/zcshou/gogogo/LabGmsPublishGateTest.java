package com.zcshou.gogogo;

import org.junit.Test;

import static org.junit.Assert.*;

public class LabGmsPublishGateTest {
    @Test public void asynchronousRequestsNeverPileUp() {
        LabGmsPublishGate gate = new LabGmsPublishGate();
        long first = gate.begin(100L);
        assertTrue(first > 0L);
        for (int i = 0; i < 500; i++) assertEquals(0L, gate.begin(110L + i));
        assertEquals(1L, gate.submitted());
        assertEquals(500L, gate.coalesced());
        assertEquals(510L, gate.pendingAgeMs(610L));
        assertTrue(gate.complete(first, true, 640L));
        assertEquals(1L, gate.succeeded());
        assertEquals(10L, gate.lastSuccessAgeMs(650L));
        assertTrue(gate.begin(651L) > first);
    }

    @Test public void callbackCannotReleaseWrongOrStoppedSession() {
        LabGmsPublishGate gate = new LabGmsPublishGate();
        long first = gate.begin(20L);
        assertFalse(gate.complete(first + 1, true, 21L));
        assertTrue(gate.pending());
        assertTrue(gate.complete(first, false, 30L));
        assertEquals(1L, gate.failed());
        long next = gate.begin(31L);
        gate.close();
        assertFalse(gate.complete(next, true, 40L));
        assertEquals(0L, gate.begin(41L));
        assertEquals(0L, gate.succeeded());
    }

    @Test public void failuresFreeTheSlotForLatestPosition() {
        LabGmsPublishGate gate = new LabGmsPublishGate();
        long first = gate.begin(1L);
        assertTrue(gate.complete(first, false, 2L));
        long second = gate.begin(3L);
        assertTrue(second > first);
        assertTrue(gate.complete(second, true, 4L));
        assertEquals(2L, gate.submitted());
        assertEquals(1L, gate.failed());
        assertEquals(1L, gate.succeeded());
    }
}
