package com.zcshou.gogogo;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Pure state-machine tests: no Android or Shizuku service needed. */
public class LabServiceLifecycleJournalTest {

    @Test
    public void emptyHistoryIsNotAnInterruption() {
        assertEquals(LabServiceLifecycleJournal.Verdict.NO_RECORD,
                LabServiceLifecycleJournal.classify(false, false, false, false, true));
    }

    @Test
    public void cleanStopRemainsCleanAfterProcessRestart() {
        assertEquals(LabServiceLifecycleJournal.Verdict.NORMAL_STOP,
                LabServiceLifecycleJournal.classify(true, true, false, false, true));
    }

    @Test
    public void activeRecordMatchesCurrentService() {
        assertEquals(LabServiceLifecycleJournal.Verdict.RUNNING_IN_THIS_PROCESS,
                LabServiceLifecycleJournal.classify(true, false, true, true, true));
    }

    @Test
    public void previousProcessDisappearedWithoutStop() {
        assertEquals(LabServiceLifecycleJournal.Verdict.INTERRUPTED_OR_STALE,
                LabServiceLifecycleJournal.classify(true, false, false, false, true));
    }

    @Test
    public void serviceMissingInSameProcessIsNotTreatedAsHealthy() {
        assertEquals(LabServiceLifecycleJournal.Verdict.INTERRUPTED_OR_STALE,
                LabServiceLifecycleJournal.classify(true, false, true, false, true));
    }

    @Test
    public void sameBootCountAndIncreasingUptimeIsSameBoot() {
        assertTrue(LabServiceLifecycleJournal.isSameBoot(9, 9,
                1_700_000_010_000L, 10_000L,
                1_700_000_060_000L, 60_000L));
    }

    @Test
    public void rebootWithLongerUptimeIsNotMistakenForSameBoot() {
        assertFalse(LabServiceLifecycleJournal.isSameBoot(9, 10,
                1_700_000_010_000L, 10_000L,
                1_700_100_060_000L, 60_000L));
    }

    @Test
    public void uptimeRollbackIsNotSameBootEvenIfCounterUnchanged() {
        assertFalse(LabServiceLifecycleJournal.isSameBoot(9, 9,
                1_700_000_060_000L, 60_000L,
                1_700_100_010_000L, 10_000L));
    }

    @Test
    public void legacyRecordFallsBackToBootEpoch() {
        assertTrue(LabServiceLifecycleJournal.isSameBoot(-1, 9,
                1_700_000_010_000L, 10_000L,
                1_700_000_060_000L, 60_000L));
        assertFalse(LabServiceLifecycleJournal.isSameBoot(-1, 9,
                1_700_000_010_000L, 10_000L,
                1_700_100_060_000L, 60_000L));
    }

    @Test
    public void largeClockAdjustmentIsClassifiedAsUncertainInFallback() {
        assertFalse(LabServiceLifecycleJournal.isSameBoot(-1, -1,
                1_700_000_010_000L, 10_000L,
                1_700_001_260_000L, 60_000L));
    }

    @Test
    public void missingLegacyEpochRemainsUncertain() {
        assertFalse(LabServiceLifecycleJournal.isSameBoot(-1, -1,
                0L, 10_000L,
                1_700_000_060_000L, 60_000L));
    }

    @Test
    public void systemRebootDoesNotImplyShizukuKilledProcess() {
        assertEquals(LabServiceLifecycleJournal.Verdict.DEVICE_REBOOT_UNCERTAIN,
                LabServiceLifecycleJournal.classify(true, false, false, false, false));
    }
}
