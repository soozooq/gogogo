package com.zcshou.gogogo;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class LabGmsPublishEvidenceTest {
    @Test public void lateTerminalResultReplacesUnknownWithoutDoubleCounting() {
        LabGmsPublishEvidence evidence = new LabGmsPublishEvidence();
        evidence.onTaskReturned();
        evidence.onCallbackUnavailable();
        evidence.onCallbackRecovered();
        evidence.onTaskCompleted(true);
        LabGmsPublishEvidence.Snapshot snapshot = evidence.snapshot();
        assertEquals(1L,snapshot.submitted);
        assertEquals(1L,snapshot.taskSucceeded);
        assertEquals(0L,snapshot.callbackUnavailable);
        assertEquals(0L,snapshot.pending);
    }
    @Test
    public void returnedTaskIsNotAlreadyAcknowledged() {
        LabGmsPublishEvidence evidence = new LabGmsPublishEvidence();
        evidence.onTaskReturned();
        LabGmsPublishEvidence.Snapshot s = evidence.snapshot();
        assertEquals(1L, s.submitted);
        assertEquals(0L, s.taskSucceeded);
        assertEquals(1L, s.pending);
    }

    @Test
    public void successfulTaskRemovesPendingButDoesNotProveConsumer() {
        LabGmsPublishEvidence evidence = new LabGmsPublishEvidence();
        evidence.onTaskReturned();
        evidence.onTaskCompleted(true);
        LabGmsPublishEvidence.Snapshot s = evidence.snapshot();
        assertEquals(1L, s.taskSucceeded);
        assertEquals(0L, s.pending);
    }

    @Test
    public void failedTaskDoesNotCountAsSuccess() {
        LabGmsPublishEvidence evidence = new LabGmsPublishEvidence();
        evidence.onTaskReturned();
        evidence.onTaskCompleted(false);
        LabGmsPublishEvidence.Snapshot s = evidence.snapshot();
        assertEquals(0L, s.taskSucceeded);
        assertEquals(1L, s.taskFailed);
        assertEquals(0L, s.pending);
    }

    @Test
    public void synchronousExceptionIsNotMislabelledAsSubmitted() {
        LabGmsPublishEvidence evidence = new LabGmsPublishEvidence();
        evidence.onSynchronousFailure();
        LabGmsPublishEvidence.Snapshot s = evidence.snapshot();
        assertEquals(0L, s.submitted);
        assertEquals(1L, s.synchronousFailure);
        assertEquals(0L, s.pending);
    }

    @Test
    public void observerAttachFailureIsExplicitlyUnknown() {
        LabGmsPublishEvidence evidence = new LabGmsPublishEvidence();
        evidence.onTaskReturned();
        evidence.onCallbackUnavailable();
        LabGmsPublishEvidence.Snapshot s = evidence.snapshot();
        assertEquals(1L, s.callbackUnavailable);
        assertEquals(0L, s.pending);
        assertEquals(0L, s.taskFailed);
    }

    @Test
    public void completedAndPendingAreSeparate() {
        LabGmsPublishEvidence evidence = new LabGmsPublishEvidence();
        evidence.onTaskReturned();
        evidence.onTaskReturned();
        evidence.onTaskReturned();
        evidence.onTaskCompleted(true);
        evidence.onTaskCompleted(false);
        LabGmsPublishEvidence.Snapshot s = evidence.snapshot();
        assertEquals(3L, s.submitted);
        assertEquals(1L, s.taskSucceeded);
        assertEquals(1L, s.taskFailed);
        assertEquals(1L, s.pending);
    }

    @Test
    public void emptyEvidenceMeansUnknownNotHealthy() {
        LabGmsPublishEvidence.Snapshot s = new LabGmsPublishEvidence().snapshot();
        assertEquals(0L, s.submitted);
        assertEquals(0L, s.taskSucceeded);
        assertEquals(0L, s.pending);
    }

    @Test
    public void callbacksFromOtherThreadAreSafelyCounted() throws InterruptedException {
        LabGmsPublishEvidence evidence = new LabGmsPublishEvidence();
        for (int i = 0; i < 100; i++) evidence.onTaskReturned();
        Thread worker = new Thread(() -> {
            for (int i = 0; i < 100; i++) evidence.onTaskCompleted(true);
        });
        worker.start();
        worker.join();
        LabGmsPublishEvidence.Snapshot s = evidence.snapshot();
        assertEquals(100L, s.submitted);
        assertEquals(100L, s.taskSucceeded);
        assertEquals(0L, s.pending);
    }
}
