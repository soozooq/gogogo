package com.zcshou.gogogo;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Opt-in ServiceGo-owned sync of that app's own VPN egress IP geolocation.
 * Only a detected default VPN network is queried, via HTTPS, and never a
 * physical network fallback. Does not start a location mock service itself.
 */
public final class LabVpnExitSync {
    public static final String PREFS = "gogogo_vpn_exit_location_sync";
    public static final String KEY_ENABLED = "enabled";
    public static final String KEY_STATUS = "status";
    public static final String KEY_COUNTRY = "country";
    public static final String KEY_CITY = "city";
    public static final String KEY_LAT = "lat_bits";
    public static final String KEY_LNG = "lng_bits";
    public static final String KEY_APPLIED_AT = "applied_at";
    public static final String KEY_LOCKED = "location_locked";
    public static final String KEY_LOCKED_AT = "location_locked_at";
    public static final String KEY_LOCK_LAT = "locked_lat_bits";
    public static final String KEY_LOCK_LNG = "locked_lng_bits";
    public static final String KEY_LOCK_COUNTRY = "locked_country";
    public static final String KEY_LOCK_CITY = "locked_city";
    private static final String URL_ENDPOINT = "https://ipwho.is/?fields=success,country,city,latitude,longitude";
    private final Context context;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ConnectivityManager connectivity;
    private final ApplyLocation applyLocation;
    private final Runnable poll = new Runnable() {
        @Override public void run() {
            if (!running) return;
            scheduleFetch(0);
            handler.postDelayed(this, LabVpnGeoPolicy.POLL_INTERVAL_MS);
        }
    };
    private final Runnable fetch = this::fetchLatest;
    private ExecutorService executor;
    private ConnectivityManager.NetworkCallback callback;
    private Network vpnNetwork;
    private boolean running;
    private long lastManualElapsed;
    private int generation;

    public interface ApplyLocation {
        /** Called on main thread; true only if still using an eligible fixed-position simulation. */
        boolean accept(double latitude, double longitude);
    }

    public LabVpnExitSync(Context context, ApplyLocation applyLocation) {
        this.context = context.getApplicationContext();
        this.applyLocation = applyLocation;
        this.connectivity = (ConnectivityManager) this.context.getSystemService(
                Context.CONNECTIVITY_SERVICE);
    }

