package com.zcshou.gogogo;

import java.util.Locale;

/**
 * Lab 22 time-domain replay engine for timestamped GPX traces.
 *
 * The engine is pure Java and does not publish Android locations itself.
 */
public final class LabTimedReplayEngine {
    private double[] lats;
    private double[] lngs;
    private double[] alts;
    private long[] relativeTimesMs;
    private long durationMs;
    private double playheadMs;
    private double playbackMultiplier = 1.0;
    private boolean active;
    private boolean finished;

    public void load(
            double[] latitudes,
            double[] longitudes,
            double[] altitudes,
            long[] timestampsMs) {
        // Validate and prepare a replacement without changing an active replay.
        if (latitudes == null || longitudes == null || timestampsMs == null
                || latitudes.length < 2 || latitudes.length > 5000
                || longitudes.length != latitudes.length
                || timestampsMs.length != latitudes.length
                || (altitudes != null && altitudes.length != latitudes.length)) {
            throw new IllegalArgumentException("replay arrays are required (2..5000 points)");
        }
        double[] nextLats = latitudes.clone();
        double[] nextLngs = longitudes.clone();
        double[] nextAlts = altitudes == null ? new double[nextLats.length] : altitudes.clone();
        long[] nextTimes = timestampsMs.clone();
        validate(nextLats, nextLngs, nextAlts, nextTimes);
        long[] nextRelativeTimes = new long[nextLats.length];
        long base = nextTimes[0];
        try {
            for (int i = 0; i < nextTimes.length; i++) {
                nextRelativeTimes[i] = Math.subtractExact(nextTimes[i], base);
            }
        } catch (ArithmeticException overflow) {
            throw new IllegalArgumentException("replay duration is too large", overflow);
        }
        long nextDuration = nextRelativeTimes[nextRelativeTimes.length - 1];
        if (nextDuration <= 0L) {
            throw new IllegalArgumentException("replay duration must be positive");
        }
        lats = nextLats;
        lngs = nextLngs;
        alts = nextAlts;
        relativeTimesMs = nextRelativeTimes;
        durationMs = nextDuration;

        playheadMs = 0.0;
        playbackMultiplier = 1.0;
        active = true;
        finished = false;
    }

    public Frame advance(double dtSeconds, double multiplier) {
        if (!Double.isFinite(dtSeconds) || !Double.isFinite(multiplier)) {
            throw new IllegalArgumentException("non-finite replay timing");
        }
        if (!active || lats == null) return null;

        double dtMs = Math.max(0.0, Math.min(1000.0, dtSeconds * 1000.0));
        double speedFactor = Math.max(0.0, Math.min(16.0, multiplier));
        playbackMultiplier = speedFactor;
        playheadMs += dtMs * speedFactor;

        if (playheadMs >= durationMs) {
            playheadMs = durationMs;
            finished = true;
            active = false;
        }

        return sampleAt(playheadMs);
    }

    public Frame current() {
        if (lats == null) return null;
        return sampleAt(playheadMs);
    }

    public boolean isActive() {
        return active;
    }

    public boolean isFinished() {
        return finished;
    }

    public double progressFraction() {
        if (durationMs <= 0L) return 0.0;
        return clamp(playheadMs / durationMs, 0.0, 1.0);
    }

    public long durationMs() {
        return durationMs;
    }

    public long playheadMs() {
        return Math.round(playheadMs);
    }

    public void stop() {
        active = false;
    }

    private Frame sampleAt(double timeMs) {
        int count = relativeTimesMs.length;
        if (timeMs <= 0.0) {
            return frameForSegment(0, 1, 0.0, 0.0);
        }
        if (timeMs >= durationMs) {
            int last = count - 1;
            double bearing = bearingDegrees(
                    lats[last - 1], lngs[last - 1],
                    lats[last], lngs[last]);
            return new Frame(
                    lats[last],
                    lngs[last],
                    alts[last],
                    0.0,
                    (float) bearing,
                    progressFraction(),
                    last);
        }

        int hi = upperBound(relativeTimesMs, timeMs);
        int lo = Math.max(0, hi - 1);
        long t0 = relativeTimesMs[lo];
        long t1 = relativeTimesMs[hi];
        double span = Math.max(1.0, t1 - t0);
        double fraction = clamp((timeMs - t0) / span, 0.0, 1.0);

        double segmentDistance = distanceMeters(
                lats[lo], lngs[lo],
                lats[hi], lngs[hi]);
        double segmentSpeed = segmentDistance / (span / 1000.0);

        return frameForSegment(lo, hi, fraction, segmentSpeed);
    }

