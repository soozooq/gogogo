package com.zcshou.gogogo;

import java.util.Locale;

/**
 * Lab 21 route kinematics model inspired by open-source route simulators.
 *
 * It models acceleration, service braking, corner speed limits and short dwell at
 * sufficiently sharp vertices. It does not spoof sensors and does not hide mock state.
 */
public final class LabRoutePhysicsEngine {
    private static final double DEFAULT_ACCEL_MPS2 = 1.35;
    private static final double DEFAULT_BRAKE_MPS2 = 2.10;
    private static final double MIN_MOVING_SPEED_MPS = 0.22;
    private static final double SHARP_TURN_DWELL_DEG = 105.0;
    private static final long SHARP_TURN_DWELL_MS = 550L;

    private double currentSpeedMps = 0.0;
    private double targetSpeedMps = 0.0;
    private double lastTurnAngleDeg = 0.0;
    private long dwellUntilElapsedMs = 0L;
    private String phase = "IDLE";

    public void reset(double initialSpeedMps) {
        currentSpeedMps = Math.max(0.0, initialSpeedMps);
        targetSpeedMps = currentSpeedMps;
        lastTurnAngleDeg = 0.0;
        dwellUntilElapsedMs = 0L;
        phase = currentSpeedMps > 0.01 ? "CRUISE" : "IDLE";
    }

    public void hardStop(String reason) {
        currentSpeedMps = 0.0;
        targetSpeedMps = 0.0;
        dwellUntilElapsedMs = 0L;
        phase = reason == null ? "STOPPED" : reason;
    }

    public Frame update(Context context) {
        if (context == null) {
            hardStop("NO_CONTEXT");
            return frame();
        }

        double dt = clamp(context.dtSeconds, 0.0, 0.25);
        double cruise = clamp(
                context.cruiseSpeedMps * context.multiplier,
                0.0,
                90.0);

        lastTurnAngleDeg = clamp(context.upcomingTurnAngleDeg, 0.0, 180.0);

        if (context.paused) {
            currentSpeedMps = 0.0;
            targetSpeedMps = 0.0;
            phase = "PAUSED";
            return frame();
        }

        if (context.elapsedRealtimeMs < dwellUntilElapsedMs) {
            currentSpeedMps = 0.0;
            targetSpeedMps = 0.0;
            phase = "DWELL";
            return frame();
        }

        double cornerLimit = cornerSpeedLimit(cruise, lastTurnAngleDeg);
        targetSpeedMps = cruise;

        double cornerBrakeDistance = brakingDistance(
                Math.max(currentSpeedMps, cruise),
                cornerLimit,
                DEFAULT_BRAKE_MPS2);

        boolean cornerActuallyLimitsSpeed = cornerLimit < cruise - 0.05;
        if (cornerActuallyLimitsSpeed
                && context.distanceToNextVertexM
                <= Math.max(2.0, cornerBrakeDistance + 1.0)) {
            targetSpeedMps = Math.min(targetSpeedMps, cornerLimit);
            phase = "CORNER_BRAKE";
        } else {
            phase = "CRUISE";
        }

        if (context.stopAtRouteEnd) {
            double safeEndSpeed = Math.sqrt(
                    Math.max(0.0,
                            2.0 * DEFAULT_BRAKE_MPS2
                                    * Math.max(0.0, context.distanceToRouteEndM)));

            if (context.distanceToRouteEndM < 30.0
                    || safeEndSpeed < targetSpeedMps) {
                targetSpeedMps = Math.min(targetSpeedMps, safeEndSpeed);
                phase = "END_BRAKE";
            }

            if (context.distanceToRouteEndM > 0.08
                    && targetSpeedMps < MIN_MOVING_SPEED_MPS) {
                targetSpeedMps = MIN_MOVING_SPEED_MPS;
            }
        }

        if (dt <= 0.0) {
            return frame();
        }

        if (currentSpeedMps < targetSpeedMps) {
            currentSpeedMps = Math.min(
                    targetSpeedMps,
                    currentSpeedMps + DEFAULT_ACCEL_MPS2 * dt);
            if ("CRUISE".equals(phase)) phase = "ACCEL";
        } else if (currentSpeedMps > targetSpeedMps) {
            currentSpeedMps = Math.max(
                    targetSpeedMps,
                    currentSpeedMps - DEFAULT_BRAKE_MPS2 * dt);
        }

        if (Math.abs(currentSpeedMps - cruise) < 0.05
                && Math.abs(targetSpeedMps - cruise) < 0.05) {
            phase = "CRUISE";
        }

        return frame();
    }

