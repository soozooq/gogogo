package com.zcshou.gogogo;

import java.util.Locale;

/** Offline country/city text filtering, without remote geocoding or API keys. */
public final class LabPlaceSearch {
    private LabPlaceSearch() {}

    public static boolean matches(String locationLabel, String search) {
        if (locationLabel == null) return false;
        String query = normalize(search);
        if (query.isEmpty()) return true;
        String candidate = normalize(locationLabel);
        // Each whitespace-delimited term must occur. Allows e.g. "中国 上海".
        for (String term : query.split(" ")) {
            if (!term.isEmpty() && !candidate.contains(term)) return false;
        }
        return true;
    }

    private static String normalize(String value) {
        if (value == null) return "";
        return value.toLowerCase(Locale.ROOT)
                .replace('·', ' ')
                .replace('（', ' ').replace('）', ' ')
                .replace('(', ' ').replace(')', ' ')
                .replaceAll("\\s+", " ").trim();
    }
}
