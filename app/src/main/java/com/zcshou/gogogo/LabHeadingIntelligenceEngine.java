package com.zcshou.gogogo;

import java.util.Locale;

/**
 * Lab 14 adaptive heading fusion for GoGoGo's own diagnostics and map rendering.
 *
 * The absolute rotation vector is magnetically referenced. The game rotation vector
 * ignores the geomagnetic field and therefore provides cleaner relative rotation but
 * may drift. This engine learns an offset between them while magnetics are trustworthy,
 * then keeps short-term inertial heading when magnetic conditions degrade.
 *
 * It does not inject or virtualize Android sensor events for other applications.
 */
public final class LabHeadingIntelligenceEngine {
    public static final int ACCURACY_UNRELIABLE = 0;
    public static final int ACCURACY_LOW = 1;
    public static final int ACCURACY_MEDIUM = 2;
    public static final int ACCURACY_HIGH = 3;

    private static final double MIN_MAG_UT = 10.0;
    private static final double MAX_MAG_UT = 120.0;
    private static final double BASELINE_ALPHA = 0.02;
    private static final double OFFSET_ALPHA = 0.08;
    private static final double MAG_DEVIATION_LIMIT = 0.22;
    private static final long HOLD_DECAY_NS = 30_000_000_000L;

    private float absoluteHeadingDeg = Float.NaN;
    private float gameHeadingDeg = Float.NaN;
    private float fusedHeadingDeg = Float.NaN;
    private float gameToNorthOffsetDeg = 0f;

    private double magneticNormUt = Double.NaN;
    private double magneticBaselineUt = Double.NaN;
    private double magneticDeviationRatio = 0.0;

    private int absoluteAccuracy = ACCURACY_UNRELIABLE;
    private int magneticAccuracy = ACCURACY_UNRELIABLE;

    private boolean magneticDisturbed = true;
    private boolean offsetLocked = false;
    private long lastReliableNorthNs = 0L;
    private long lastUpdateNs = 0L;

    public synchronized Frame onAbsoluteHeading(
            float headingDeg,
            int accuracy,
            long timestampNs) {
        absoluteHeadingDeg = normalize(headingDeg);
        absoluteAccuracy = accuracy;
        lastUpdateNs = Math.max(lastUpdateNs, timestampNs);
        recompute(timestampNs);
        return snapshot(timestampNs);
    }

    public synchronized Frame onGameHeading(float headingDeg, long timestampNs) {
        gameHeadingDeg = normalize(headingDeg);
        lastUpdateNs = Math.max(lastUpdateNs, timestampNs);
        recompute(timestampNs);
        return snapshot(timestampNs);
    }

    public synchronized Frame onMagneticField(
            float xUt,
            float yUt,
            float zUt,
            int accuracy,
            long timestampNs) {
        magneticAccuracy = accuracy;
        magneticNormUt = Math.sqrt(
                (double) xUt * xUt
                        + (double) yUt * yUt
                        + (double) zUt * zUt);

        boolean plausible = magneticNormUt >= MIN_MAG_UT && magneticNormUt <= MAX_MAG_UT;

        if (Double.isNaN(magneticBaselineUt)) {
            if (plausible && accuracy >= ACCURACY_MEDIUM) {
                magneticBaselineUt = magneticNormUt;
            }
        } else {
            magneticDeviationRatio = Math.abs(magneticNormUt - magneticBaselineUt)
                    / Math.max(1.0, magneticBaselineUt);

            boolean stableEnoughToLearn = plausible
                    && accuracy >= ACCURACY_MEDIUM
                    && magneticDeviationRatio < 0.15;
            if (stableEnoughToLearn) {
                magneticBaselineUt = magneticBaselineUt * (1.0 - BASELINE_ALPHA)
                        + magneticNormUt * BASELINE_ALPHA;
                magneticDeviationRatio = Math.abs(magneticNormUt - magneticBaselineUt)
                        / Math.max(1.0, magneticBaselineUt);
            }
        }

        if (Double.isNaN(magneticBaselineUt)) {
            magneticDeviationRatio = 1.0;
        }

        magneticDisturbed = !plausible
                || accuracy < ACCURACY_MEDIUM
                || magneticDeviationRatio > MAG_DEVIATION_LIMIT;

        lastUpdateNs = Math.max(lastUpdateNs, timestampNs);
        recompute(timestampNs);
        return snapshot(timestampNs);
    }

