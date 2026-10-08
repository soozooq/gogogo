package com.zcshou.gogogo;

import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class LabRoutePhysicsProfileTest {

    @Test
    public void carAcceleratesFasterThanWalk() {
        LabRoutePhysicsEngine walk = new LabRoutePhysicsEngine();
        walk.setProfile(LabRoutePhysicsEngine.Profile.WALK);
        walk.reset(0.0);

        LabRoutePhysicsEngine car = new LabRoutePhysicsEngine();
        car.setProfile(LabRoutePhysicsEngine.Profile.CAR);
        car.reset(0.0);

        LabRoutePhysicsEngine.Context context =
                new LabRoutePhysicsEngine.Context(
                        12.0,
                        1.0,
                        0.25,
                        100.0,
                        0.0,
                        500.0,
                        false,
                        false,
                        1000L);

        double walkSpeed = walk.update(context).speedMps;
        double carSpeed = car.update(context).speedMps;

        assertTrue(carSpeed > walkSpeed * 2.0);
    }

    @Test
    public void carPenalizesSharpCornersMoreThanWalk() {
        LabRoutePhysicsEngine walk = new LabRoutePhysicsEngine();
        walk.setProfile(LabRoutePhysicsEngine.Profile.WALK);
        walk.reset(8.0);

        LabRoutePhysicsEngine car = new LabRoutePhysicsEngine();
        car.setProfile(LabRoutePhysicsEngine.Profile.CAR);
        car.reset(8.0);

        LabRoutePhysicsEngine.Context context =
                new LabRoutePhysicsEngine.Context(
                        8.0,
                        1.0,
                        0.25,
                        2.0,
                        120.0,
                        500.0,
                        false,
                        false,
                        1000L);

        double walkTarget = walk.update(context).targetSpeedMps;
        double carTarget = car.update(context).targetSpeedMps;

        assertTrue(carTarget < walkTarget);
    }
}
