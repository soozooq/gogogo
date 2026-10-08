package com.zcshou.gogogo;

import java.util.Locale;

/** Pure helpers for diagnostic comparison. No location provider writes. */
public final class TencentProbeMath {
    private TencentProbeMath() {}

    public static boolean validCoordinate(double latitude, double longitude) {
        return Double.isFinite(latitude) && Double.isFinite(longitude)
                && latitude >= -90.0 && latitude <= 90.0
                && longitude >= -180.0 && longitude <= 180.0;
    }

    /**
     * (0,0) is a legitimate place on Earth, but is also a common uninitialized
     * sentinel. Flag it for investigation rather than claiming it is always bad.
     */
    public static boolean suspiciousZeroPair(double latitude, double longitude) {
        return latitude == 0.0 && longitude == 0.0;
    }

    public static double distanceMeters(double lat1, double lon1,
                                        double lat2, double lon2) {
        if (!validCoordinate(lat1, lon1) || !validCoordinate(lat2, lon2)) {
            return Double.NaN;
        }
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.pow(Math.sin(dLat / 2.0), 2)
                + Math.cos(Math.toRadians(lat1))
                * Math.cos(Math.toRadians(lat2))
                * Math.pow(Math.sin(dLon / 2.0), 2);
        double bounded = Math.max(0.0, Math.min(1.0, a));
        return 6371000.0 * 2.0 * Math.atan2(Math.sqrt(bounded), Math.sqrt(1.0 - bounded));
    }

    public static String describeCoordinate(double lat, double lon) {
        if (!validCoordinate(lat, lon)) return "INVALID_COORDINATE";
        String s = String.format(Locale.US, "%.7f, %.7f", lon, lat);
        return suspiciousZeroPair(lat, lon) ? s + " [ZERO_PAIR_SUSPECT]" : s;
    }

    public static String displayAge(long timestampMillis, long nowMillis) {
        if (timestampMillis <= 0) return "UNKNOWN";
        long delta = nowMillis - timestampMillis;
        if (delta < -60000L) return "FUTURE_TIMESTAMP";
        return String.format(Locale.US, "%.1fs", Math.max(0L, delta) / 1000.0);
    }
}
