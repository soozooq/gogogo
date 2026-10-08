package com.zcshou.gogogo;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

public final class LabScenarioEngine implements SharedPreferences.OnSharedPreferenceChangeListener {
    private static final String PREFS = "gogogo_deterministic_scenario";
    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_NAME = "name";
    private static final String KEY_SEED = "seed";
    private static final String KEY_LOCATION_NOISE_M = "location_noise_m";
    private static final String KEY_BATTERY_PERCENT = "battery_percent";
    private static final String KEY_BASE_HEADING = "base_heading";
    private static final String KEY_HEADING_NOISE = "heading_noise";
    private static final String KEY_BASE_EPOCH_MS = "base_epoch_ms";

    public static final long DEFAULT_SEED = 114514L;
    public static final long DEFAULT_BASE_EPOCH_MS = 1767225600000L; // 2026-01-01 00:00:00 UTC

    private final SharedPreferences prefs;

    private volatile boolean enabled;
    private volatile String name;
    private volatile long seed;
    private volatile double locationNoiseMeters;
    private volatile int batteryPercent;
    private volatile float baseHeading;
    private volatile float headingNoise;
    private volatile long baseEpochMillis;

    public LabScenarioEngine(Context context) {
        prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        reload();
        prefs.registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        reload();
    }

    public void close() {
        prefs.unregisterOnSharedPreferenceChangeListener(this);
    }

