package com.zcshou.gogogo;

import org.junit.Test;
import static org.junit.Assert.*;

public class LabProviderEvidencePolicyTest {
    @Test public void removeReturnedMeansApiAcceptedNotConsumerProof() {
        assertTrue(LabProviderEvidencePolicy.mayReportClean(
                LabProviderEvidencePolicy.CleanupOutcome.REMOVE_RETURNED));
    }
    @Test public void missingTestProviderMayBeReportedClean() {
        assertTrue(LabProviderEvidencePolicy.mayReportClean(
                LabProviderEvidencePolicy.CleanupOutcome.ALREADY_ABSENT));
    }
    @Test public void deniedRemovalRemainsUncertain() {
        assertFalse(LabProviderEvidencePolicy.mayReportClean(
                LabProviderEvidencePolicy.CleanupOutcome.SECURITY_DENIED));
        assertTrue(LabProviderEvidencePolicy.isUncertain(
                LabProviderEvidencePolicy.CleanupOutcome.SECURITY_DENIED));
    }
    @Test public void processDeathMidSweepRemainsUncertain() {
        assertTrue(LabProviderEvidencePolicy.isUncertain(
                LabProviderEvidencePolicy.CleanupOutcome.PENDING));
    }
    @Test public void arbitraryExceptionDoesNotImplySuccessfulCleanup() {
        assertFalse(LabProviderEvidencePolicy.mayReportClean(
                LabProviderEvidencePolicy.CleanupOutcome.OTHER_FAILURE));
    }
    @Test public void appOpsRestoreRetriesOnlyWhenIdleAndOrphaned() {
        assertTrue(LabProviderEvidencePolicy.shouldRetryAfterAppOps(true,false,true));
        assertFalse(LabProviderEvidencePolicy.shouldRetryAfterAppOps(false,false,true));
        assertFalse(LabProviderEvidencePolicy.shouldRetryAfterAppOps(true,true,true));
        assertFalse(LabProviderEvidencePolicy.shouldRetryAfterAppOps(true,false,false));
    }
}
