package com.zcshou.gogogo;

/** WGS84 spherical geometry shared by the location service and trace replay. */
public final class LocationGeometry {
    private static final double EARTH_RADIUS_METERS = 6371000.0;

    private LocationGeometry() {}

    public static double normalizeLongitude(double longitude) {
        if (!Double.isFinite(longitude)) throw new IllegalArgumentException("invalid longitude");
        double normalized = longitude % 360.0;
        if (normalized > 180.0) normalized -= 360.0;
        if (normalized < -180.0) normalized += 360.0;
        return normalized;
    }

    public static double distanceMeters(double lat1, double lon1, double lat2, double lon2) {
        double p1 = Math.toRadians(lat1);
        double p2 = Math.toRadians(lat2);
        double dp = Math.toRadians(lat2 - lat1);
        double dl = Math.toRadians(lon2 - lon1);
        double h = Math.sin(dp / 2.0) * Math.sin(dp / 2.0)
                + Math.cos(p1) * Math.cos(p2) * Math.sin(dl / 2.0) * Math.sin(dl / 2.0);
        // Floating-point rounding at antipodes can put h just above 1.
        h = Math.max(0.0, Math.min(1.0, h));
        return EARTH_RADIUS_METERS * 2.0 * Math.atan2(Math.sqrt(h), Math.sqrt(1.0 - h));
    }

    /** Returns latitude, longitude after a finite distance on the sphere. */
    public static double[] destination(double latitude, double longitude,
                                       double bearingDegrees, double distanceMeters) {
        if (!LocationPublishGate.validTarget(latitude, longitude, 0.0)
                || !Double.isFinite(bearingDegrees)
                || !Double.isFinite(distanceMeters) || distanceMeters < 0.0) {
            throw new IllegalArgumentException("invalid destination input");
        }
        double angular = distanceMeters / EARTH_RADIUS_METERS;
        double bearing = Math.toRadians(bearingDegrees % 360.0);
        double lat = Math.toRadians(latitude);
        double sinLat = Math.sin(lat);
        double cosLat = Math.cos(lat);
        double sinNextLat = sinLat * Math.cos(angular)
                + cosLat * Math.sin(angular) * Math.cos(bearing);
        double nextLat = Math.asin(Math.max(-1.0, Math.min(1.0, sinNextLat)));
        double nextLon = Math.toRadians(longitude) + Math.atan2(
                Math.sin(bearing) * Math.sin(angular) * cosLat,
                Math.cos(angular) - sinLat * Math.sin(nextLat));
        return new double[] {Math.toDegrees(nextLat), normalizeLongitude(Math.toDegrees(nextLon))};
    }
}
