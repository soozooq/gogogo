package com.zcshou.gogogo;

import android.os.SystemClock;

/**
 * Thread-safe state backend used by FIXED / ROUTE / ROAM in Lab 21.
 *
 * This is deliberately small: movement engines own behavior, while this class only
 * exposes their latest canonical sample through one common contract.
 */
public final class LabMutableSimulationBackend implements LabSimulationBackend {
    private final String id;
    private volatile boolean active;
    private volatile LabSimulationSample sample;

    public LabMutableSimulationBackend(String id) {
        this.id = id == null ? "UNKNOWN" : id;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public boolean isActive() {
        return active;
    }

    @Override
    public LabSimulationSample snapshot() {
        return sample;
    }

    public void activate(
            double latitude,
            double longitude,
            double altitude,
            double speedMps,
            float bearingDegrees,
            float accuracyMeters) {
        active = true;
        update(
                latitude,
                longitude,
                altitude,
                speedMps,
                bearingDegrees,
                accuracyMeters);
    }

    public void update(
            double latitude,
            double longitude,
            double altitude,
            double speedMps,
            float bearingDegrees,
            float accuracyMeters) {
        sample = new LabSimulationSample(
                latitude,
                longitude,
                altitude,
                speedMps,
                bearingDegrees,
                accuracyMeters,
                id,
                SystemClock.elapsedRealtime());
    }

    public void deactivate() {
        active = false;
    }
}