    public synchronized Frame updateAccuracy(
            int sensorType,
            int accuracy,
            long timestampNs) {
        if (sensorType == 11 /* TYPE_ROTATION_VECTOR */
                || sensorType == 20 /* TYPE_GEOMAGNETIC_ROTATION_VECTOR */) {
            absoluteAccuracy = accuracy;
        } else if (sensorType == 2 /* TYPE_MAGNETIC_FIELD */) {
            magneticAccuracy = accuracy;
        }
        recompute(timestampNs);
        return snapshot(timestampNs);
    }

    private void recompute(long timestampNs) {
        boolean hasAbsolute = !Float.isNaN(absoluteHeadingDeg);
        boolean hasGame = !Float.isNaN(gameHeadingDeg);
        boolean northReliable = hasAbsolute
                && !magneticDisturbed
                && absoluteAccuracy >= ACCURACY_MEDIUM;

        if (northReliable && hasGame) {
            float desiredOffset = shortestDelta(gameHeadingDeg, absoluteHeadingDeg);
            if (!offsetLocked) {
                gameToNorthOffsetDeg = desiredOffset;
                offsetLocked = true;
            } else {
                gameToNorthOffsetDeg = circularBlendSigned(
                        gameToNorthOffsetDeg,
                        desiredOffset,
                        (float) OFFSET_ALPHA);
            }
            lastReliableNorthNs = timestampNs;
        } else if (northReliable) {
            lastReliableNorthNs = timestampNs;
        }

        if (hasGame && offsetLocked) {
            fusedHeadingDeg = normalize(gameHeadingDeg + gameToNorthOffsetDeg);
        } else if (hasAbsolute) {
            fusedHeadingDeg = absoluteHeadingDeg;
        } else if (hasGame) {
            fusedHeadingDeg = gameHeadingDeg;
        } else {
            fusedHeadingDeg = Float.NaN;
        }
    }

    public synchronized Frame snapshot(long nowNs) {
        boolean hasAbsolute = !Float.isNaN(absoluteHeadingDeg);
        boolean hasGame = !Float.isNaN(gameHeadingDeg);
        boolean northReliable = hasAbsolute
                && !magneticDisturbed
                && absoluteAccuracy >= ACCURACY_MEDIUM;

        String state;
        if (!hasAbsolute && !hasGame) {
            state = "UNAVAILABLE";
        } else if (northReliable && hasGame && offsetLocked) {
            state = "MAG_LOCKED";
        } else if (hasGame && offsetLocked) {
            state = "INERTIAL_HOLD";
        } else if (hasAbsolute) {
            state = "ABSOLUTE_ONLY";
        } else {
            state = "RELATIVE_ONLY";
        }

        double confidence;
        if ("MAG_LOCKED".equals(state)) {
            confidence = absoluteAccuracy >= ACCURACY_HIGH ? 0.96 : 0.86;
            confidence *= Math.max(0.65, 1.0 - magneticDeviationRatio);
        } else if ("INERTIAL_HOLD".equals(state)) {
            long age = lastReliableNorthNs <= 0L
                    ? HOLD_DECAY_NS
                    : Math.max(0L, nowNs - lastReliableNorthNs);
            double fraction = Math.min(1.0, age / (double) HOLD_DECAY_NS);
            confidence = 0.82 - 0.47 * fraction;
        } else if ("ABSOLUTE_ONLY".equals(state)) {
            confidence = magneticDisturbed ? 0.40 : 0.72;
        } else if ("RELATIVE_ONLY".equals(state)) {
            confidence = 0.35;
        } else {
            confidence = 0.0;
        }

        String source;
        if ("MAG_LOCKED".equals(state)) source = "ABS+GAME";
        else if ("INERTIAL_HOLD".equals(state)) source = "GAME+LOCKED_OFFSET";
        else if ("ABSOLUTE_ONLY".equals(state)) source = "ABSOLUTE";
        else if ("RELATIVE_ONLY".equals(state)) source = "GAME";
        else source = "NONE";

        String calibration;
        if (magneticAccuracy <= ACCURACY_LOW) {
            calibration = "MAG_ACCURACY_LOW";
        } else if (magneticDisturbed) {
            calibration = "MAG_DISTURBED";
        } else if (!offsetLocked && hasGame && hasAbsolute) {
            calibration = "LEARNING_OFFSET";
        } else {
            calibration = "OK";
        }

        return new Frame(
                !Float.isNaN(fusedHeadingDeg),
                fusedHeadingDeg,
                absoluteHeadingDeg,
                gameHeadingDeg,
                gameToNorthOffsetDeg,
                magneticNormUt,
                magneticBaselineUt,
                magneticDeviationRatio,
                magneticDisturbed,
                absoluteAccuracy,
                magneticAccuracy,
                state,
                source,
                calibration,
                clamp01(confidence),
                lastReliableNorthNs,
                lastUpdateNs);
    }

