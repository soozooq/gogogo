package com.zcshou.gogogo;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Lab 13 track-quality auditor.
 *
 * It checks temporal monotonicity, sampling gaps, segment speed, abrupt acceleration
 * and turn-rate discontinuities. The result is intended for GoGoGo's own replay/test
 * data quality and is not an anti-detection or platform-evasion score.
 */
public final class LabMotionQualityEngine {
    private static final double EARTH_RADIUS_M = 6371000.0;

    public Result audit(double[] lats, double[] lngs, long[] times) {
        int n = Math.min(
                lats == null ? 0 : lats.length,
                Math.min(lngs == null ? 0 : lngs.length, times == null ? 0 : times.length));

        if (n < 2) {
            List<String> issues = new ArrayList<>();
            issues.add("样本不足：至少需要 2 个轨迹点");
            return new Result(
                    n, 0L, 0.0, 0.0, 0.0, 0L,
                    0, 0, 0, 0, 0, 0.0,
                    0, "INSUFFICIENT", issues,
                    sha256("insufficient|" + n));
        }

        double totalDistance = 0.0;
        double maxSpeed = 0.0;
        double speedSum = 0.0;
        int speedSamples = 0;
        int nonMonotonic = 0;
        int longGaps = 0;
        int teleportSegments = 0;
        int accelerationSpikes = 0;
        int turnRateSpikes = 0;
        int nearDuplicateSegments = 0;

        double previousSpeed = Double.NaN;
        float previousBearing = Float.NaN;
        long firstTime = times[0];
        long lastTime = times[0];
        List<Long> positiveIntervals = new ArrayList<>();

        for (int i = 1; i < n; i++) {
            long dtMs = times[i] - times[i - 1];
            if (dtMs <= 0L) {
                nonMonotonic++;
                continue;
            }

            positiveIntervals.add(dtMs);
            lastTime = Math.max(lastTime, times[i]);

            if (dtMs > 10000L) {
                longGaps++;
            }

            double distance = distanceMeters(
                    lats[i - 1], lngs[i - 1],
                    lats[i], lngs[i]);
            totalDistance += distance;

            if (distance < 0.20) {
                nearDuplicateSegments++;
            }

            double dtSec = dtMs / 1000.0;
            double speed = distance / Math.max(0.001, dtSec);
            maxSpeed = Math.max(maxSpeed, speed);
            speedSum += speed;
            speedSamples++;

            if (speed > 80.0) {
                teleportSegments++;
            }

            if (!Double.isNaN(previousSpeed)) {
                double accel = Math.abs(speed - previousSpeed) / Math.max(0.001, dtSec);
                if (accel > 15.0) {
                    accelerationSpikes++;
                }
            }

            if (distance >= 0.5) {
                float bearing = bearingDegrees(
                        lats[i - 1], lngs[i - 1],
                        lats[i], lngs[i]);
                if (!Float.isNaN(previousBearing) && speed > 0.5) {
                    double turnRate = Math.abs(shortestAngleDelta(previousBearing, bearing))
                            / Math.max(0.001, dtSec);
                    if (turnRate > 180.0) {
                        turnRateSpikes++;
                    }
                }
                previousBearing = bearing;
            }

            previousSpeed = speed;
        }

        long durationMs = Math.max(0L, lastTime - firstTime);
        double avgSpeed = durationMs > 0L
                ? totalDistance / (durationMs / 1000.0)
                : (speedSamples == 0 ? 0.0 : speedSum / speedSamples);

        long medianIntervalMs = median(positiveIntervals);
        double duplicateRatio = Math.max(0, n - 1) == 0
                ? 0.0
                : nearDuplicateSegments / (double) (n - 1);

        int score = 100;
        score -= Math.min(35, nonMonotonic * 15);
        score -= Math.min(30, teleportSegments * 12);
        score -= Math.min(18, longGaps * 3);
        score -= Math.min(18, accelerationSpikes * 3);
        score -= Math.min(12, turnRateSpikes * 2);

        if (medianIntervalMs > 15000L) score -= 12;
        else if (medianIntervalMs > 5000L) score -= 6;

        if (duplicateRatio > 0.95 && totalDistance > 5.0) score -= 6;

        score = Math.max(0, Math.min(100, score));
        String grade = grade(score);

        List<String> issues = new ArrayList<>();
        if (nonMonotonic > 0) issues.add("时间戳非递增 ×" + nonMonotonic);
        if (longGaps > 0) issues.add("采样间隔 >10s ×" + longGaps);
        if (teleportSegments > 0) issues.add("段速度 >80m/s ×" + teleportSegments);
        if (accelerationSpikes > 0) issues.add("加速度突变 >15m/s² ×" + accelerationSpikes);
        if (turnRateSpikes > 0) issues.add("转向变化 >180°/s ×" + turnRateSpikes);
        if (medianIntervalMs > 5000L) issues.add("采样中位间隔偏大：" + medianIntervalMs + "ms");
        if (issues.isEmpty()) issues.add("未发现明显时间/运动连续性异常");

        String canonical = String.format(Locale.US,
                "n=%d|dur=%d|dist=%.3f|avg=%.4f|max=%.4f|median=%d|nonmono=%d|gaps=%d|"
                        + "teleport=%d|accel=%d|turn=%d|dup=%.6f|score=%d|grade=%s",
                n, durationMs, totalDistance, avgSpeed, maxSpeed, medianIntervalMs,
                nonMonotonic, longGaps, teleportSegments, accelerationSpikes,
                turnRateSpikes, duplicateRatio, score, grade);

        return new Result(
                n,
                durationMs,
                totalDistance,
                avgSpeed,
                maxSpeed,
                medianIntervalMs,
                nonMonotonic,
                longGaps,
                teleportSegments,
                accelerationSpikes,
                turnRateSpikes,
                duplicateRatio,
                score,
                grade,
                issues,
                sha256(canonical));
    }