    /**
     * Called after ServiceGo reaches a route vertex.
     * Returns true when a dwell was armed and remaining movement for this tick should stop.
     */
    public boolean onVertexReached(
            double turnAngleDeg,
            boolean routeStillActive,
            long elapsedRealtimeMs) {
        lastTurnAngleDeg = clamp(turnAngleDeg, 0.0, 180.0);

        if (!routeStillActive) {
            hardStop("FINISHED");
            return true;
        }

        if (lastTurnAngleDeg >= SHARP_TURN_DWELL_DEG) {
            dwellUntilElapsedMs = Math.max(
                    dwellUntilElapsedMs,
                    elapsedRealtimeMs + SHARP_TURN_DWELL_MS);
            currentSpeedMps = 0.0;
            targetSpeedMps = 0.0;
            phase = "DWELL";
            return true;
        }

        return false;
    }

    public Frame frame() {
        return new Frame(
                currentSpeedMps,
                targetSpeedMps,
                lastTurnAngleDeg,
                phase,
                dwellUntilElapsedMs);
    }

    private static double cornerSpeedLimit(double cruise, double turnAngleDeg) {
        if (cruise <= 0.0) return 0.0;
        double severity = clamp(turnAngleDeg / 180.0, 0.0, 1.0);
        double factor = 1.0 - 0.78 * Math.pow(severity, 0.80);
        factor = clamp(factor, 0.22, 1.0);
        return Math.min(cruise, Math.max(MIN_MOVING_SPEED_MPS, cruise * factor));
    }

    private static double brakingDistance(
            double fromSpeed,
            double toSpeed,
            double decelerationMps2) {
        double from2 = Math.max(0.0, fromSpeed * fromSpeed);
        double to2 = Math.max(0.0, toSpeed * toSpeed);
        if (from2 <= to2 || decelerationMps2 <= 0.0) return 0.0;
        return (from2 - to2) / (2.0 * decelerationMps2);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public static final class Context {
        public final double cruiseSpeedMps;
        public final double multiplier;
        public final double dtSeconds;
        public final double distanceToNextVertexM;
        public final double upcomingTurnAngleDeg;
        public final double distanceToRouteEndM;
        public final boolean stopAtRouteEnd;
        public final boolean paused;
        public final long elapsedRealtimeMs;

        public Context(
                double cruiseSpeedMps,
                double multiplier,
                double dtSeconds,
                double distanceToNextVertexM,
                double upcomingTurnAngleDeg,
                double distanceToRouteEndM,
                boolean stopAtRouteEnd,
                boolean paused,
                long elapsedRealtimeMs) {
            this.cruiseSpeedMps = cruiseSpeedMps;
            this.multiplier = multiplier;
            this.dtSeconds = dtSeconds;
            this.distanceToNextVertexM = Math.max(0.0, distanceToNextVertexM);
            this.upcomingTurnAngleDeg = upcomingTurnAngleDeg;
            this.distanceToRouteEndM = Math.max(0.0, distanceToRouteEndM);
            this.stopAtRouteEnd = stopAtRouteEnd;
            this.paused = paused;
            this.elapsedRealtimeMs = elapsedRealtimeMs;
        }
    }

    public static final class Frame {
        public final double speedMps;
        public final double targetSpeedMps;
        public final double turnAngleDeg;
        public final String phase;
        public final long dwellUntilElapsedMs;

        Frame(
                double speedMps,
                double targetSpeedMps,
                double turnAngleDeg,
                String phase,
                long dwellUntilElapsedMs) {
            this.speedMps = speedMps;
            this.targetSpeedMps = targetSpeedMps;
            this.turnAngleDeg = turnAngleDeg;
            this.phase = phase;
            this.dwellUntilElapsedMs = dwellUntilElapsedMs;
        }

        public String summary() {
            return String.format(
                    Locale.US,
                    "%s · v=%.2f→%.2f m/s · turn=%.0f°",
                    phase,
                    speedMps,
                    targetSpeedMps,
                    turnAngleDeg);
        }
    }
}