    public void reload() {
        enabled = prefs.getBoolean(KEY_ENABLED, false);
        name = prefs.getString(KEY_NAME, "Lab11");
        seed = prefs.getLong(KEY_SEED, DEFAULT_SEED);
        locationNoiseMeters = Double.longBitsToDouble(
                prefs.getLong(KEY_LOCATION_NOISE_M,
                        Double.doubleToRawLongBits(80.0)));
        batteryPercent = prefs.getInt(KEY_BATTERY_PERCENT, 37);
        baseHeading = Float.intBitsToFloat(
                prefs.getInt(KEY_BASE_HEADING, Float.floatToRawIntBits(90.0f)));
        headingNoise = Float.intBitsToFloat(
                prefs.getInt(KEY_HEADING_NOISE, Float.floatToRawIntBits(12.0f)));
        baseEpochMillis = prefs.getLong(KEY_BASE_EPOCH_MS, DEFAULT_BASE_EPOCH_MS);

        locationNoiseMeters = clamp(locationNoiseMeters, 0.0, 5000.0);
        batteryPercent = clampInt(batteryPercent, 0, 100);
        baseHeading = normalizeHeading(baseHeading);
        headingNoise = (float) clamp(headingNoise, 0.0, 180.0);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getName() {
        return name;
    }

    public long getSeed() {
        return seed;
    }

    public double getLocationNoiseMeters() {
        return locationNoiseMeters;
    }

    public int getBaseBatteryPercent() {
        return batteryPercent;
    }

    public float getBaseHeading() {
        return baseHeading;
    }

    public float getHeadingNoise() {
        return headingNoise;
    }

    public long getBaseEpochMillis() {
        return baseEpochMillis;
    }

    public void setEnabled(boolean value) {
        prefs.edit().putBoolean(KEY_ENABLED, value).apply();
        enabled = value;
    }

    public void saveConfig(
            String newName,
            long newSeed,
            double newLocationNoiseMeters,
            int newBatteryPercent,
            float newBaseHeading,
            float newHeadingNoise,
            long newBaseEpochMillis,
            boolean enableAfterSave) {

        String cleanName = newName == null ? "Lab11" : newName.trim();
        if (cleanName.isEmpty()) cleanName = "Lab11";
        if (cleanName.length() > 80) cleanName = cleanName.substring(0, 80);

        double noise = clamp(newLocationNoiseMeters, 0.0, 5000.0);
        int battery = clampInt(newBatteryPercent, 0, 100);
        float heading = normalizeHeading(newBaseHeading);
        float headingJitter = (float) clamp(newHeadingNoise, 0.0, 180.0);
        long epoch = Math.max(0L, newBaseEpochMillis);

        prefs.edit()
                .putString(KEY_NAME, cleanName)
                .putLong(KEY_SEED, newSeed)
                .putLong(KEY_LOCATION_NOISE_M, Double.doubleToRawLongBits(noise))
                .putInt(KEY_BATTERY_PERCENT, battery)
                .putInt(KEY_BASE_HEADING, Float.floatToRawIntBits(heading))
                .putInt(KEY_HEADING_NOISE, Float.floatToRawIntBits(headingJitter))
                .putLong(KEY_BASE_EPOCH_MS, epoch)
                .putBoolean(KEY_ENABLED, enableAfterSave)
                .apply();

        name = cleanName;
        seed = newSeed;
        locationNoiseMeters = noise;
        batteryPercent = battery;
        baseHeading = heading;
        headingNoise = headingJitter;
        baseEpochMillis = epoch;
        enabled = enableAfterSave;
    }

    public ScenarioLocation applyLocation(
            boolean publish,
            double latitude,
            double longitude,
            double altitude,
            double speedMps,
            float bearingDegrees,
            float accuracyMeters,
            boolean includeMotion,
            long step) {

        if (!enabled || !publish || locationNoiseMeters <= 0.0) {
            return new ScenarioLocation(
                    publish,
                    latitude,
                    normalizeLongitude(longitude),
                    altitude,
                    speedMps,
                    bearingDegrees,
                    accuracyMeters,
                    includeMotion,
                    0.0,
                    0.0);
        }

        long bucket = Math.max(0L, step / 30L); // roughly one deterministic location-noise state per second
        double uRadius = unit(seed, 0x4C4F434154494F4EL, bucket);
        double uAngle = unit(seed, 0x414E474C455F4C4FL, bucket);
        double radius = Math.sqrt(uRadius) * locationNoiseMeters;
        double angle = uAngle * Math.PI * 2.0;

        double north = Math.cos(angle) * radius;
        double east = Math.sin(angle) * radius;

        double outLat = latitude + north / 111320.0;
        double cos = Math.max(0.05, Math.cos(Math.toRadians(latitude)));
        double outLng = longitude + east / (111320.0 * cos);

        outLat = clamp(outLat, -90.0, 90.0);
        outLng = normalizeLongitude(outLng);

        return new ScenarioLocation(
                true,
                outLat,
                outLng,
                altitude,
                speedMps,
                bearingDegrees,
                Math.max(accuracyMeters, (float) Math.max(1.0, locationNoiseMeters)),
                includeMotion,
                north,
                east);
    }

    public long virtualTimeMillis(long step, long tickIntervalMs) {
        long safeStep = Math.max(0L, step);
        long tick = Math.max(1L, tickIntervalMs);
        try {
            return Math.addExact(baseEpochMillis, Math.multiplyExact(safeStep, tick));
        } catch (ArithmeticException ignored) {
            return Long.MAX_VALUE;
        }
    }

    public int batteryPercent(long step) {
        if (!enabled) return batteryPercent;
        long bucket = Math.max(0L, step / 300L);
        double u = unit(seed, 0x424154544552595FL, bucket);
        int wobble = (int) Math.round((u - 0.5) * 4.0);
        return clampInt(batteryPercent + wobble, 0, 100);
    }

    public float headingDegrees(long step) {
        if (!enabled) return baseHeading;
        long bucket = Math.max(0L, step / 3L);
        double u = unit(seed, 0x48454144494E475FL, bucket);
        double signed = u * 2.0 - 1.0;
        return normalizeHeading((float) (baseHeading + signed * headingNoise));
    }

    public String summary() {
        return (enabled ? "ON" : "OFF")
                + " · " + name
                + " · seed=" + seed
                + " · noise=" + String.format(Locale.US, "%.1fm", locationNoiseMeters)
                + " · battery=" + batteryPercent + "%"
                + " · heading=" + String.format(Locale.US, "%.0f±%.0f°", baseHeading, headingNoise)
                + " · id=" + scenarioId();
    }

    public String scenarioId() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(canonicalConfig().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (int i = 0; i < 8 && i < hash.length; i++) {
                hex.append(String.format(Locale.US, "%02x", hash[i] & 0xff));
            }
            return hex.toString();
        } catch (Throwable t) {
            return Long.toHexString(seed);
        }
    }

    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        try {
            json.put("schema", 1);
            json.put("enabled", enabled);
            json.put("name", name);
            json.put("seed", seed);
            json.put("location_noise_m", locationNoiseMeters);
            json.put("battery_percent", batteryPercent);
            json.put("base_heading_deg", baseHeading);
            json.put("heading_noise_deg", headingNoise);
            json.put("base_epoch_ms", baseEpochMillis);
            json.put("scenario_id", scenarioId());
        } catch (Throwable ignored) {
        }
        return json;
    }

    public void applyJson(JSONObject json, boolean enableAfterImport) {
        if (json == null) throw new IllegalArgumentException("scenario json is null");
        saveConfig(
                json.optString("name", "Imported"),
                json.optLong("seed", DEFAULT_SEED),
                json.optDouble("location_noise_m", 80.0),
                json.optInt("battery_percent", 37),
                (float) json.optDouble("base_heading_deg", 90.0),
                (float) json.optDouble("heading_noise_deg", 12.0),
                json.optLong("base_epoch_ms", DEFAULT_BASE_EPOCH_MS),
                enableAfterImport);
    }

    private String canonicalConfig() {
        return "name=" + name + "\n"
                + "seed=" + seed + "\n"
                + "location_noise_m=" + Double.toString(locationNoiseMeters) + "\n"
                + "battery_percent=" + batteryPercent + "\n"
                + "base_heading=" + Float.toString(baseHeading) + "\n"
                + "heading_noise=" + Float.toString(headingNoise) + "\n"
                + "base_epoch_ms=" + baseEpochMillis;
    }

    private static double unit(long seed, long channel, long step) {
        long x = seed;
        x ^= channel * 0x9E3779B97F4A7C15L;
        x ^= step * 0xBF58476D1CE4E5B9L;
        x = mix64(x);
        return ((x >>> 11) * 0x1.0p-53);
    }

    private static long mix64(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    private static float normalizeHeading(float value) {
        float out = value % 360f;
        if (out < 0f) out += 360f;
        return out;
    }

    private static double normalizeLongitude(double value) {
        double out = value;
        while (out > 180.0) out -= 360.0;
        while (out < -180.0) out += 360.0;
        return out;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    public static final class ScenarioLocation {
        public final boolean publish;
        public final double latitude;
        public final double longitude;
        public final double altitude;
        public final double speedMps;
        public final float bearingDegrees;
        public final float accuracyMeters;
        public final boolean includeMotion;
        public final double noiseNorthMeters;
        public final double noiseEastMeters;

        ScenarioLocation(
                boolean publish,
                double latitude,
                double longitude,
                double altitude,
                double speedMps,
                float bearingDegrees,
                float accuracyMeters,
                boolean includeMotion,
                double noiseNorthMeters,
                double noiseEastMeters) {
            this.publish = publish;
            this.latitude = latitude;
            this.longitude = longitude;
            this.altitude = altitude;
            this.speedMps = speedMps;
            this.bearingDegrees = bearingDegrees;
            this.accuracyMeters = accuracyMeters;
            this.includeMotion = includeMotion;
            this.noiseNorthMeters = noiseNorthMeters;
            this.noiseEastMeters = noiseEastMeters;
        }
    }
}
