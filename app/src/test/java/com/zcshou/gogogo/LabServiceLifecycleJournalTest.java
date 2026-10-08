package com.zcshou.gogogo;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

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
    public void systemRebootDoesNotImplyShizukuKilledProcess() {
        assertEquals(LabServiceLifecycleJournal.Verdict.DEVICE_REBOOT_UNCERTAIN,
                LabServiceLifecycleJournal.classify(true, false, false, false, false));
    }
}