    public static boolean enabled(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_ENABLED, false);
    }

    public static void configure(Context context, boolean enabled) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (prefs.getBoolean(KEY_LOCKED, false)) return; // Explicit unlock required.
        prefs.edit().putBoolean(KEY_ENABLED, enabled)
                .putString(KEY_STATUS, enabled ? "等待模拟服务与 VPN 网络" : "已关闭 VPN 联动")
                .apply();
    }

    public static boolean locked(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_LOCKED, false);
    }

    public static final class LockedTarget {
        public final double latitude;
        public final double longitude;
        public final String country;
        public final String city;
        public final long lockedAt;
        private LockedTarget(double latitude, double longitude,
                             String country, String city, long lockedAt) {
            this.latitude = latitude;
            this.longitude = longitude;
            this.country = country;
            this.city = city;
            this.lockedAt = lockedAt;
        }
    }

    public static LockedTarget getLockedTarget(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (!prefs.getBoolean(KEY_LOCKED, false)) return null;
        double latitude = Double.longBitsToDouble(prefs.getLong(KEY_LOCK_LAT,
                Double.doubleToRawLongBits(Double.NaN)));
        double longitude = Double.longBitsToDouble(prefs.getLong(KEY_LOCK_LNG,
                Double.doubleToRawLongBits(Double.NaN)));
        String country = prefs.getString(KEY_LOCK_COUNTRY, "");
        if (!LabVpnGeoPolicy.valid(latitude, longitude, country)) return null;
        return new LockedTarget(latitude, longitude, country,
                prefs.getString(KEY_LOCK_CITY, ""), prefs.getLong(KEY_LOCKED_AT, 0L));
    }

    /**
     * Persist the exact last coordinate that ServiceGo successfully applied.
     * Turns OFF future VPN-follow updates, including callbacks already in flight.
     */
    public static boolean freezeLastApplied(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        double latitude = Double.longBitsToDouble(prefs.getLong(KEY_LAT,
                Double.doubleToRawLongBits(Double.NaN)));
        double longitude = Double.longBitsToDouble(prefs.getLong(KEY_LNG,
                Double.doubleToRawLongBits(Double.NaN)));
        String country = prefs.getString(KEY_COUNTRY, "");
        long applied = prefs.getLong(KEY_APPLIED_AT, 0L);
        if (!LabVpnGeoPolicy.canFreeze(prefs.getBoolean(KEY_ENABLED, false),
                prefs.getBoolean(KEY_LOCKED, false), applied,
                LabVpnGeoPolicy.valid(latitude, longitude, country))) return false;
        // commit() makes the lock durable before the service receives CONFIG.
        return prefs.edit()
                .putLong(KEY_LOCK_LAT, Double.doubleToRawLongBits(latitude))
                .putLong(KEY_LOCK_LNG, Double.doubleToRawLongBits(longitude))
                .putString(KEY_LOCK_COUNTRY, country)
                .putString(KEY_LOCK_CITY, prefs.getString(KEY_CITY, ""))
                .putLong(KEY_LOCKED_AT, System.currentTimeMillis())
                .putBoolean(KEY_LOCKED, true).putBoolean(KEY_ENABLED, false)
                .putString(KEY_STATUS, "VPN 坐标已锁定；节点切换或重连不再更新")
                .commit();
    }

    /** Unlocking does not change the current mock target or silently reenable sync. */
    public static void unfreeze(Context context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putBoolean(KEY_LOCKED, false).putBoolean(KEY_ENABLED, false)
                .putString(KEY_STATUS, "已解锁；原位置保持不变，可手动开启 VPN 跟随")
                .apply();
    }

    /** A new manual fixed target supersedes the displayed last-applied VPN target
     * until a fresh VPN lookup has actually re-applied new coordinates. */
    public static void markManualTargetPending(Context context) {
        if (!enabled(context)) return;
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putLong(KEY_APPLIED_AT, 0L)
                .putString(KEY_STATUS, "已设置手动坐标，等待下次 VPN 出口校准")
                .apply();
    }

    public static String statusText(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String state = prefs.getString(KEY_STATUS,
                "未开启；不会向第三方 IP 地理位置服务发起请求");
        LockedTarget fixed = getLockedTarget(context);
        if (fixed != null) {
            return "🔒 已锁定 VPN 出口位置：" + fixed.country
                    + (fixed.city.isEmpty() ? "" : " · " + fixed.city)
                    + "\n锁定坐标已保存；即使 VPN 掉线/恢复/换节点也不会改动。"
                    + "\n仅在模拟服务运行时生效；服务被系统终止无法保证继续覆盖系统定位。";
        }
        long at = prefs.getLong(KEY_APPLIED_AT, 0);
        if (at > 0 && enabled(context)) {
            state += "\n上次已应用：" + prefs.getString(KEY_COUNTRY, "?")
                    + " · " + prefs.getString(KEY_CITY, "?")
                    + "（IP 近似城市，非精确 GPS）";
        }
        return state;
    }

    /** Main thread only. */
    public void startOrStopFromConfig() {
        if (!enabled(context)) {
            stop();
            if (!locked(context)) writeStatus("已关闭 VPN 联动；之前模拟的位置保持不变");
            return;
        }
        if (running) {
            manualRefresh();
            return;
        }
        if (connectivity == null) {
            writeStatus("系统网络管理器不可用，无法联动");
            return;
        }
        running = true;
        generation++;
        executor = Executors.newSingleThreadExecutor();
        callback = new ConnectivityManager.NetworkCallback() {
            @Override public void onCapabilitiesChanged(Network network, NetworkCapabilities caps) {
                if (!running) return;
                boolean vpn = caps != null
                        && caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
                        && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
                if (!vpn) {
                    vpnNetwork = null;
                    generation++;
                    handler.removeCallbacks(fetch);
                    writeStatus("GoGoGo 当前没有走系统 VPN：已暂停联动，保留原模拟位置");
                    return;
                }
                if (!network.equals(vpnNetwork)) {
                    vpnNetwork = network;
                    generation++;
                    writeStatus("已检测到 GoGoGo 的 VPN 网络，正在核对出口城市…");
                    scheduleFetch(2500);
                }
            }
            @Override public void onLost(Network network) {
                if (network.equals(vpnNetwork)) {
                    vpnNetwork = null;
                    generation++;
                    handler.removeCallbacks(fetch);
                    writeStatus("VPN 网络已断开；不改回真实位置，也不自动更改模拟位置");
                }
            }
        };
        try {
            connectivity.registerDefaultNetworkCallback(callback, handler);
            writeStatus("联动已开启，正在等待 VPN 网络状态");
            handler.postDelayed(poll, LabVpnGeoPolicy.POLL_INTERVAL_MS);
        } catch (RuntimeException error) {
            running = false;
            callback = null;
            executor.shutdownNow();
            executor = null;
            writeStatus("无法订阅网络状态：" + error.getClass().getSimpleName());
        }
    }

    /** Main thread only. Stale in-flight callbacks are ignored after stop. */
    public void stop() {
        running = false;
        vpnNetwork = null;
        generation++;
        handler.removeCallbacks(fetch);
        handler.removeCallbacks(poll);
        if (callback != null && connectivity != null) {
            try {
                connectivity.unregisterNetworkCallback(callback);
            } catch (RuntimeException ignored) {
            }
        }
        callback = null;
        if (executor != null) executor.shutdownNow();
        executor = null;
    }

    public void manualRefresh() {
        if (!running) return;
        long now = SystemClock.elapsedRealtime();
        if (now - lastManualElapsed < LabVpnGeoPolicy.MIN_MANUAL_INTERVAL_MS) {
            writeStatus("手动刷新过于频繁，短暂间隔后再试");
            return;
        }
        lastManualElapsed = now;
        scheduleFetch(0);
    }

    /** Explicit manual position set by owner: invalidate in-flight IP lookup,
     * then request the current VPN city instead of accepting stale results. */
    public void onManualPositionSelected() {
        if (!running) return;
        generation++;
        scheduleFetch(2500);
    }

    private void scheduleFetch(long delayMs) {
        handler.removeCallbacks(fetch);
        if (running && vpnNetwork != null) handler.postDelayed(fetch, delayMs);
    }

    private void fetchLatest() {
        final Network network = vpnNetwork;
        if (!running || network == null || executor == null) return;
        final int token = ++generation;
        try {
            executor.execute(() -> {
                GeoPlace result = null;
                String failure = null;
                HttpURLConnection connection = null;
                try {
                    // Binds the HTTPS request to the VPN network that covers THIS APP.
                    // Do not fall back to global or cellular connectivity.
                    connection = (HttpURLConnection) network.openConnection(new URL(URL_ENDPOINT));
                    connection.setConnectTimeout(7000);
                    connection.setReadTimeout(7000);
                    connection.setInstanceFollowRedirects(false);
                    connection.setRequestProperty("Accept", "application/json");
                    if (connection.getResponseCode() != 200) {
                        failure = "地理定位接口 HTTP " + connection.getResponseCode();
                    } else {
                        try (InputStream input = connection.getInputStream()) {
                            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                            byte[] buffer = new byte[2048];
                            int n;
                            while ((n = input.read(buffer)) != -1) {
                                if (bytes.size() + n > 8192) throw new IllegalArgumentException("oversize");
                                bytes.write(buffer, 0, n);
                            }
                            JSONObject json = new JSONObject(
                                    new String(bytes.toByteArray(), StandardCharsets.UTF_8));
                            if (!json.optBoolean("success", false)) {
                                throw new IllegalArgumentException("api_not_successful");
                            }
                            GeoPlace found = new GeoPlace(
                                    json.optDouble("latitude", Double.NaN),
                                    json.optDouble("longitude", Double.NaN),
                                    json.optString("country", ""),
                                    json.optString("city", ""));
                            if (!LabVpnGeoPolicy.valid(found.latitude, found.longitude,
                                    found.country)) {
                                throw new IllegalArgumentException("invalid_ip_geo");
                            }
                            result = found;
                        }
                    }
                } catch (Exception issue) {
                    failure = "出口城市查询不可用：" + issue.getClass().getSimpleName();
                } finally {
                    if (connection != null) connection.disconnect();
                }
                final GeoPlace place = result;
                final String error = failure;
                handler.post(() -> {
                    if (!running || !enabled(context) || locked(context)
                            || token != generation || !network.equals(vpnNetwork)) return;
                    if (place == null) {
                        writeStatus(error == null ? "IP 定位无结果" : error
                                + "；继续保持现有模拟位置");
                        return;
                    }
                    boolean accepted = applyLocation.accept(place.latitude, place.longitude);
                    if (!accepted) {
                        writeStatus("查询成功：" + place.summary()
                                + "；当前路线/漫游/回放或服务未就绪，未覆盖坐标");
                        return;
                    }
                    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                            .putLong(KEY_LAT, Double.doubleToRawLongBits(place.latitude))
                            .putLong(KEY_LNG, Double.doubleToRawLongBits(place.longitude))
                            .putString(KEY_COUNTRY, place.country)
                            .putString(KEY_CITY, place.city)
                            .putLong(KEY_APPLIED_AT, System.currentTimeMillis())
                            .putString(KEY_STATUS, "已跟随 VPN 出口近似坐标：" + place.summary())
                            .apply();
                });
            });
        } catch (RuntimeException unavailable) {
            writeStatus("同步线程不可用；保持现有模拟位置");
        }
    }

    private void writeStatus(String status) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY_STATUS, status).apply();
    }

    private static final class GeoPlace {
        final double latitude;
        final double longitude;
        final String country;
        final String city;
        GeoPlace(double latitude, double longitude, String country, String city) {
            this.latitude = latitude;
            this.longitude = longitude;
            this.country = country;
            this.city = city;
        }
        String summary() { return country + (city.isEmpty() ? "" : " · " + city); }
    }
}
