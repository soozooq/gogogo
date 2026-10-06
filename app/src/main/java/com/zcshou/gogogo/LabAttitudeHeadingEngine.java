package com.zcshou.gogogo;

import java.util.Locale;

/**
 * Lab 15 posture-aware heading candidate selection inspired by attitude-unconstrained
 * smartphone navigation research.
 *
 * Input rotation matrices must already be remapped to the current display orientation.
 * The engine evaluates the horizontal projection of the screen-top axis (+Y). When that
 * axis becomes close to vertical, it falls back to the screen-forward axis (-Z), choosing
 * the sign that preserves continuity with the existing north-referenced heading.
 *
 * The output is a diagnostic/candidate heading for GoGoGo. It does not alter Android
 * sensor events or another application's coordinate frame.
 */
public final class LabAttitudeHeadingEngine {
    private static final double AXIS_USABLE = 0.30;

    private float lastCandidateDeg = Float.NaN;

    public synchronized Frame update(
            float[] rotationMatrix,
            float pitchDeg,
            float rollDeg,
            float northReferenceDeg) {
        if (rotationMatrix == null || rotationMatrix.length < 9) {
            return unavailable(pitchDeg, rollDeg);
        }

        // R transforms device vectors into world ENU coordinates. Columns therefore
        // describe device axes in world space. World X=East, Y=North, Z=Up.
        double topEast = rotationMatrix[1];
        double topNorth = rotationMatrix[4];
        double topUp = rotationMatrix[7];

        double backEast = rotationMatrix[2];
        double backNorth = rotationMatrix[5];
        double backUp = rotationMatrix[8];

        double topHorizontal = Math.hypot(topEast, topNorth);
        double normalHorizontal = Math.hypot(backEast, backNorth);

        String posture;
        double absNormalUp = Math.abs(backUp);
        double absTopUp = Math.abs(topUp);
        if (absNormalUp >= 0.82) {
            posture = "FLAT";
        } else if (absTopUp >= 0.78 || absNormalUp <= 0.30) {
            posture = "UPRIGHT";
        } else {
            posture = "TILTED";
        }

        float topHeading = heading(topEast, topNorth);
        float screenForward = heading(-backEast, -backNorth);
        float screenBackward = heading(backEast, backNorth);

        float target = !Float.isNaN(lastCandidateDeg)
                ? lastCandidateDeg
                : northReferenceDeg;

        String axis;
        float selected;
        double projection;

        if (topHorizontal >= AXIS_USABLE
                && (topHorizontal >= normalHorizontal * 0.72 || "FLAT".equals(posture))) {
            axis = "SCREEN_TOP";
            selected = topHeading;
            projection = topHorizontal;
        } else if (normalHorizontal >= AXIS_USABLE) {
            axis = "SCREEN_FORWARD";
            if (!Float.isNaN(target)) {
                float dForward = Math.abs(shortestDelta(target, screenForward));
                float dBackward = Math.abs(shortestDelta(target, screenBackward));
                selected = dForward <= dBackward ? screenForward : screenBackward;
                if (dBackward < dForward) {
                    axis = "SCREEN_FORWARD_FLIPPED";
                }
            } else {
                selected = screenForward;
            }
            projection = normalHorizontal;
        } else if (topHorizontal > 0.05) {
            axis = "SCREEN_TOP_WEAK";
            selected = topHeading;
            projection = topHorizontal;
        } else {
            return unavailable(pitchDeg, rollDeg);
        }

        if (!Float.isNaN(lastCandidateDeg)) {
            // Circular low-pass. Strong projected axes need less damping.
            float alpha = (float) Math.max(0.18, Math.min(0.60, projection * 0.55));
            selected = circularLerp(lastCandidateDeg, selected, alpha);
        }

        lastCandidateDeg = normalize(selected);

        double postureConfidence = Math.max(0.0, Math.min(1.0, projection));
        if ("TILTED".equals(posture)) postureConfidence *= 0.92;
        if (axis.endsWith("_WEAK")) postureConfidence *= 0.55;

        return new Frame(
                true,
                lastCandidateDeg,
                topHeading,
                screenForward,
                pitchDeg,
                rollDeg,
                posture,
                axis,
                topHorizontal,
                normalHorizontal,
                postureConfidence);
    }

    public synchronized void reset() {
        lastCandidateDeg = Float.NaN;
    }

    private static Frame unavailable(float pitchDeg, float rollDeg) {
        return new Frame(
                false,
                Float.NaN,
                Float.NaN,
                Float.NaN,
                pitchDeg,
                rollDeg,
                "UNKNOWN",
                "NONE",
                0.0,
                0.0,
                0.0);
    }

    private static float heading(double east, double north) {
        if (Math.hypot(east, north) < 1e-6) return Float.NaN;
        double deg = Math.toDegrees(Math.atan2(east, north));
        if (deg < 0.0) deg += 360.0;
        return (float) deg;
    }

    private static float normalize(float value) {
        if (Float.isNaN(value) || Float.isInfinite(value)) return Float.NaN;
        float out = value % 360f;
        if (out < 0f) out += 360f;
        return out;
    }

    private static float shortestDelta(float fromDeg, float toDeg) {
        float delta = normalize(toDeg) - normalize(fromDeg);
        if (delta > 180f) delta -= 360f;
        if (delta < -180f) delta += 360f;
        return delta;
    }

    private static float circularLerp(float fromDeg, float toDeg, float alpha) {
        return normalize(fromDeg + shortestDelta(fromDeg, toDeg)
                * Math.max(0f, Math.min(1f, alpha)));
    }

    public static final class Frame {
        public final boolean available;
        public final float headingDeg;
        public final float topAxisHeadingDeg;
        public final float screenForwardHeadingDeg;
        public final float pitchDeg;
        public final float rollDeg;
        public final String posture;
        public final String axis;
        public final double topHorizontalProjection;
        public final double normalHorizontalProjection;
        public final double confidence;

        Frame(
                boolean available,
                float headingDeg,
                float topAxisHeadingDeg,
                float screenForwardHeadingDeg,
                float pitchDeg,
                float rollDeg,
                String posture,
                String axis,
                double topHorizontalProjection,
                double normalHorizontalProjection,
                double confidence) {
            this.available = available;
            this.headingDeg = headingDeg;
            this.topAxisHeadingDeg = topAxisHeadingDeg;
            this.screenForwardHeadingDeg = screenForwardHeadingDeg;
            this.pitchDeg = pitchDeg;
            this.rollDeg = rollDeg;
            this.posture = posture;
            this.axis = axis;
            this.topHorizontalProjection = topHorizontalProjection;
            this.normalHorizontalProjection = normalHorizontalProjection;
            this.confidence = confidence;
        }

        public String summary() {
            return String.format(Locale.US,
                    "%s · %s · %.1f° · conf %.0f%% · pitch %.1f° · roll %.1f°",
                    posture,
                    axis,
                    available ? headingDeg : 0f,
                    confidence * 100.0,
                    pitchDeg,
                    rollDeg);
        }
    }
}
