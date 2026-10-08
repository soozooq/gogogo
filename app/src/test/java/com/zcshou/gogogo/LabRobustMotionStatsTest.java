package com.zcshou.gogogo;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class LabRobustMotionStatsTest {

    @Test
    public void stableSeriesHasSmallMadAndNoOutlier() {
        LabRobustMotionStats.Result result = LabRobustMotionStats.analyze(
                Arrays.asList(1.0, 1.1, 0.9, 1.0, 1.05, 0.95));

        assertEquals(1.0, result.median, 0.051);
        assertTrue(result.mad < 0.11);
        assertEquals(0, result.robustOutlierCount);
    }

    @Test
    public void isolatedSpikeIsDetectedAsRobustOutlier() {
        LabRobustMotionStats.Result result = LabRobustMotionStats.analyze(
                Arrays.asList(1.0, 1.0, 1.1, 0.9, 1.0, 25.0, 1.0, 1.1));

        assertTrue(result.robustOutlierCount >= 1);
    }

    @Test
    public void sustainedRegimeShiftProducesCusumSignal() {
        LabRobustMotionStats.Result result = LabRobustMotionStats.analyze(
                Arrays.asList(
                        1.0, 1.0, 1.1, 0.9, 1.0,
                        5.0, 5.1, 5.0, 4.9, 5.0, 5.1, 5.0));

        assertTrue(result.cusumChangePointCount >= 1);
    }

    @Test
    public void emptySeriesIsSafe() {
        LabRobustMotionStats.Result result = LabRobustMotionStats.analyze(
                java.util.Collections.emptyList());

        assertEquals(0.0, result.median, 0.0);
        assertEquals(0.0, result.mad, 0.0);
        assertEquals(0, result.robustOutlierCount);
        assertEquals(0, result.cusumChangePointCount);
    }
}
