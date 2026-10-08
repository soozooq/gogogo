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
        int freshStreams = 0;
        int availableStreams = 0;
        int availableSnapshots = 0;
        int freshSnapshots = 0;
        int mockMarked = 0;
        double maxSeparation = 0.0;

        Sample reference = null;
        List<String> staleStreams = new ArrayList<>();
        List<String> staleSnapshots = new ArrayList<>();

        for (Sample sample : samples) {
            if (sample == null || !sample.available) continue;
            available++;

            if (sample.stream) {
                availableStreams++;
                if (sample.ageMs >= 0L && sample.ageMs <= 1000L) {
                    freshStreams++;
                } else {
                    staleStreams.add(sample.name);
                }
            } else {
                availableSnapshots++;
                // Snapshot APIs naturally age after the one-shot completes.
                // Treat <=30s as reasonably recent diagnostics, but do not use
                // snapshot freshness to downgrade the live-stream grade.
                if (sample.ageMs >= 0L && sample.ageMs <= 30000L) {
                    freshSnapshots++;
                } else {
                    staleSnapshots.add(sample.name);
                }
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
        if (availableStreams >= 3
                && freshStreams >= 3
                && maxSeparation <= 10.0) {
            grade = "CONSISTENT";
        } else if (freshStreams >= 2
                && maxSeparation <= 50.0) {
            grade = "PARTIAL";
        } else {
            grade = "DEGRADED";
        }

        return new Result(
                grade,
                available,
                availableStreams,
                freshStreams,
                availableSnapshots,
                freshSnapshots,
                mockMarked,
                maxSeparation,
                staleStreams,
                staleSnapshots);
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
        public final boolean stream;

        public Sample(
                String name,
                boolean available,
                double latitude,
                double longitude,
                long ageMs,
                boolean mockMarked,
                boolean stream) {
            this.name = name == null ? "unknown" : name;
            this.available = available;
            this.latitude = latitude;
            this.longitude = longitude;
            this.ageMs = ageMs;
            this.mockMarked = mockMarked;
            this.stream = stream;
        }
    }

    public static final class Result {
        public final String grade;
        public final int availableChannels;
        public final int availableStreams;
        public final int freshStreams;
        public final int availableSnapshots;
        public final int freshSnapshots;
        public final int mockMarkedChannels;
        public final double maxSeparationMeters;
        public final List<String> staleStreams;
        public final List<String> staleSnapshots;

        Result(
                String grade,
                int availableChannels,
                int availableStreams,
                int freshStreams,
                int availableSnapshots,
                int freshSnapshots,
                int mockMarkedChannels,
                double maxSeparationMeters,
                List<String> staleStreams,
                List<String> staleSnapshots) {
            this.grade = grade;
            this.availableChannels = availableChannels;
            this.availableStreams = availableStreams;
            this.freshStreams = freshStreams;
            this.availableSnapshots = availableSnapshots;
            this.freshSnapshots = freshSnapshots;
            this.mockMarkedChannels = mockMarkedChannels;
            this.maxSeparationMeters = maxSeparationMeters;
            this.staleStreams = Collections.unmodifiableList(new ArrayList<>(staleStreams));
            this.staleSnapshots = Collections.unmodifiableList(new ArrayList<>(staleSnapshots));
        }
    }
}
