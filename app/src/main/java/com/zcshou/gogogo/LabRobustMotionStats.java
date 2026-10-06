package com.zcshou.gogogo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Robust statistics used by Lab 13 for experiment-quality diagnostics.
 *
 * Median/MAD resists isolated spikes better than mean/stddev. The CUSUM signal is a
 * lightweight change-point indicator for replay analysis; it is diagnostic only.
 */
public final class LabRobustMotionStats {
    private LabRobustMotionStats() {
    }

    public static Result analyze(List<Double> samples) {
        if (samples == null || samples.isEmpty()) {
            return new Result(0.0, 0.0, 0, 0);
        }

        List<Double> clean = new ArrayList<>();
        for (Double value : samples) {
            if (value != null && !Double.isNaN(value) && !Double.isInfinite(value)) {
                clean.add(value);
            }
        }
        if (clean.isEmpty()) {
            return new Result(0.0, 0.0, 0, 0);
        }

        double median = median(clean);

        List<Double> deviations = new ArrayList<>(clean.size());
        for (double value : clean) {
            deviations.add(Math.abs(value - median));
        }
        double mad = median(deviations);

        // 1.4826 makes MAD comparable to sigma for normally distributed samples.
        double robustSigma = Math.max(0.05, mad * 1.4826);
        int outliers = 0;
        for (double value : clean) {
            double robustZ = Math.abs(value - median) / robustSigma;
            if (robustZ > 6.0) outliers++;
        }

        // Two-sided CUSUM. It intentionally uses a robust center/scale so a single
        // bad point does not redefine the baseline for the rest of the replay.
        double allowance = robustSigma * 0.5;
        double threshold = robustSigma * 6.0;
        double positive = 0.0;
        double negative = 0.0;
        int changePoints = 0;

        for (double value : clean) {
            double centered = value - median;
            positive = Math.max(0.0, positive + centered - allowance);
            negative = Math.min(0.0, negative + centered + allowance);

            if (positive > threshold || negative < -threshold) {
                changePoints++;
                positive = 0.0;
                negative = 0.0;
            }
        }

        return new Result(median, mad, outliers, changePoints);
    }

    private static double median(List<Double> values) {
        List<Double> copy = new ArrayList<>(values);
        Collections.sort(copy);
        int mid = copy.size() / 2;
        if ((copy.size() & 1) == 1) return copy.get(mid);
        return (copy.get(mid - 1) + copy.get(mid)) / 2.0;
    }

    public static final class Result {
        public final double median;
        public final double mad;
        public final int robustOutlierCount;
        public final int cusumChangePointCount;

        Result(double median, double mad, int robustOutlierCount, int cusumChangePointCount) {
            this.median = median;
            this.mad = mad;
            this.robustOutlierCount = robustOutlierCount;
            this.cusumChangePointCount = cusumChangePointCount;
        }
    }
}
