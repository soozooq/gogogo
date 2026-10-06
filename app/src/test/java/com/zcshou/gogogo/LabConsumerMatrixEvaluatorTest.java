package com.zcshou.gogogo;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class LabConsumerMatrixEvaluatorTest {

    @Test
    public void consistentFreshChannelsScoreConsistent() {
        LabConsumerMatrixEvaluator.Result result =
                LabConsumerMatrixEvaluator.evaluate(Arrays.asList(
                        new LabConsumerMatrixEvaluator.Sample("gps", true, 35.0, 139.0, 50, true),
                        new LabConsumerMatrixEvaluator.Sample("network", true, 35.00001, 139.0, 70, true),
                        new LabConsumerMatrixEvaluator.Sample("last", true, 35.0, 139.00001, 80, true),
                        new LabConsumerMatrixEvaluator.Sample("current", true, 35.0, 139.0, 40, true),
                        new LabConsumerMatrixEvaluator.Sample("updates", true, 35.00001, 139.00001, 30, true)
                ));

        assertEquals("CONSISTENT", result.grade);
        assertEquals(5, result.availableChannels);
        assertEquals(5, result.freshChannels);
        assertTrue(result.maxSeparationMeters < 10.0);
    }

    @Test
    public void staleChannelsAreReported() {
        LabConsumerMatrixEvaluator.Result result =
                LabConsumerMatrixEvaluator.evaluate(Arrays.asList(
                        new LabConsumerMatrixEvaluator.Sample("gps", true, 35.0, 139.0, 5000, true),
                        new LabConsumerMatrixEvaluator.Sample("network", true, 35.0, 139.0, 50, true)
                ));

        assertEquals("DEGRADED", result.grade);
        assertEquals(1, result.freshChannels);
        assertTrue(result.staleChannels.contains("gps"));
    }

    @Test
    public void separatedChannelsDoNotScoreConsistent() {
        LabConsumerMatrixEvaluator.Result result =
                LabConsumerMatrixEvaluator.evaluate(Arrays.asList(
                        new LabConsumerMatrixEvaluator.Sample("gps", true, 35.0, 139.0, 50, true),
                        new LabConsumerMatrixEvaluator.Sample("network", true, 36.0, 139.0, 50, true),
                        new LabConsumerMatrixEvaluator.Sample("last", true, 35.0, 139.0, 50, true),
                        new LabConsumerMatrixEvaluator.Sample("current", true, 35.0, 139.0, 50, true)
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
        assertEquals(0, result.freshChannels);
    }
}
