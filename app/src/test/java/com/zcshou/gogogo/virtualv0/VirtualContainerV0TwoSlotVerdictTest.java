package com.zcshou.gogogo.virtualv0;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class VirtualContainerV0TwoSlotVerdictTest {
    private static boolean verify(int hostPid, int hostUid, int aPid, int aUid,
            int bPid, int bUid, String expectedA, String actualA,
            String expectedB, String actualB) {
        return VirtualContainerV0TwoSlotVerdict.passed(hostPid, hostUid,
                aPid, aUid, bPid, bUid,
                expectedA, actualA, expectedB, actualB);
    }

    @Test public void acceptsTwoBoundProcessesWithIndependentState() {
        assertTrue(verify(100, 11000, 200, 11000, 300, 11000, "A_one", "A_one", "B_two", "B_two"));
    }

    @Test public void rejectsSameProcessForBothSlots() {
        assertFalse(verify(100, 11000, 200, 11000, 200, 11000, "A_one", "A_one", "B_two", "B_two"));
    }

    @Test public void rejectsHostProcessPretendingToBeSlot() {
        assertFalse(verify(100, 11000, 100, 11000, 300, 11000, "A_one", "A_one", "B_two", "B_two"));
    }

    @Test public void rejectsWrongUid() {
        assertFalse(verify(100, 11000, 200, 11001, 300, 11000, "A_one", "A_one", "B_two", "B_two"));
    }

    @Test public void rejectsCrossSlotDataLeak() {
        assertFalse(verify(100, 11000, 200, 11000, 300, 11000, "A_one", "B_two", "B_two", "B_two"));
    }

    @Test public void rejectsSameExpectedStateInBothSlots() {
        assertFalse(verify(100, 11000, 200, 11000, 300, 11000, "same", "same", "same", "same"));
    }

    @Test public void rejectsMissingValues() {
        assertFalse(verify(100, 11000, 200, 11000, 300, 11000, "A_one", null, "B_two", "B_two"));
        assertFalse(verify(0, 11000, 200, 11000, 300, 11000, "A_one", "A_one", "B_two", "B_two"));
    }
}
