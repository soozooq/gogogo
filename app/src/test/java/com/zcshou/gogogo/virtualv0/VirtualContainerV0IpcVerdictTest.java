package com.zcshou.gogogo.virtualv0;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class VirtualContainerV0IpcVerdictTest {
    @Test public void acceptsDifferentPidWithSameUid() {
        assertTrue(VirtualContainerV0IpcVerdict.passed(1200, 10123, 1300, 10123));
    }

    @Test public void rejectsSamePidEvenIfUidMatches() {
        assertFalse(VirtualContainerV0IpcVerdict.passed(1200, 10123, 1200, 10123));
    }

    @Test public void rejectsForeignUidEvenIfPidDiffers() {
        assertFalse(VirtualContainerV0IpcVerdict.passed(1200, 10123, 1300, 10124));
    }

    @Test public void rejectsUnavailableProcessIdentity() {
        assertFalse(VirtualContainerV0IpcVerdict.passed(0, 10123, 1300, 10123));
        assertFalse(VirtualContainerV0IpcVerdict.passed(1200, 10123, -1, 10123));
        assertFalse(VirtualContainerV0IpcVerdict.passed(1200, -1, 1300, -1));
    }
}
