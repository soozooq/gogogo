package com.zcshou.gogogo;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Pure-Java bounded support log rules; never include arbitrary SDK messages/POI names. */
public final class LabSupportLogText {
    private LabSupportLogText() {}

    public static String appendBounded(String previous, String event, int maxEntries) {
        int limit = Math.max(1, Math.min(24, maxEntries));
        List<String> entries = new ArrayList<>();
        if (previous != null && !previous.isEmpty()) {
            for (String entry : previous.split("\\n")) {
                if (!entry.isEmpty()) entries.add(entry);
            }
        }
        if (event != null && !event.isEmpty()) {
            entries.add(event.replace('\n', ' ').replace('\r', ' '));
        }
        int from = Math.max(0, entries.size() - limit);
        StringBuilder out = new StringBuilder();
        for (int i = from; i < entries.size(); i++) {
            out.append(entries.get(i)).append('\n');
        }
        return out.toString();
    }

    /** Only deterministic tokens; never persist a Tencent SDK reason, place name, key or coordinates. */
    public static String summarizeTencent(String raw) {
        if (raw == null || raw.isEmpty()) return null;
        if (raw.startsWith("FIX ")) return "FIX_RECEIVED coordinates=REDACTED";
        if (raw.startsWith("POI_COUNT ")) return "POI_COUNT " + safeToken(raw.substring(10));
        if (raw.startsWith("POI[") || raw.startsWith("POI_LIST")) return null;
        if (raw.startsWith("CALLBACK ")) {
            int reasonAt = raw.indexOf(" reason=");
            String prefix = reasonAt >= 0 ? raw.substring(0, reasonAt) : raw;
            return safeToken(prefix);
        }
        if (raw.startsWith("REQUEST ") || raw.startsWith("SDK_REQUEST_RETURN ")) {
            return safeToken(raw);
        }
        if (raw.startsWith("TIMEOUT")) return "TIMEOUT no_callback";
        if (raw.startsWith("LOCATION_ERROR")) return "LOCATION_ERROR";
        if (raw.startsWith("ERROR ")) {
            String[] words = raw.split("\\s+");
            return "ERROR " + (words.length > 1 ? safeToken(words[1]) : "UNKNOWN");
        }
        if (raw.startsWith("SDK_STATUS ")) {
            // name/description are SDK-controlled free text: never persist them.
            java.util.regex.Matcher code = java.util.regex.Pattern
                    .compile("=([-]?[0-9]{1,8})").matcher(raw);
            return "SDK_STATUS code=" + (code.find() ? code.group(1) : "UNKNOWN");
        }
        return null;
    }

    /** Keep names and error classes readable but block newlines, user text and huge fields. */
    public static String safeToken(String raw) {
        if (raw == null) return "UNKNOWN";
        String safe = raw.replaceAll("[^a-zA-Z0-9_ .:/=+\\-]", "_");
        return safe.substring(0, Math.min(safe.length(), 150));
    }

    /** Privacy-preserving distance category to a previously requested synthetic target. */
    public static String distanceBucket(double targetLng, double targetLat,
                                        double fixLng, double fixLat) {
        if (!Double.isFinite(targetLng) || !Double.isFinite(targetLat)
                || !Double.isFinite(fixLng) || !Double.isFinite(fixLat)
                || Math.abs(targetLng) > 180 || Math.abs(fixLng) > 180
                || Math.abs(targetLat) > 90 || Math.abs(fixLat) > 90) {
            return "UNKNOWN";
        }
        double lat1 = Math.toRadians(targetLat);
        double lat2 = Math.toRadians(fixLat);
        double dLat = lat2 - lat1;
        double dLon = Math.toRadians(fixLng - targetLng);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(lat1) * Math.cos(lat2)
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double distanceKm = 6371.0 * 2.0 * Math.asin(Math.min(1.0, Math.sqrt(a)));
        if (distanceKm < 1.0) return "LT_1KM";
        if (distanceKm < 20.0) return "LT_20KM";
        if (distanceKm < 100.0) return "LT_100KM";
        if (distanceKm < 1000.0) return "LT_1000KM";
        return "GE_1000KM";
    }

    public static String finiteTarget(String value, boolean longitude) {
        if (value == null) return "INVALID";
        try {
            double coordinate = Double.parseDouble(value.trim());
            double max = longitude ? 180.0 : 90.0;
            if (!Double.isFinite(coordinate) || coordinate < -max || coordinate > max) {
                return "INVALID";
            }
            return String.format(Locale.US, "%.6f", coordinate);
        } catch (NumberFormatException e) {
            return "INVALID";
        }
    }
}
