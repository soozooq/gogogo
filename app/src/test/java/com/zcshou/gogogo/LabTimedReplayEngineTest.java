package com.zcshou.gogogo;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class LabTimedReplayEngineTest {

    @Test
    public void interpolatesHalfwayUsingOriginalTimeAxis() {
        LabTimedReplayEngine engine = new LabTimedReplayEngine();
        engine.load(
                new double[]{35.0, 35.0},
                new double[]{139.0, 139.001},
                new double[]{10.0, 20.0},
                new long[]{1_000L, 11_000L});

        LabTimedReplayEngine.Frame frame = engine.advance(5.0, 1.0);

        // dt is intentionally clamped to 1 second per engine call.
        assertEquals(0.1, frame.progressFraction, 0.001);
        assertEquals(12.0, frame.altitude, 0.001);
        assertTrue(frame.speedMps > 5.0);
        assertTrue(engine.isActive());
    }

    @Test
    public void playbackMultiplierAdvancesTimeAndReportedSpeed() {
        LabTimedReplayEngine engine = new LabTimedReplayEngine();
        engine.load(
                new double[]{35.0, 35.0},
                new double[]{139.0, 139.001},
                new double[]{10.0, 10.0},
                new long[]{0L, 10_000L});

        LabTimedReplayEngine.Frame normal = engine.advance(0.5, 1.0);
        double normalSpeed = normal.speedMps;

        LabTimedReplayEngine.Frame faster = engine.advance(0.5, 2.0);

        assertEquals(1_500L, engine.playheadMs());
        assertTrue(faster.speedMps > normalSpeed * 1.9);
    }

    @Test
    public void finishesAtLastPoint() {
        LabTimedReplayEngine engine = new LabTimedReplayEngine();
        engine.load(
                new double[]{35.0, 35.0001},
                new double[]{139.0, 139.0001},
                new double[]{10.0, 11.0},
                new long[]{100L, 1_100L});

        LabTimedReplayEngine.Frame frame = engine.advance(1.0, 1.0);

        assertTrue(engine.isFinished());
        assertEquals(1.0, frame.progressFraction, 0.0001);
        assertEquals(0.0, frame.speedMps, 0.0001);
    }
}
