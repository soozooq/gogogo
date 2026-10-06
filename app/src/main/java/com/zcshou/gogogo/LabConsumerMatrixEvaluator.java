package com.zcshou.gogogo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Pure-Java evaluator for Lab 16 consumer observations.
 */
public final class LabConsumerMatrixEvaluator {
    private LabConsumerMatrixEvaluator() {}

    public static Result evaluate(List<Sample> samples) {
        if (samples == null) samples = Collections.emptyList();

        int available = 0;
        int fresh = 0;
        int mockMarked = 0;
        double maxSeparation = 0.0;

        Sample reference = null;
        List<String> stale = new ArrayList<>();

        for (Sample sample : samples) {
            if (sample == null || !sample.available) continue;
            available++;

            if (sample.ageMs >= 0L && sample.ageMs <= 1000L) {
                fresh++;
            } else {
                stale.add(sample.name);
            }

            if (sample.mockMarked) mockMarked++;

            if (reference == null) {
                reference = sample;
            } else {
                maxSeparation = Math.max(
                        maxSeparation,
                        distanceMeters(
                                reference.latitude,
                                reference.longitude,
                                sample.latitude,
                                sample.longitude));
            }
        }

        String grade;
        if (fresh >= 4 && maxSeparation <= 10.0) {
            grade = "CONSISTENT";
        } else if (fresh >= 2 && maxSeparation <= 50.0) {
            grade = "PARTIAL";
        } else {
            grade = "DEGRADED";
        }

        return new Result(
                grade,
                available,
                fresh,
                mockMarked,
                maxSeparation,
                stale);
    }

    private static double distanceMeters(
            double lat1,
            double lon1,
            double lat2,
            double lon2) {
        final double earth = 6371000.0;
        double p1 = Math.toRadians(lat1);
        double p2 = Math.toRadians(lat2);
        double dp = Math.toRadians(lat2 - lat1);
        double dl = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dp / 2.0) * Math.sin(dp / 2.0)
                + Math.cos(p1) * Math.cos(p2)
                * Math.sin(dl / 2.0) * Math.sin(dl / 2.0);
        return earth * 2.0
                * Math.atan2(Math.sqrt(a), Math.sqrt(Math.max(0.0, 1.0 - a)));
    }

    public static final class Sample {
        public final String name;
        public final boolean available;
        public final double latitude;
        public final double longitude;
        public final long ageMs;
        public final boolean mockMarked;

        public Sample(
                String name,
                boolean available,
                double latitude,
                double longitude,
                long ageMs,
                boolean mockMarked) {
            this.name = name == null ? "unknown" : name;
            this.available = available;
            this.latitude = latitude;
            this.longitude = longitude;
            this.ageMs = ageMs;
            this.mockMarked = mockMarked;
        }
    }

    public static final class Result {
        public final String grade;
        public final int availableChannels;
        public final int freshChannels;
        public final int mockMarkedChannels;
        public final double maxSeparationMeters;
        public final List<String> staleChannels;

        Result(
                String grade,
                int availableChannels,
                int freshChannels,
                int mockMarkedChannels,
                double maxSeparationMeters,
                List<String> staleChannels) {
            this.grade = grade;
            this.availableChannels = availableChannels;
            this.freshChannels = freshChannels;
            this.mockMarkedChannels = mockMarkedChannels;
            this.maxSeparationMeters = maxSeparationMeters;
            this.staleChannels = Collections.unmodifiableList(new ArrayList<>(staleChannels));
        }
    }
}
