package com.zcshou.gogogo;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

/**
 * GoGoGo Lab 12 kinematic observatory.
 *
 * This engine never writes physical sensors. It derives a coherent motion frame from
 * GoGoGo's own published location stream plus the handset heading that Android exposes
 * to this process. The frame is useful for replay diagnostics, map visualization and
 * reproducible experiment reports.
 */
public final class LabKinematicsEngine {
    private static final double MIN_DT_SEC = 0.001;
    private static final double MAX_ACCEL_MPS2 = 30.0;
    private static final double MAX_JERK_MPS3 = 120.0;
    private static final double MAX_TURN_RATE_DPS = 720.0;

    private double lastSpeedMps = Double.NaN;
    private double lastAccelerationMps2 = 0.0;
    private float lastEffectiveBearingDeg = Float.NaN;
    private long lastStep = -1L;
    private String chainHash = "GENESIS";

    public Frame update(
            double latitude,
            double longitude,
            double speedMps,
            float courseBearingDeg,
            boolean motionPaused,
            boolean deviceHeadingAvailable,
            float deviceHeadingDeg,
            String provenanceSource,
            long step,
            long tickIntervalMs) {

        long safeStep = Math.max(0L, step);
        if (lastStep >= 0L && safeStep <= lastStep) {
            reset();
        }

        double dt = Math.max(MIN_DT_SEC, Math.max(1L, tickIntervalMs) / 1000.0);
        double speed = Math.max(0.0, speedMps);
        float course = normalizeHeading(courseBearingDeg);
        float device = normalizeHeading(deviceHeadingDeg);

        String bearingSource;
        float targetBearing;
        if (speed >= 0.35) {
            targetBearing = course;
            bearingSource = "COURSE";
        } else if (deviceHeadingAvailable) {
            targetBearing = device;
            bearingSource = "DEVICE";
        } else {
            targetBearing = course;
            bearingSource = "COURSE_FALLBACK";
        }

        float alpha = "DEVICE".equals(bearingSource) ? 0.22f : 0.42f;
        float effectiveBearing = Float.isNaN(lastEffectiveBearingDeg)
                ? targetBearing
                : circularLerp(lastEffectiveBearingDeg, targetBearing, alpha);

        double acceleration = Double.isNaN(lastSpeedMps)
                ? 0.0
                : clamp((speed - lastSpeedMps) / dt, -MAX_ACCEL_MPS2, MAX_ACCEL_MPS2);

        double jerk = lastStep < 0L
                ? 0.0
                : clamp((acceleration - lastAccelerationMps2) / dt, -MAX_JERK_MPS3, MAX_JERK_MPS3);

        double turnRate = Float.isNaN(lastEffectiveBearingDeg)
                ? 0.0
                : clamp(shortestAngleDelta(lastEffectiveBearingDeg, effectiveBearing) / dt,
                -MAX_TURN_RATE_DPS, MAX_TURN_RATE_DPS);

        String state = classify(speed, motionPaused);
        String source = provenanceSource == null ? "UNKNOWN" : provenanceSource;

        String canonical = String.format(Locale.US,
                "step=%d|lat=%.7f|lng=%.7f|speed=%.4f|course=%.3f|device=%s|effective=%.3f|"
                        + "accel=%.4f|jerk=%.4f|turn=%.4f|state=%s|bearing=%s|source=%s",
                safeStep,
                latitude,
                longitude,
                speed,
                course,
                deviceHeadingAvailable ? String.format(Locale.US, "%.3f", device) : "NA",
                effectiveBearing,
                acceleration,
                jerk,
                turnRate,
                state,
                bearingSource,
                source);

        chainHash = sha256(chainHash + "\n" + canonical);

        Frame frame = new Frame(
                safeStep,
                state,
                speed,
                acceleration,
                jerk,
                turnRate,
                course,
                deviceHeadingAvailable,
                device,
                effectiveBearing,
                bearingSource,
                chainHash,
                source);

        lastSpeedMps = speed;
        lastAccelerationMps2 = acceleration;
        lastEffectiveBearingDeg = effectiveBearing;
        lastStep = safeStep;
        return frame;
    }

