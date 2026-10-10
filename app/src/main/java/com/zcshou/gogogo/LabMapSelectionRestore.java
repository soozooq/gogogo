package com.zcshou.gogogo;

/**
 * Restored map selection takes precedence over the original launch viewport.
 * Corrupt saved values must never replace a previously valid selection.
 */
public final class LabMapSelectionRestore {
    private LabMapSelectionRestore() {}

    public static LabHomeCoordinates.Point choose(
            double initialLongitude, double initialLatitude,
            boolean savedSelectionExists,
            double savedLongitude, double savedLatitude) {
        LabHomeCoordinates.Point initial = LabHomeCoordinates.parse(
                Double.toString(initialLongitude), Double.toString(initialLatitude));
        if (!savedSelectionExists) return initial;
        try {
            return LabHomeCoordinates.parse(Double.toString(savedLongitude),
                    Double.toString(savedLatitude));
        } catch (IllegalArgumentException ignored) {
            return initial;
        }
    }
}
