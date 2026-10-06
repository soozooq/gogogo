package com.zcshou.gogogo;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class LabKinematicsEngineTest {

    @Test
    public void stationaryUsesDeviceHeading() {
        LabKinematicsEngine engine = new LabKinematicsEngine();
        LabKinematicsEngine.Frame frame = engine.update(
                35.0, 139.0, 0.0, 90f,
                false, true, 270f,
                "TEST", 0L, 33L);

        assertEquals("DEVICE", frame.bearingSource);
        assertEquals(270f, frame.effectiveBearingDeg, 0.001f);
        assertEquals("STATIONARY", frame.state);
    }

    @Test
    public void movingUsesCourseHeading() {
        LabKinematicsEngine engine = new LabKinematicsEngine();
        LabKinematicsEngine.Frame frame = engine.update(
                35.0, 139.0, 3.0, 45f,
                false, true, 270f,
                "ROUTE", 0L, 33L);

        assertEquals("COURSE", frame.bearingSource);
        assertEquals(45f, frame.effectiveBearingDeg, 0.001f);
        assertEquals("RUNNING", frame.state);
    }

    @Test
    public void replayResetReproducesHashChain() {
        LabKinematicsEngine engine = new LabKinematicsEngine();

        engine.update(35.0, 139.0, 1.2, 10f,
                false, false, 0f, "ROUTE", 0L, 33L);
        LabKinematicsEngine.Frame second = engine.update(
                35.00001, 139.00001, 1.4, 12f,
                false, false, 0f, "ROUTE", 1L, 33L);
        String firstRun = second.chainHash;

        engine.reset();

        engine.update(35.0, 139.0, 1.2, 10f,
                false, false, 0f, "ROUTE", 0L, 33L);
        LabKinematicsEngine.Frame replay = engine.update(
                35.00001, 139.00001, 1.4, 12f,
                false, false, 0f, "ROUTE", 1L, 33L);

        assertEquals(firstRun, replay.chainHash);
    }

    @Test
    public void headingSmoothingCrossesNorthByShortestArc() {
        LabKinematicsEngine engine = new LabKinematicsEngine();

        engine.update(35.0, 139.0, 0.0, 0f,
                false, true, 359f, "TEST", 0L, 33L);
        LabKinematicsEngine.Frame frame = engine.update(
                35.0, 139.0, 0.0, 0f,
                false, true, 1f, "TEST", 1L, 33L);

        assertTrue(frame.effectiveBearingDeg > 350f || frame.effectiveBearingDeg < 10f);
        assertTrue(Math.abs(frame.turnRateDegPerSec) < 100f);
    }
}
