package com.zcshou.gogogo;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertThrows;

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
        assertEquals(11.0, frame.altitude, 0.001);
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

    private LabTimedReplayEngine activeReplay() {
        LabTimedReplayEngine engine = new LabTimedReplayEngine();
        engine.load(new double[] {35.0, 35.0}, new double[] {139.0, 139.001},
                new double[] {10.0, 20.0}, new long[] {1000L, 11000L});
        engine.advance(0.5, 1.0);
        return engine;
    }

    @Test(timeout = 1000)
    public void rejectsOutOfRangeCoordinatesBeforeSampling() {
        for (double[] point : new double[][] {{91, 10}, {-91, 10}, {10, 181},
                {10, -181}, {10, Double.MAX_VALUE}}) {
            LabTimedReplayEngine engine = new LabTimedReplayEngine();
            assertThrows(IllegalArgumentException.class, () -> engine.load(
                    new double[] {0, point[0]}, new double[] {0, point[1]}, null,
                    new long[] {0, 1000}));
        }
    }

    @Test
    public void rejectsInvalidAltitudeAndPointCount() {
        LabTimedReplayEngine engine = new LabTimedReplayEngine();
        assertThrows(IllegalArgumentException.class, () -> engine.load(
                new double[] {0, 1}, new double[] {0, 1},
                new double[] {0, Double.NaN}, new long[] {0, 1000}));
        assertThrows(IllegalArgumentException.class, () -> engine.load(
                new double[5001], new double[5001], null, new long[5001]));
    }

    @Test
    public void rejectedReplacementKeepsTheActiveReplayAndPlayhead() {
        LabTimedReplayEngine engine = activeReplay();
        LabTimedReplayEngine.Frame before = engine.current();
        assertThrows(IllegalArgumentException.class, () -> engine.load(
                new double[] {48, 49}, new double[] {2, 3}, null,
                new long[] {1000, 1000}));
        assertTrue(engine.isActive());
        assertEquals(500L, engine.playheadMs());
        assertEquals(10000L, engine.durationMs());
        assertEquals(before.latitude, engine.current().latitude, 0.0);
        assertEquals(before.longitude, engine.current().longitude, 0.0);
        assertEquals(before.altitude, engine.current().altitude, 0.0);
    }

    @Test
    public void overflowingTimelineDoesNotDamageThePriorReplay() {
        LabTimedReplayEngine engine = activeReplay();
        assertThrows(IllegalArgumentException.class, () -> engine.load(
                new double[] {48, 49}, new double[] {2, 3}, null,
                new long[] {Long.MIN_VALUE, Long.MAX_VALUE}));
        assertTrue(engine.isActive());
        assertEquals(500L, engine.playheadMs());
        assertEquals(35.0, engine.current().latitude, 0.0);
    }

    @Test
    public void nonFiniteTimingCannotPoisonAnActiveReplay() {
        LabTimedReplayEngine engine = activeReplay();
        assertThrows(IllegalArgumentException.class, () -> engine.advance(Double.NaN, 1));
        assertThrows(IllegalArgumentException.class, () -> engine.advance(0.5, Double.POSITIVE_INFINITY));
        assertEquals(500L, engine.playheadMs());
        assertTrue(Double.isFinite(engine.advance(0.5, 1).longitude));
        assertEquals(1000L, engine.playheadMs());
    }

    @Test
    public void crossesTheDatelineWithoutJumpingThroughGreenwich() {
        LabTimedReplayEngine engine = new LabTimedReplayEngine();
        engine.load(new double[] {10, 10}, new double[] {179.9, -179.9}, null,
                new long[] {0, 1000});
        LabTimedReplayEngine.Frame middle = engine.advance(0.5, 1);
        assertEquals(180.0, Math.abs(middle.longitude), 1e-9);
        assertTrue(Double.isFinite(middle.speedMps));
        assertTrue(middle.speedMps < 25000);
    }

    @Test
    public void finiteExtremeAltitudesDoNotOverflowDuringInterpolation() {
        LabTimedReplayEngine engine = new LabTimedReplayEngine();
        engine.load(new double[] {0, 1}, new double[] {0, 1},
                new double[] {Double.MAX_VALUE, -Double.MAX_VALUE}, new long[] {0, 1000});
        assertEquals(Double.MAX_VALUE, engine.current().altitude, 0.0);
        assertEquals(0.0, engine.advance(0.5, 1).altitude, 0.0);
    }

    @Test
    public void duplicateIntermediateTimestampsRemainSupported() {
        LabTimedReplayEngine engine = new LabTimedReplayEngine();
        engine.load(new double[] {0, 0, 0}, new double[] {0, 0.001, 0.002}, null,
                new long[] {0, 0, 1000});
        assertTrue(Double.isFinite(engine.advance(0.5, 1).speedMps));
        assertEquals(0.0015, engine.current().longitude, 1e-9);
    }
}
