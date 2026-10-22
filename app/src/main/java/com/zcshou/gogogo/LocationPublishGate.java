package com.zcshou.gogogo;

import java.util.function.Supplier;

/**
 * Service-local publication boundary. A created/bound service has no target yet.
 * Writers synchronize on this gate while changing a complete target/motion state;
 * captureIfReady uses the same monitor to produce one immutable provider frame.
 * Provider IPC must happen after capture, outside this monitor.
 */
public final class LocationPublishGate {
    private boolean initialized;
    private boolean closed;

    public static boolean validTarget(double latitude, double longitude, double altitude) {
        return Double.isFinite(latitude) && latitude >= -90.0 && latitude <= 90.0
                && Double.isFinite(longitude) && longitude >= -180.0 && longitude <= 180.0
                && Double.isFinite(altitude);
    }

    public static boolean validRoute(double[] latitudes, double[] longitudes) {
        if (latitudes == null || longitudes == null || latitudes.length < 2
                || latitudes.length > 5000 || latitudes.length != longitudes.length) {
            return false;
        }
        for (int i = 0; i < latitudes.length; i++) {
            if (!validTarget(latitudes[i], longitudes[i], 0.0)) return false;
        }
        return true;
    }

    /** Call only after the complete, validated target has been written. */
    public synchronized boolean initialize(double latitude, double longitude, double altitude) {
        if (closed || !validTarget(latitude, longitude, altitude)) return false;
        initialized = true;
        return true;
    }

    public synchronized boolean isReady() {
        return initialized && !closed;
    }

    public synchronized boolean isClosed() {
        return closed;
    }

    /** null means no authorized target, or this service has stopped. */
    public synchronized <T> T captureIfReady(Supplier<T> capture) {
        return isReady() ? capture.get() : null;
    }

    public synchronized void close() {
        closed = true;
    }
}
