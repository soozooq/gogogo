package com.zcshou.gogogo;

/** Shared coordinate identity for picker favorites and LabStore (WGS-84 degrees). */
public final class LabPlaceCoordinateMatch {
    private static final double EPSILON_DEGREES = 0.000001;

    private LabPlaceCoordinateMatch() {}

    public static boolean same(double firstLongitude, double firstLatitude,
                               double secondLongitude, double secondLatitude) {
        return Double.isFinite(firstLongitude) && Double.isFinite(firstLatitude)
                && Double.isFinite(secondLongitude) && Double.isFinite(secondLatitude)
                && Math.abs(firstLongitude - secondLongitude) < EPSILON_DEGREES
                && Math.abs(firstLatitude - secondLatitude) < EPSILON_DEGREES;
    }
}
