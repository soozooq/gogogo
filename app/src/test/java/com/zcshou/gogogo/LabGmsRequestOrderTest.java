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

    @Test public void recoveryReportShowsLateCallbacksWithoutRawEventIdentifiers() {
        LabRecoveryTriage.Snapshot snapshot = new LabRecoveryTriage.Snapshot(
                LabRecoveryTriage.Lifecycle.NORMAL_STOP, false,
                java.util.Collections.emptyList(), "DISABLE_REQUESTED_STOP",
                LabEvidenceTimeWindow.Relation.AFTER_START, 3L);
        String report = LabRecoveryTriage.render(snapshot);
        assertTrue(report.contains("已忽略 3 次过期 GMS 异步回调"));
        assertTrue(report.contains("不能证明系统状态"));
        assertFalse(report.contains("请求编号:"));
    }

    @Test public void missingLateEventsDoNotClutterRecoveryReport() {
        LabRecoveryTriage.Snapshot snapshot = new LabRecoveryTriage.Snapshot(
                LabRecoveryTriage.Lifecycle.NO_RECORD, false,
                java.util.Collections.emptyList(), null,
                LabEvidenceTimeWindow.Relation.UNKNOWN, 0L);
        assertFalse(LabRecoveryTriage.render(snapshot).contains("过期 GMS 异步回调"));
    }

    @Test public void overflowResetsRequestCounter() {
        assertEquals(1L, LabGmsRequestOrder.next(Long.MAX_VALUE));
    }
}
