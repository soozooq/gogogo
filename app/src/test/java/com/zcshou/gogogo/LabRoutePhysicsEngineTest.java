package com.zcshou.gogogo;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class LabRoutePhysicsEngineTest {

    @Test
    public void acceleratesInsteadOfJumpingToCruise() {
        LabRoutePhysicsEngine engine = new LabRoutePhysicsEngine();
        engine.reset(0.0);

        LabRoutePhysicsEngine.Frame frame = engine.update(
                new LabRoutePhysicsEngine.Context(
                        10.0,
                        1.0,
                        0.25,
                        100.0,
                        0.0,
                        1000.0,
                        false,
                        false,
                        1000L));

        assertTrue(frame.speedMps > 0.0);
        assertTrue(frame.speedMps < 10.0);
        assertEquals("ACCEL", frame.phase);
    }

    @Test
    public void sharpUpcomingTurnReducesTargetSpeed() {
        LabRoutePhysicsEngine engine = new LabRoutePhysicsEngine();
        engine.reset(12.0);

        LabRoutePhysicsEngine.Frame frame = engine.update(
                new LabRoutePhysicsEngine.Context(
                        12.0,
                        1.0,
                        0.25,
                        4.0,
                        135.0,
                        200.0,
                        false,
                        false,
                        1000L));

        assertTrue(frame.targetSpeedMps < 12.0);
        assertTrue(frame.speedMps <= 12.0);
        assertEquals("CORNER_BRAKE", frame.phase);
    }

    @Test
    public void routeEndTriggersBraking() {
        LabRoutePhysicsEngine engine = new LabRoutePhysicsEngine();
        engine.reset(8.0);

        LabRoutePhysicsEngine.Frame frame = engine.update(
                new LabRoutePhysicsEngine.Context(
                        8.0,
                        1.0,
                        0.25,
                        2.0,
                        0.0,
                        2.0,
                        true,
                        false,
                        1000L));

        assertTrue(frame.targetSpeedMps < 8.0);
        assertEquals("END_BRAKE", frame.phase);
    }

    @Test
    public void sharpVertexArmsShortDwell() {
        LabRoutePhysicsEngine engine = new LabRoutePhysicsEngine();
        engine.reset(5.0);

        boolean armed = engine.onVertexReached(
                120.0,
                true,
                1000L);

        assertTrue(armed);

        LabRoutePhysicsEngine.Frame duringDwell = engine.update(
                new LabRoutePhysicsEngine.Context(
                        5.0,
                        1.0,
                        0.25,
                        20.0,
                        0.0,
                        100.0,
                        false,
                        false,
                        1200L));

        assertEquals(0.0, duringDwell.speedMps, 0.0001);
        assertEquals("DWELL", duringDwell.phase);
    }

    @Test
    public void mildVertexDoesNotDwell() {
        LabRoutePhysicsEngine engine = new LabRoutePhysicsEngine();
        engine.reset(5.0);

        boolean armed = engine.onVertexReached(
                35.0,
                true,
                1000L);

        assertTrue(!armed);
    }
}
