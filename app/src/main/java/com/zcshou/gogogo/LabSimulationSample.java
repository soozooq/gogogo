package com.zcshou.gogogo;

import java.util.Locale;

/**
 * Canonical synthetic location sample produced by Lab 21 simulation backends.
 */
public final class LabSimulationSample {
    public final double latitude;
    public final double longitude;
    public final double altitude;
    public final double speedMps;
    public final float bearingDegrees;
    public final float accuracyMeters;
    public final String source;
    public final long elapsedRealtimeMs;

    public LabSimulationSample(
            double latitude,
            double longitude,
            double altitude,
            double speedMps,
            float bearingDegrees,
            float accuracyMeters,
            String source,
            long elapsedRealtimeMs) {
        this.latitude = latitude;
        this.longitude = normalizeLongitude(longitude);
        this.altitude = altitude;
        this.speedMps = Math.max(0.0, speedMps);
        this.bearingDegrees = normalizeHeading(bearingDegrees);
        this.accuracyMeters = Math.max(0.1f, accuracyMeters);
        this.source = source == null ? "UNKNOWN" : source;
        this.elapsedRealtimeMs = Math.max(0L, elapsedRealtimeMs);
    }

    public String summary() {
        return String.format(
                Locale.US,
                "%s · %.6f, %.6f · %.2fm/s · %.1f°",
                source,
                latitude,
                longitude,
                speedMps,
                bearingDegrees);
    }

    private static float normalizeHeading(float value) {
        float out = value % 360f;
        if (out < 0f) out += 360f;
        return out;
    }

    private static double normalizeLongitude(double value) {
        double out = value;
        while (out > 180.0) out -= 360.0;
        while (out < -180.0) out += 360.0;
        return out;
    }
}