    public void reset() {
        lastSpeedMps = Double.NaN;
        lastAccelerationMps2 = 0.0;
        lastEffectiveBearingDeg = Float.NaN;
        lastStep = -1L;
        chainHash = "GENESIS";
    }

    private static String classify(double speed, boolean paused) {
        if (paused) return "PAUSED";
        if (speed < 0.15) return "STATIONARY";
        if (speed < 2.2) return "WALKING";
        if (speed < 5.0) return "RUNNING";
        if (speed < 12.0) return "CYCLING";
        return "VEHICLE";
    }

    private static float circularLerp(float fromDeg, float toDeg, float alpha) {
        float from = normalizeHeading(fromDeg);
        float to = normalizeHeading(toDeg);
        float delta = shortestAngleDelta(from, to);
        return normalizeHeading(from + delta * clampFloat(alpha, 0f, 1f));
    }

    private static float shortestAngleDelta(float fromDeg, float toDeg) {
        float delta = normalizeHeading(toDeg) - normalizeHeading(fromDeg);
        if (delta > 180f) delta -= 360f;
        if (delta < -180f) delta += 360f;
        return delta;
    }

    private static float normalizeHeading(float value) {
        if (Float.isNaN(value) || Float.isInfinite(value)) return 0f;
        float out = value % 360f;
        if (out < 0f) out += 360f;
        return out;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float clampFloat(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] data = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder(data.length * 2);
            for (byte b : data) {
                out.append(String.format(Locale.US, "%02x", b & 0xff));
            }
            return out.toString();
        } catch (Throwable t) {
            return Integer.toHexString(input.hashCode());
        }
    }

    public static final class Frame {
        public final long step;
        public final String state;
        public final double speedMps;
        public final double accelerationMps2;
        public final double jerkMps3;
        public final double turnRateDegPerSec;
        public final float courseBearingDeg;
        public final boolean deviceHeadingAvailable;
        public final float deviceHeadingDeg;
        public final float effectiveBearingDeg;
        public final String bearingSource;
        public final String chainHash;
        public final String provenanceSource;

        Frame(
                long step,
                String state,
                double speedMps,
                double accelerationMps2,
                double jerkMps3,
                double turnRateDegPerSec,
                float courseBearingDeg,
                boolean deviceHeadingAvailable,
                float deviceHeadingDeg,
                float effectiveBearingDeg,
                String bearingSource,
                String chainHash,
                String provenanceSource) {
            this.step = step;
            this.state = state;
            this.speedMps = speedMps;
            this.accelerationMps2 = accelerationMps2;
            this.jerkMps3 = jerkMps3;
            this.turnRateDegPerSec = turnRateDegPerSec;
            this.courseBearingDeg = courseBearingDeg;
            this.deviceHeadingAvailable = deviceHeadingAvailable;
            this.deviceHeadingDeg = deviceHeadingDeg;
            this.effectiveBearingDeg = effectiveBearingDeg;
            this.bearingSource = bearingSource;
            this.chainHash = chainHash;
            this.provenanceSource = provenanceSource;
        }

        public String shortHash() {
            if (chainHash == null) return "none";
            return chainHash.length() <= 16 ? chainHash : chainHash.substring(0, 16);
        }

        public String summary() {
            return String.format(Locale.US,
                    "%s · %.2fm/s · a=%+.2f · jerk=%+.2f · turn=%+.1f°/s · bearing=%s %.1f° · h=%s",
                    state,
                    speedMps,
                    accelerationMps2,
                    jerkMps3,
                    turnRateDegPerSec,
                    bearingSource,
                    effectiveBearingDeg,
                    shortHash());
        }
    }
}
