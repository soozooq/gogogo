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
                        new LabConsumerMatrixEvaluator.Sample("gps", true, 35.0, 139.0, 50, true, true),
                        new LabConsumerMatrixEvaluator.Sample("network", true, 35.00001, 139.0, 70, true, true),
                        new LabConsumerMatrixEvaluator.Sample("last", true, 35.0, 139.00001, 6000, true, false),
                        new LabConsumerMatrixEvaluator.Sample("current", true, 35.0, 139.0, 5800, true, false),
                        new LabConsumerMatrixEvaluator.Sample("updates", true, 35.00001, 139.00001, 30, true, true)
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
                        new LabConsumerMatrixEvaluator.Sample("gps", true, 35.0, 139.0, 5000, true, true),
                        new LabConsumerMatrixEvaluator.Sample("network", true, 35.0, 139.0, 50, true, true),
                        new LabConsumerMatrixEvaluator.Sample("updates", true, 35.0, 139.0, 60, true, true)
                ));

        assertEquals("PARTIAL", result.grade);
        assertEquals(2, result.freshStreams);
        assertTrue(result.staleStreams.contains("gps"));
    }

    @Test
    public void staleSnapshotDoesNotDowngradeHealthyStreams() {
        LabConsumerMatrixEvaluator.Result result =
                LabConsumerMatrixEvaluator.evaluate(Arrays.asList(
                        new LabConsumerMatrixEvaluator.Sample("gps", true, 35.0, 139.0, 50, true, true),
                        new LabConsumerMatrixEvaluator.Sample("network", true, 35.0, 139.0, 50, true, true),
                        new LabConsumerMatrixEvaluator.Sample("updates", true, 35.0, 139.0, 50, true, true),
                        new LabConsumerMatrixEvaluator.Sample("last", true, 35.0, 139.0, 60000, true, false)
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
                        new LabConsumerMatrixEvaluator.Sample("gps", true, 35.0, 139.0, 50, true, true),
                        new LabConsumerMatrixEvaluator.Sample("network", true, 36.0, 139.0, 50, true, true),
                        new LabConsumerMatrixEvaluator.Sample("updates", true, 35.0, 139.0, 50, true, true)
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
}
