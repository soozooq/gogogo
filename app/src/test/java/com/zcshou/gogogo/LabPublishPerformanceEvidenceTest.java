package com.zcshou.gogogo;

import org.junit.Test;
import static org.junit.Assert.*;

public class LabPublishPerformanceEvidenceTest {
    @Test public void noObservationsAreUnknownRatherThanThirtyHertz() {
        LabPublishPerformanceEvidence.Snapshot s =
                new LabPublishPerformanceEvidence().snapshot();
        assertTrue(Double.isNaN(s.measuredTickHz()));
        assertTrue(Double.isNaN(s.meanTaskLatencyMs()));
        assertEquals(0L, s.pendingTasks);
        assertTrue(s.render().contains("UNKNOWN"));
    }

    @Test public void realTickIntervalsIncludeProcessingDelay() {
        LabPublishPerformanceEvidence e = new LabPublishPerformanceEvidence();
        e.onTick(100L);
        e.onTick(145L);
        e.onTick(195L);
        LabPublishPerformanceEvidence.Snapshot s = e.snapshot();
        assertEquals(3L, s.ticks);
        assertEquals(2L, s.tickIntervals);
        assertEquals(95L, s.tickTotalMs);
        assertEquals(50L, s.maxTickIntervalMs);
        assertEquals(2000.0 / 95.0, s.measuredTickHz(), 0.0001);
        assertEquals(0L, s.intervalsOver50Ms);
    }

    @Test public void longDelaysAreCountedNotSmoothedAway() {
        LabPublishPerformanceEvidence e = new LabPublishPerformanceEvidence();
        e.onTick(0); e.onTick(52); e.onTick(210);
        LabPublishPerformanceEvidence.Snapshot s = e.snapshot();
        assertEquals(2L, s.intervalsOver50Ms);
        assertEquals(1L, s.intervalsOver100Ms);
        assertEquals(158L, s.maxTickIntervalMs);
    }

    @Test public void taskReturnIsNotCompletion() {
        LabPublishPerformanceEvidence e = new LabPublishPerformanceEvidence();
        e.onTaskReturned();
        LabPublishPerformanceEvidence.Snapshot s=e.snapshot();
        assertEquals(1L,s.taskReturned);
        assertEquals(1L,s.pendingTasks);
        assertEquals(0L,s.taskFinished);
        assertTrue(Double.isNaN(s.meanTaskLatencyMs()));
    }

    @Test public void asyncLatencyAndPendingAreMeasuredIndependently() {
        LabPublishPerformanceEvidence e=new LabPublishPerformanceEvidence();
        e.onTaskReturned();e.onTaskReturned();
        e.onTaskFinished(true,100,145);
        e.onTaskFinished(false,103,209);
        LabPublishPerformanceEvidence.Snapshot s=e.snapshot();
        assertEquals(2L,s.taskReturned);
        assertEquals(2L,s.taskFinished);
        assertEquals(1L,s.taskFailed);
        assertEquals(2L,s.peakPendingTasks);
        assertEquals(0L,s.pendingTasks);
        assertEquals(75.5,s.meanTaskLatencyMs(),0.001);
        assertEquals(106L,s.maxTaskLatencyMs);
        assertEquals(1L,s.taskLatenciesOver100Ms);
    }

    @Test public void observerUnknownIsNotFailure() {
        LabPublishPerformanceEvidence e=new LabPublishPerformanceEvidence();
        e.onTaskReturned();e.onCallbackUnavailable();
        LabPublishPerformanceEvidence.Snapshot s=e.snapshot();
        assertEquals(1L,s.taskObserverUnknown);
        assertEquals(0L,s.taskFailed);
        assertEquals(0L,s.pendingTasks);
        assertEquals(0L,s.taskFinished);
    }

    @Test public void invalidCompletionClockNeverLooksFresh() {
        LabPublishPerformanceEvidence e=new LabPublishPerformanceEvidence();
        e.onTaskReturned();e.onTaskFinished(true,200,100);
        LabPublishPerformanceEvidence.Snapshot s=e.snapshot();
        assertEquals(1L,s.invalidClockSamples);
        assertEquals(0L,s.validTaskLatencies);
        assertTrue(Double.isNaN(s.meanTaskLatencyMs()));
    }

    @Test public void duplicateTerminalCallbackDoesNotGoNegative() {
        LabPublishPerformanceEvidence e=new LabPublishPerformanceEvidence();
        e.onTaskReturned();e.onTaskFinished(true,0,4);
        e.onTaskFinished(true,0,5);
        e.onCallbackUnavailable();
        LabPublishPerformanceEvidence.Snapshot s=e.snapshot();
        assertEquals(0L,s.pendingTasks);
        assertEquals(1L,s.taskFinished);
        assertEquals(2L,s.unmatchedCallbacks);
    }

    @Test public void backwardClockDoesNotInventFastTick() {
        LabPublishPerformanceEvidence e=new LabPublishPerformanceEvidence();
        e.onTick(200);e.onTick(100);
        LabPublishPerformanceEvidence.Snapshot s=e.snapshot();
        assertEquals(0L,s.tickIntervals);
        assertEquals(1L,s.invalidClockSamples);
        assertTrue(Double.isNaN(s.measuredTickHz()));
    }

    @Test public void threadSafeTaskCallbacksAreCounted() throws Exception {
        LabPublishPerformanceEvidence e=new LabPublishPerformanceEvidence();
        for (int i=0;i<200;i++) e.onTaskReturned();
        Thread t=new Thread(()->{
            for(int i=0;i<200;i++)e.onTaskFinished(true,10,13);
        });
        t.start();t.join();
        assertEquals(200L,e.snapshot().taskFinished);
        assertEquals(0L,e.snapshot().pendingTasks);
        assertEquals(200L,e.snapshot().validTaskLatencies);
    }

    @Test public void reportDoesNotIncludeLocationsOrDeviceIdentifiers() {
        LabPublishPerformanceEvidence e=new LabPublishPerformanceEvidence();
        e.onTick(100);e.onTick(150);e.onTaskReturned();e.onTaskFinished(true,140,170);
        String s=e.snapshot().render();
        assertTrue(s.contains("计划休眠间隔=33ms"));
        assertTrue(s.contains("pending=0"));
        assertFalse(s.contains("longitude="));
        assertFalse(s.contains("latitude="));
        assertFalse(s.contains("wechat process"));
    }
}
