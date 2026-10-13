package com.zcshou.gogogo;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Explicit-submit, rate-limited Nominatim-compatible geocoder.
 *
 * IMPORTANT: OSMF's public Nominatim is a small shared service, not an
 * unlimited commercial API. No autocomplete, bulk querying, prefetching or
 * automatic lookups are allowed. User manually presses the search button.
 *
 * https://operations.osmfoundation.org/policies/nominatim/
 * Results must carry © OpenStreetMap contributors attribution (shown by UI).
 */
public final class TianwaitianGeocoder {
    public static final String DEFAULT_ENDPOINT = "https://nominatim.openstreetmap.org/search";
    public static final String ATTRIBUTION = "© OpenStreetMap contributors · ODbL";
    private static final String PREFS = "twt_geocoder_search";
    private static final String KEY_ENDPOINT = "search_endpoint";
    private static final String KEY_CACHE = "search_cache";
    private static final long TTL_MS = 7L * 24 * 60 * 60 * 1000;
    private static final int MAX_CACHE_ENTRIES = 24;
    private static final int MAX_BODY_CHARS = 160_000;
    // Across all search screens within this app process.
    private static final Object RATE_LOCK = new Object();
    private static long nextRequestAtElapsed;
    public static final ExecutorService WORKER = Executors.newSingleThreadExecutor();

    private TianwaitianGeocoder() {}

    public static final class Result {
        public final String label;
        public final double longitude;
        public final double latitude;
        public Result(String label, double longitude, double latitude) {
            this.label = label;
            this.longitude = longitude;
            this.latitude = latitude;
        }
    }

