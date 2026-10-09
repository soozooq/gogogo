package com.zcshou.gogogo;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class LabConsumerMatrixEvaluatorTest {

    @Test
    public void consistentFreshStreamsScoreConsistent() {
        LabConsumerMatrixEvaluator.Result result =
                LabConsumerMatrixEvaluator.evaluate(Arrays.asList(
                        new LabConsumerMatrixEvaluator.Sample("gps", true, 35.0, 139.0, 50, 0L, true, true),
                        new LabConsumerMatrixEvaluator.Sample("network", true, 35.00001, 139.0, 70, 0L, true, true),
                        new LabConsumerMatrixEvaluator.Sample("last", true, 35.0, 139.00001, 6000, 0L, true, false),
                        new LabConsumerMatrixEvaluator.Sample("current", true, 35.0, 139.0, 5800, 0L, true, false),
                        new LabConsumerMatrixEvaluator.Sample("updates", true, 35.00001, 139.00001, 30, 0L, true, true)
                ));

        assertEquals("CONSISTENT", result.grade);
        assertEquals(5, result.availableChannels);
        assertEquals(3, result.availableStreams);
        assertEquals(3, result.freshStreams);
        assertEquals(2, result.availableSnapshots);
        assertEquals(2, result.freshSnapshots);
        assertTrue(result.maxSeparationMeters < 10.0);
    }

    @Test
    public void staleStreamIsReported() {
        LabConsumerMatrixEvaluator.Result result =
                LabConsumerMatrixEvaluator.evaluate(Arrays.asList(
                        new LabConsumerMatrixEvaluator.Sample("gps", true, 35.0, 139.0, 5000, 0L, true, true),
                        new LabConsumerMatrixEvaluator.Sample("network", true, 35.0, 139.0, 50, 0L, true, true),
                        new LabConsumerMatrixEvaluator.Sample("updates", true, 35.0, 139.0, 60, 0L, true, true)
                ));

        assertEquals("PARTIAL", result.grade);
        assertEquals(2, result.freshStreams);
        assertTrue(result.staleStreams.contains("gps"));
    }

    @Test
    public void staleSnapshotDoesNotDowngradeHealthyStreams() {
        LabConsumerMatrixEvaluator.Result result =
                LabConsumerMatrixEvaluator.evaluate(Arrays.asList(
                        new LabConsumerMatrixEvaluator.Sample("gps", true, 35.0, 139.0, 50, 0L, true, true),
                        new LabConsumerMatrixEvaluator.Sample("network", true, 35.0, 139.0, 50, 0L, true, true),
                        new LabConsumerMatrixEvaluator.Sample("updates", true, 35.0, 139.0, 50, 0L, true, true),
                        new LabConsumerMatrixEvaluator.Sample("last", true, 35.0, 139.0, 60000, 0L, true, false)
                ));

        assertEquals("CONSISTENT", result.grade);
        assertEquals(3, result.freshStreams);
        assertEquals(0, result.freshSnapshots);
        assertTrue(result.staleSnapshots.contains("last"));
    }

    @Test
    public void separatedChannelsDoNotScoreConsistent() {
        LabConsumerMatrixEvaluator.Result result =
                LabConsumerMatrixEvaluator.evaluate(Arrays.asList(
                        new LabConsumerMatrixEvaluator.Sample("gps", true, 35.0, 139.0, 50, 0L, true, true),
                        new LabConsumerMatrixEvaluator.Sample("network", true, 36.0, 139.0, 50, 0L, true, true),
                        new LabConsumerMatrixEvaluator.Sample("updates", true, 35.0, 139.0, 50, 0L, true, true)
                ));

        assertEquals("DEGRADED", result.grade);
        assertTrue(result.maxSeparationMeters > 1000.0);
    }

    @Test
    public void emptyInputIsSafe() {
        LabConsumerMatrixEvaluator.Result result =
                LabConsumerMatrixEvaluator.evaluate(Collections.emptyList());

        assertEquals("DEGRADED", result.grade);
        assertEquals(0, result.availableChannels);
        assertEquals(0, result.freshStreams);
    }
    @Test
    public void newlyDeliveredOldStreamFixIsNotFresh() {
        LabConsumerMatrixEvaluator.Result result =
                LabConsumerMatrixEvaluator.evaluate(Arrays.asList(
                        sample("gps", 600000, 0, true),
                        sample("network", 20, 0, true),
                        sample("updates", 20, 0, true)));
        assertEquals("PARTIAL", result.grade);
        assertEquals(2, result.freshStreams);
        assertTrue(result.staleStreams.contains("gps"));
    }

    @Test
    public void newlyDeliveredOldSnapshotCannotMasqueradeAsFresh() {
        LabConsumerMatrixEvaluator.Result result =
                LabConsumerMatrixEvaluator.evaluate(Arrays.asList(
                        sample("gps", 50, 50, true),
                        sample("network", 50, 50, true),
                        sample("updates", 50, 50, true),
                        sample("last", 600000, 0, false)));
        assertEquals("CONSISTENT", result.grade);
        assertEquals(0, result.freshSnapshots);
        assertTrue(result.staleSnapshots.contains("last"));
    }

    @Test
    public void staleCallbackDoesNotCountAsLiveStream() {
        LabConsumerMatrixEvaluator.Result result =
                LabConsumerMatrixEvaluator.evaluate(Arrays.asList(
                        sample("gps", 20, 2000, true),
                        sample("network", 20, 0, true),
                        sample("updates", 20, 0, true)));
        assertEquals("PARTIAL", result.grade);
        assertTrue(result.staleStreams.contains("gps"));
    }

    @Test
    public void unknownFixAgeIsNotFreshEvenWithRecentCallback() {
        LabConsumerMatrixEvaluator.Result result =
                LabConsumerMatrixEvaluator.evaluate(Arrays.asList(
                        sample("gps", -1, 0, true),
                        sample("network", 25, 0, true),
                        sample("updates", 25, 0, true),
                        sample("last", -1, 0, false)));
        assertEquals("PARTIAL", result.grade);
        assertTrue(result.staleStreams.contains("gps"));
        assertTrue(result.staleSnapshots.contains("last"));
    }

    @Test
    public void oldRemoteSnapshotDoesNotPoisonFreshStreamDistance() {
        LabConsumerMatrixEvaluator.Result result =
                LabConsumerMatrixEvaluator.evaluate(Arrays.asList(
                        sample("gps", 50, 0, true),
                        sample("network", 50, 0, true),
                        sample("updates", 50, 0, true),
                        new LabConsumerMatrixEvaluator.Sample(
                                "old far away", true, 40.0, 130.0, 600000, 0, true, false)));
        assertEquals("CONSISTENT", result.grade);
        assertTrue(result.maxSeparationMeters > 1000.0);
        assertTrue(result.maxFreshStreamSeparationMeters < 10.0);
    }

    private static LabConsumerMatrixEvaluator.Sample sample(
            String name, long fixAge, long callbackAge, boolean stream) {
        return new LabConsumerMatrixEvaluator.Sample(
                name, true, 35.0, 139.0, fixAge, callbackAge, true, stream);
    }

}
