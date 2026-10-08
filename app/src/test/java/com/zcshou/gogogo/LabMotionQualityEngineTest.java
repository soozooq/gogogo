package com.zcshou.gogogo;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class LabMotionQualityEngineTest {

    @Test
    public void smoothWalkingTrackScoresHigh() {
        LabMotionQualityEngine engine = new LabMotionQualityEngine();

        double[] lats = {35.000000, 35.000009, 35.000018, 35.000027, 35.000036};
        double[] lngs = {139.000000, 139.000000, 139.000000, 139.000000, 139.000000};
        long[] times = {0L, 1000L, 2000L, 3000L, 4000L};

        LabMotionQualityEngine.Result result = engine.audit(lats, lngs, times);

        assertTrue(result.score >= 95);
        assertEquals(0, result.nonMonotonicTimestamps);
        assertEquals(0, result.teleportSegmentCount);
        assertEquals(0, result.accelerationSpikeCount);
    }

    @Test
    public void nonMonotonicTimestampIsPenalized() {
        LabMotionQualityEngine engine = new LabMotionQualityEngine();

        double[] lats = {35.0, 35.00001, 35.00002};
        double[] lngs = {139.0, 139.0, 139.0};
        long[] times = {1000L, 900L, 2000L};

        LabMotionQualityEngine.Result result = engine.audit(lats, lngs, times);

        assertEquals(1, result.nonMonotonicTimestamps);
        assertTrue(result.score < 100);
    }

    @Test
    public void largeJumpIsFlagged() {
        LabMotionQualityEngine engine = new LabMotionQualityEngine();

        double[] lats = {35.0, 36.0};
        double[] lngs = {139.0, 139.0};
        long[] times = {0L, 1000L};

        LabMotionQualityEngine.Result result = engine.audit(lats, lngs, times);

        assertEquals(1, result.teleportSegmentCount);
        assertTrue(result.maxSegmentSpeedMps > 80.0);
        assertTrue(result.score < 100);
    }

    @Test
    public void auditDigestIsDeterministic() {
        LabMotionQualityEngine engine = new LabMotionQualityEngine();

        double[] lats = {35.0, 35.00001, 35.00002};
        double[] lngs = {139.0, 139.00001, 139.00002};
        long[] times = {0L, 1000L, 2000L};

        LabMotionQualityEngine.Result a = engine.audit(lats, lngs, times);
        LabMotionQualityEngine.Result b = engine.audit(lats, lngs, times);

        assertEquals(a.digestSha256, b.digestSha256);
        assertEquals(a.score, b.score);
    }

    @Test
    public void insufficientTrackIsReported() {
        LabMotionQualityEngine engine = new LabMotionQualityEngine();

        LabMotionQualityEngine.Result result =
                engine.audit(new double[]{35.0}, new double[]{139.0}, new long[]{0L});

        assertEquals("INSUFFICIENT", result.grade);
        assertEquals(0, result.score);
    }
}