    private static float normalize(float deg) {
        if (Float.isNaN(deg) || Float.isInfinite(deg)) return Float.NaN;
        float out = deg % 360f;
        if (out < 0f) out += 360f;
        return out;
    }

    private static float shortestDelta(float fromDeg, float toDeg) {
        float d = normalize(toDeg) - normalize(fromDeg);
        if (d > 180f) d -= 360f;
        if (d < -180f) d += 360f;
        return d;
    }

    private static float circularBlendSigned(float from, float to, float alpha) {
        float delta = to - from;
        if (delta > 180f) delta -= 360f;
        if (delta < -180f) delta += 360f;
        float out = from + delta * Math.max(0f, Math.min(1f, alpha));
        while (out > 180f) out -= 360f;
        while (out < -180f) out += 360f;
        return out;
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    public static final class Frame {
        public final boolean available;
        public final float fusedHeadingDeg;
        public final float absoluteHeadingDeg;
        public final float gameHeadingDeg;
        public final float gameToNorthOffsetDeg;
        public final double magneticNormUt;
        public final double magneticBaselineUt;
        public final double magneticDeviationRatio;
        public final boolean magneticDisturbed;
        public final int absoluteAccuracy;
        public final int magneticAccuracy;
        public final String state;
        public final String source;
        public final String calibration;
        public final double confidence;
        public final long lastReliableNorthNs;
        public final long lastUpdateNs;

        Frame(
                boolean available,
                float fusedHeadingDeg,
                float absoluteHeadingDeg,
                float gameHeadingDeg,
                float gameToNorthOffsetDeg,
                double magneticNormUt,
                double magneticBaselineUt,
                double magneticDeviationRatio,
                boolean magneticDisturbed,
                int absoluteAccuracy,
                int magneticAccuracy,
                String state,
                String source,
                String calibration,
                double confidence,
                long lastReliableNorthNs,
                long lastUpdateNs) {
            this.available = available;
            this.fusedHeadingDeg = fusedHeadingDeg;
            this.absoluteHeadingDeg = absoluteHeadingDeg;
            this.gameHeadingDeg = gameHeadingDeg;
            this.gameToNorthOffsetDeg = gameToNorthOffsetDeg;
            this.magneticNormUt = magneticNormUt;
            this.magneticBaselineUt = magneticBaselineUt;
            this.magneticDeviationRatio = magneticDeviationRatio;
            this.magneticDisturbed = magneticDisturbed;
            this.absoluteAccuracy = absoluteAccuracy;
            this.magneticAccuracy = magneticAccuracy;
            this.state = state;
            this.source = source;
            this.calibration = calibration;
            this.confidence = confidence;
            this.lastReliableNorthNs = lastReliableNorthNs;
            this.lastUpdateNs = lastUpdateNs;
        }

        public String summary() {
            return String.format(Locale.US,
                    "%s · %.1f° · conf %.0f%% · mag %.1fµT · dev %.0f%% · %s",
                    state,
                    available ? fusedHeadingDeg : 0f,
                    confidence * 100.0,
                    Double.isNaN(magneticNormUt) ? 0.0 : magneticNormUt,
                    magneticDeviationRatio * 100.0,
                    calibration);
        }
    }
}