    private Frame frameForSegment(
            int lo,
            int hi,
            double fraction,
            double speedMps) {
        double lat = lerp(lats[lo], lats[hi], fraction);
        double lng = interpolateLongitude(lngs[lo], lngs[hi], fraction);
        double alt = lerp(alts[lo], alts[hi], fraction);
        float bearing = (float) bearingDegrees(
                lats[lo], lngs[lo],
                lats[hi], lngs[hi]);

        return new Frame(
                lat,
                lng,
                alt,
                Math.max(0.0, speedMps * playbackMultiplier),
                bearing,
                progressFraction(),
                hi);
    }

    private static void validate(
            double[] lats,
            double[] lngs,
            double[] alts,
            long[] timestampsMs) {
        if (lats == null || lngs == null || timestampsMs == null) {
            throw new IllegalArgumentException("replay arrays are required");
        }
        if (lats.length < 2
                || lats.length != lngs.length
                || lats.length != timestampsMs.length) {
            throw new IllegalArgumentException("replay arrays must have equal length >= 2");
        }
        if (alts != null && alts.length != lats.length) {
            throw new IllegalArgumentException("altitude array length mismatch");
        }

        long previous = Long.MIN_VALUE;
        for (int i = 0; i < lats.length; i++) {
            if (!LocationPublishGate.validTarget(lats[i], lngs[i], alts[i])) {
                throw new IllegalArgumentException("invalid replay coordinate or altitude");
            }
            if (timestampsMs[i] < previous) {
                throw new IllegalArgumentException("timestamps must be monotonic");
            }
            previous = timestampsMs[i];
        }
    }

    private static int upperBound(long[] values, double value) {
        int lo = 0;
        int hi = values.length - 1;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (values[mid] <= value) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }
        return lo;
    }

    private static double interpolateLongitude(double from, double to, double fraction) {
        double delta = to - from;
        if (delta > 180.0) delta -= 360.0;
        if (delta < -180.0) delta += 360.0;
        double out = from + delta * fraction;
        return LocationGeometry.normalizeLongitude(out);
    }

    private static double lerp(double a, double b, double t) {
        return a * (1.0 - t) + b * t;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double distanceMeters(
            double lat1,
            double lon1,
            double lat2,
            double lon2) {
        return LocationGeometry.distanceMeters(lat1, lon1, lat2, lon2);
    }

    private static double bearingDegrees(
            double lat1,
            double lon1,
            double lat2,
            double lon2) {
        double p1 = Math.toRadians(lat1);
        double p2 = Math.toRadians(lat2);
        double dl = Math.toRadians(lon2 - lon1);
        double y = Math.sin(dl) * Math.cos(p2);
        double x = Math.cos(p1) * Math.sin(p2)
                - Math.sin(p1) * Math.cos(p2) * Math.cos(dl);
        double bearing = Math.toDegrees(Math.atan2(y, x));
        bearing %= 360.0;
        if (bearing < 0.0) bearing += 360.0;
        return bearing;
    }

    public static final class Frame {
        public final double latitude;
        public final double longitude;
        public final double altitude;
        public final double speedMps;
        public final float bearingDegrees;
        public final double progressFraction;
        public final int targetPointIndex;

        Frame(
                double latitude,
                double longitude,
                double altitude,
                double speedMps,
                float bearingDegrees,
                double progressFraction,
                int targetPointIndex) {
            this.latitude = latitude;
            this.longitude = longitude;
            this.altitude = altitude;
            this.speedMps = speedMps;
            this.bearingDegrees = bearingDegrees;
            this.progressFraction = progressFraction;
            this.targetPointIndex = targetPointIndex;
        }

        public String summary() {
            return String.format(
                    Locale.US,
                    "REPLAY · %.1f%% · %.2fm/s · point=%d",
                    progressFraction * 100.0,
                    speedMps,
                    targetPointIndex);
        }
    }
}
