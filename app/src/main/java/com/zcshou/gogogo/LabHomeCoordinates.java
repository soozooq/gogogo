package com.zcshou.gogogo;

/**
 * Strict WGS-84 input boundary shared by home service start and map handoff.
 * A parseable Java double can still be NaN or Infinity, neither is a
 * usable coordinate. No external location or provider state is read here.
 */
public final class LabHomeCoordinates {
    private LabHomeCoordinates() {}

    public static final class Point {
        public final double longitude;
        public final double latitude;

        private Point(double longitude, double latitude) {
            this.longitude = longitude;
            this.latitude = latitude;
        }
    }

    public static Point parse(String longitudeText, String latitudeText) {
        final double longitude;
        final double latitude;
        try {
            longitude = Double.parseDouble(longitudeText.trim());
            latitude = Double.parseDouble(latitudeText.trim());
        } catch (RuntimeException invalidFormat) {
            throw new IllegalArgumentException("坐标格式不正确");
        }
        if (!Double.isFinite(longitude) || !Double.isFinite(latitude)) {
            throw new IllegalArgumentException("坐标不是有效数字");
        }
        if (longitude < -180.0 || longitude > 180.0
                || latitude < -90.0 || latitude > 90.0) {
            throw new IllegalArgumentException("坐标超出范围");
        }
        return new Point(longitude, latitude);
    }
}