    private static String grade(int score) {
        if (score >= 95) return "A+";
        if (score >= 88) return "A";
        if (score >= 78) return "B";
        if (score >= 65) return "C";
        if (score >= 50) return "D";
        return "F";
    }

    private static long median(List<Long> values) {
        if (values == null || values.isEmpty()) return 0L;
        List<Long> copy = new ArrayList<>(values);
        Collections.sort(copy);
        int mid = copy.size() / 2;
        if ((copy.size() & 1) == 1) return copy.get(mid);
        return (copy.get(mid - 1) + copy.get(mid)) / 2L;
    }

    private static double distanceMeters(double lat1, double lon1, double lat2, double lon2) {
        double p1 = Math.toRadians(lat1);
        double p2 = Math.toRadians(lat2);
        double dp = Math.toRadians(lat2 - lat1);
        double dl = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dp / 2.0) * Math.sin(dp / 2.0)
                + Math.cos(p1) * Math.cos(p2)
                * Math.sin(dl / 2.0) * Math.sin(dl / 2.0);
        return EARTH_RADIUS_M * 2.0
                * Math.atan2(Math.sqrt(a), Math.sqrt(Math.max(0.0, 1.0 - a)));
    }

    private static float bearingDegrees(double lat1, double lon1, double lat2, double lon2) {
        double p1 = Math.toRadians(lat1);
        double p2 = Math.toRadians(lat2);
        double dl = Math.toRadians(lon2 - lon1);
        double y = Math.sin(dl) * Math.cos(p2);
        double x = Math.cos(p1) * Math.sin(p2)
                - Math.sin(p1) * Math.cos(p2) * Math.cos(dl);
        double result = Math.toDegrees(Math.atan2(y, x));
        if (result < 0.0) result += 360.0;
        return (float) result;
    }

    private static float shortestAngleDelta(float fromDeg, float toDeg) {
        float delta = toDeg - fromDeg;
        if (delta > 180f) delta -= 360f;
        if (delta < -180f) delta += 360f;
        return delta;
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) out.append(String.format(Locale.US, "%02x", b & 0xff));
            return out.toString();
        } catch (Throwable t) {
            return Integer.toHexString(value.hashCode());
        }
    }

    public static final class Result {
        public final int pointCount;
        public final long durationMs;
        public final double distanceMeters;
        public final double averageSpeedMps;
        public final double maxSegmentSpeedMps;
        public final long medianIntervalMs;
        public final int nonMonotonicTimestamps;
        public final int longGapCount;
        public final int teleportSegmentCount;
        public final int accelerationSpikeCount;
        public final int turnRateSpikeCount;
        public final double nearDuplicateRatio;
        public final int score;
        public final String grade;
        public final List<String> issues;
        public final String digestSha256;

        Result(
                int pointCount,
                long durationMs,
                double distanceMeters,
                double averageSpeedMps,
                double maxSegmentSpeedMps,
                long medianIntervalMs,
                int nonMonotonicTimestamps,
                int longGapCount,
                int teleportSegmentCount,
                int accelerationSpikeCount,
                int turnRateSpikeCount,
                double nearDuplicateRatio,
                int score,
                String grade,
                List<String> issues,
                String digestSha256) {
            this.pointCount = pointCount;
            this.durationMs = durationMs;
            this.distanceMeters = distanceMeters;
            this.averageSpeedMps = averageSpeedMps;
            this.maxSegmentSpeedMps = maxSegmentSpeedMps;
            this.medianIntervalMs = medianIntervalMs;
            this.nonMonotonicTimestamps = nonMonotonicTimestamps;
            this.longGapCount = longGapCount;
            this.teleportSegmentCount = teleportSegmentCount;
            this.accelerationSpikeCount = accelerationSpikeCount;
            this.turnRateSpikeCount = turnRateSpikeCount;
            this.nearDuplicateRatio = nearDuplicateRatio;
            this.score = score;
            this.grade = grade;
            this.issues = Collections.unmodifiableList(new ArrayList<>(issues));
            this.digestSha256 = digestSha256;
        }

        public String shortDigest() {
            return digestSha256 == null || digestSha256.length() <= 16
                    ? String.valueOf(digestSha256)
                    : digestSha256.substring(0, 16);
        }
    }
}
