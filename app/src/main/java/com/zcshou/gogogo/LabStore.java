package com.zcshou.gogogo;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class LabStore {
    private static final String PREFS = "gogogo_lab_store";
    private static final String KEY_FAVORITES = "favorites";
    private static final String KEY_HISTORY = "history";
    private static final int MAX_FAVORITES = 100;
    private static final int MAX_HISTORY = 50;

    private LabStore() {}

    public static final class SavedPoint {
        public final String name;
        public final double longitude;
        public final double latitude;
        public final long timestamp;

        public SavedPoint(String name, double longitude, double latitude, long timestamp) {
            this.name = name;
            this.longitude = longitude;
            this.latitude = latitude;
            this.timestamp = timestamp;
        }

        public String displayText() {
            return (name == null || name.trim().isEmpty() ? "未命名位置" : name)
                    + "\n" + String.format(java.util.Locale.US, "%.6f, %.6f", longitude, latitude);
        }
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static void addFavorite(Context context, String name, double longitude, double latitude) {
        List<SavedPoint> items = new ArrayList<>(getFavorites(context));
        // Same coordinate -> move to top and refresh name/time.
        for (int i = items.size() - 1; i >= 0; i--) {
            SavedPoint p = items.get(i);
            if (Math.abs(p.longitude - longitude) < 0.000001
                    && Math.abs(p.latitude - latitude) < 0.000001) {
                items.remove(i);
            }
        }
        items.add(0, new SavedPoint(name, longitude, latitude, System.currentTimeMillis()));
        trim(items, MAX_FAVORITES);
        save(context, KEY_FAVORITES, items);
    }

    public static void addHistory(Context context, double longitude, double latitude) {
        List<SavedPoint> items = new ArrayList<>(getHistory(context));
        if (!items.isEmpty()) {
            SavedPoint first = items.get(0);
            if (Math.abs(first.longitude - longitude) < 0.000001
                    && Math.abs(first.latitude - latitude) < 0.000001) {
                return;
            }
        }
        items.add(0, new SavedPoint("最近模拟", longitude, latitude, System.currentTimeMillis()));
        trim(items, MAX_HISTORY);
        save(context, KEY_HISTORY, items);
    }

    public static List<SavedPoint> getFavorites(Context context) {
        return load(context, KEY_FAVORITES);
    }

    public static List<SavedPoint> getHistory(Context context) {
        return load(context, KEY_HISTORY);
    }

    public static void clearHistory(Context context) {
        prefs(context).edit().remove(KEY_HISTORY).apply();
    }

    private static void trim(List<SavedPoint> items, int max) {
        while (items.size() > max) {
            items.remove(items.size() - 1);
        }
    }

    private static List<SavedPoint> load(Context context, String key) {
        String raw = prefs(context).getString(key, "[]");
        if (raw == null || raw.trim().isEmpty()) return Collections.emptyList();

        List<SavedPoint> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                out.add(new SavedPoint(
                        o.optString("name", ""),
                        o.optDouble("lng", 0.0),
                        o.optDouble("lat", 0.0),
                        o.optLong("ts", 0L)
                ));
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    private static void save(Context context, String key, List<SavedPoint> items) {
        JSONArray arr = new JSONArray();
        for (SavedPoint p : items) {
            try {
                JSONObject o = new JSONObject();
                o.put("name", p.name);
                o.put("lng", p.longitude);
                o.put("lat", p.latitude);
                o.put("ts", p.timestamp);
                arr.put(o);
            } catch (Exception ignored) {
            }
        }
        prefs(context).edit().putString(key, arr.toString()).apply();
    }
}
