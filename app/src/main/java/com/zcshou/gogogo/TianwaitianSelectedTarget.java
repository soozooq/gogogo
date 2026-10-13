package com.zcshou.gogogo;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Single source of truth for the position intentionally SELECTED in the UI.
 * This never starts/stops or alters ServiceGo: the actively published mock
 * position is a separate concept.
 */
public final class TianwaitianSelectedTarget {
    private static final String PREFS = "twt_selected_target_v1";
    private static final String LON = "longitude";
    private static final String LAT = "latitude";
    private static final String LABEL = "label";
    private static final String SEQUENCE = "sequence";

    public static final class Target {
        public final double longitude;
        public final double latitude;
        public final String label;
        public final long sequence;

        private Target(double longitude, double latitude, String label, long sequence) {
            this.longitude = longitude;
            this.latitude = latitude;
            this.label = label;
            this.sequence = sequence;
        }
    }

    private TianwaitianSelectedTarget() {}

    public static Target read(Context context) {
        SharedPreferences prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (!prefs.contains(LON) || !prefs.contains(LAT)) return null;
        try {
            LabHomeCoordinates.Point point = LabHomeCoordinates.parse(
                    prefs.getString(LON, ""), prefs.getString(LAT, ""));
            return new Target(point.longitude, point.latitude,
                    safeLabel(prefs.getString(LABEL, "")), prefs.getLong(SEQUENCE, 0));
        } catch (RuntimeException invalidOrStale) {
            // A damaged preference must not crash the map launcher.
            return null;
        }
    }

    /**
     * Save only a confirmed user selection (map tap, picker result, successful
     * mock start). No passive diagnostic or lifecycle callback may call this.
     */
    public static Target save(Context context, double longitude, double latitude,
                              String label) {
        LabHomeCoordinates.Point point = LabHomeCoordinates.parse(
                Double.toString(longitude), Double.toString(latitude));
        SharedPreferences prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        long next = prefs.getLong(SEQUENCE, 0) + 1L;
        if (next <= 0) next = 1L;
        String display = safeLabel(label);
        prefs.edit()
                .putString(LON, Double.toString(point.longitude))
                .putString(LAT, Double.toString(point.latitude))
                .putString(LABEL, display)
                .putLong(SEQUENCE, next)
                .apply();
        return new Target(point.longitude, point.latitude, display, next);
    }

    private static String safeLabel(String input) {
        if (input == null || input.trim().isEmpty()) return "地图选点";
        String label = input.trim();
        return label.length() <= 100 ? label : label.substring(0, 100);
    }
}