    public static String endpoint(Context context) {
        String value = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_ENDPOINT, DEFAULT_ENDPOINT);
        return validEndpoint(value) ? value : DEFAULT_ENDPOINT;
    }

    /**
     * A user can switch a Nominatim-compatible HTTPS provider in the app
     * without an APK update, should OSMF withdraw access.
     */
    public static boolean validEndpoint(String value) {
        if (value == null || value.length() > 240) return false;
        try {
            URI uri = new URI(value.trim());
            return "https".equalsIgnoreCase(uri.getScheme())
                    && uri.getHost() != null && !uri.getHost().isEmpty()
                    && uri.getUserInfo() == null && uri.getQuery() == null
                    && uri.getFragment() == null
                    && uri.getPath() != null
                    && uri.getPath().endsWith("/search");
        } catch (Exception invalid) {
            return false;
        }
    }

    public static void setEndpoint(Context context, String value) {
        if (!validEndpoint(value)) throw new IllegalArgumentException(
                "请输入 HTTPS 且以 /search 结尾的 Nominatim 兼容接口地址");
        context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY_ENDPOINT, value.trim()).remove(KEY_CACHE).apply();
    }

    public static void clearCache(Context context) {
        context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().remove(KEY_CACHE).apply();
    }

    private static String cacheKey(String endpoint, String query) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(
                    (endpoint + "\n" + query.trim().toLowerCase(Locale.ROOT))
                            .getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : bytes) hex.append(String.format(Locale.ROOT, "%02x", b & 255));
            return hex.toString();
        } catch (Exception unavailable) {
            throw new IllegalStateException("SHA-256 unavailable", unavailable);
        }
    }

    /** Local on-device 7-day cache to avoid repeating public API requests. */
    public static List<Result> cached(Context context, String query) {
        String key = cacheKey(endpoint(context), query);
        String blob = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_CACHE, "{}");
        try {
            JSONObject record = new JSONObject(blob).optJSONObject(key);
            if (record == null || System.currentTimeMillis() -
                    record.optLong("at", 0) > TTL_MS) return null;
            return parseResults(record.optJSONArray("items"));
        } catch (JSONException corrupt) {
            return null;
        }
    }

    /** Only call on an explicit user button action; never from a TextWatcher. */
    public static List<Result> search(Context context, String query) throws Exception {
        if (!TianwaitianSearchQuery.validOnlineQuery(query)) {
            throw new IllegalArgumentException("请填写 2–120 字的地点名称或地址");
        }
        List<Result> hit = cached(context, query);
        if (hit != null) return hit;
        String endpoint = endpoint(context);
        // Minimum 1.2s between outgoing requests from THIS app instance.
        synchronized (RATE_LOCK) {
            long delay = nextRequestAtElapsed - SystemClock.elapsedRealtime();
            if (delay > 0) Thread.sleep(delay);
            nextRequestAtElapsed = SystemClock.elapsedRealtime() + 1200;
        }
        String address = endpoint + "?format=jsonv2&limit=5&addressdetails=0"
                + "&accept-language=zh-CN%2Cen"
                + "&q=" + URLEncoder.encode(query.trim(), "UTF-8");
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new java.net.URL(address).openConnection();
            conn.setConnectTimeout(8500);
            conn.setReadTimeout(8500);
            conn.setInstanceFollowRedirects(false);
            conn.setRequestProperty("User-Agent",
                    "Tianwaitian-Android/2.4.9 (https://github.com/soozooq/gogogo)");
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8");
            int status = conn.getResponseCode();
            if (status == 429) throw new IllegalStateException(
                    "公共搜索服务繁忙，请稍后再试，勿连续请求");
            if (status == 403) throw new IllegalStateException(
                    "搜索服务拒绝请求，可以在搜索设置中更换服务地址");
            if (status != 200) throw new IllegalStateException(
                    "在线搜索暂时不可用（HTTP " + status + "）");
            StringBuilder body = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    conn.getInputStream(), StandardCharsets.UTF_8))) {
                char[] buffer = new char[2048];
                int n;
                while ((n = reader.read(buffer)) != -1) {
                    if (body.length() + n > MAX_BODY_CHARS) {
                        throw new IllegalStateException("搜索结果体积超出限制");
                    }
                    body.append(buffer, 0, n);
                }
            }
            List<Result> result = parseResults(new JSONArray(body.toString()));
            writeCache(context, cacheKey(endpoint, query), result);
            return result;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static List<Result> parseResults(JSONArray items) {
        if (items == null) return Collections.emptyList();
        List<Result> output = new ArrayList<>();
        for (int i = 0; i < Math.min(5, items.length()); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item == null) continue;
            String label = item.optString("display_name", "").trim();
            if (label.isEmpty()) continue;
            try {
                LabHomeCoordinates.Point p = LabHomeCoordinates.parse(
                        item.optString("lon", ""), item.optString("lat", ""));
                output.add(new Result(label.length() > 220
                        ? label.substring(0, 220) : label,
                        p.longitude, p.latitude));
            } catch (IllegalArgumentException invalid) {
                // Unusable third-party result must not break the entire search.
            }
        }
        return output;
    }

    private static void writeCache(Context context, String key, List<Result> found) {
        SharedPreferences prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        try {
            JSONObject root = new JSONObject(prefs.getString(KEY_CACHE, "{}"));
            long now = System.currentTimeMillis();
            JSONArray items = new JSONArray();
            for (Result r : found) {
                JSONObject item = new JSONObject();
                item.put("display_name", r.label);
                item.put("lon", Double.toString(r.longitude));
                item.put("lat", Double.toString(r.latitude));
                items.put(item);
            }
            JSONObject entry = new JSONObject();
            entry.put("at", now);
            entry.put("items", items);
            root.put(key, entry);
            List<String> stale = new ArrayList<>();
            Iterator<String> keys = root.keys();
            while (keys.hasNext()) {
                String candidate = keys.next();
                JSONObject cached = root.optJSONObject(candidate);
                if (cached == null || now - cached.optLong("at", 0) > TTL_MS) {
                    stale.add(candidate);
                }
            }
            for (String candidate : stale) root.remove(candidate);
            while (root.length() > MAX_CACHE_ENTRIES) {
                String oldestKey = null;
                long oldest = Long.MAX_VALUE;
                keys = root.keys();
                while (keys.hasNext()) {
                    String candidate = keys.next();
                    long when = root.optJSONObject(candidate).optLong("at", 0);
                    if (when < oldest) { oldest = when; oldestKey = candidate; }
                }
                if (oldestKey == null) break;
                root.remove(oldestKey);
            }
            prefs.edit().putString(KEY_CACHE, root.toString()).apply();
        } catch (JSONException corrupt) {
            // Caching failure must never discard a valid user-visible response.
        }
    }
}
