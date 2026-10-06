package com.zcshou.gogogo;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Locale;

/**
 * GoGoGo Lab Resource Broker.
 *
 * Inspired by resource-shadowing / security-domain research (MockDroid, MOSES, TrustDroid),
 * but intentionally scoped to GoGoGo's own synthetic resources. It does not hook or bypass
 * third-party app security controls.
 */
public final class LabPolicyEngine implements SharedPreferences.OnSharedPreferenceChangeListener {
    public static final String DOMAIN_PERSONAL = "PERSONAL";
    public static final String DOMAIN_WORK = "WORK";
    public static final String DOMAIN_LAB = "LAB";

    public static final String LOCATION_EXACT = "EXACT";
    public static final String LOCATION_COARSE_250 = "COARSE_250M";
    public static final String LOCATION_COARSE_1000 = "COARSE_1000M";
    public static final String LOCATION_PAUSED = "PAUSE_PUBLISH";

    public static final String NETWORK_LIVE = "LIVE";
    public static final String NETWORK_REDACTED = "REDACTED";
    public static final String NETWORK_OFFLINE_SHADOW = "OFFLINE_SHADOW";

    public static final String SENSOR_LIVE = "LIVE";
    public static final String SENSOR_QUANTIZED = "QUANTIZED";
    public static final String SENSOR_UNAVAILABLE_SHADOW = "UNAVAILABLE_SHADOW";

    private static final String PREFS = "gogogo_resource_broker";
    private static final String KEY_DOMAIN = "domain";
    private static final String KEY_LOCATION = "location";
    private static final String KEY_NETWORK = "network";
    private static final String KEY_SENSOR = "sensor";

    private final SharedPreferences prefs;

    private volatile String domain;
    private volatile String locationMode;
    private volatile String networkMode;
    private volatile String sensorMode;

    public LabPolicyEngine(Context context) {
        prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        reload();
        prefs.registerOnSharedPreferenceChangeListener(this);
    }

    public void close() {
        prefs.unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        reload();
    }

    public void reload() {
        domain = prefs.getString(KEY_DOMAIN, DOMAIN_PERSONAL);
        locationMode = prefs.getString(KEY_LOCATION, LOCATION_EXACT);
        networkMode = prefs.getString(KEY_NETWORK, NETWORK_LIVE);
        sensorMode = prefs.getString(KEY_SENSOR, SENSOR_LIVE);
    }

    public String getDomain() {
        return domain;
    }

    public String getLocationMode() {
        return locationMode;
    }

    public String getNetworkMode() {
        return networkMode;
    }

    public String getSensorMode() {
        return sensorMode;
    }

    public void applyPreset(String preset) {
        if (DOMAIN_WORK.equals(preset)) {
            save(DOMAIN_WORK, LOCATION_COARSE_250, NETWORK_REDACTED, SENSOR_LIVE);
        } else if (DOMAIN_LAB.equals(preset)) {
            save(DOMAIN_LAB, LOCATION_COARSE_1000,
                    NETWORK_OFFLINE_SHADOW, SENSOR_UNAVAILABLE_SHADOW);
        } else {
            save(DOMAIN_PERSONAL, LOCATION_EXACT, NETWORK_LIVE, SENSOR_LIVE);
        }
    }

    public void saveCustomLab(String newLocationMode, String newNetworkMode, String newSensorMode) {
        save(DOMAIN_LAB,
                validLocation(newLocationMode) ? newLocationMode : LOCATION_EXACT,
                validNetwork(newNetworkMode) ? newNetworkMode : NETWORK_LIVE,
                validSensor(newSensorMode) ? newSensorMode : SENSOR_LIVE);
    }

    private void save(String newDomain, String newLocation, String newNetwork, String newSensor) {
        prefs.edit()
                .putString(KEY_DOMAIN, newDomain)
                .putString(KEY_LOCATION, newLocation)
                .putString(KEY_NETWORK, newNetwork)
                .putString(KEY_SENSOR, newSensor)
                .apply();
        domain = newDomain;
        locationMode = newLocation;
        networkMode = newNetwork;
        sensorMode = newSensor;
    }

    public String summary() {
        return domain
                + " · location=" + locationMode
                + " · network=" + networkMode
                + " · sensor=" + sensorMode;
    }

    public LocationDecision decideLocation(
            double rawLat,
            double rawLng,
            double rawAlt,
            double rawSpeed,
            float rawBearing) {

        String mode = locationMode;

        if (LOCATION_PAUSED.equals(mode)) {
            return new LocationDecision(
                    false,
                    rawLat, rawLng, rawAlt,
                    0.0, rawBearing,
                    9999.0f,
                    false,
                    mode);
        }

        if (LOCATION_COARSE_250.equals(mode)) {
            return coarse(rawLat, rawLng, rawAlt, rawBearing, 250.0, mode);
        }

        if (LOCATION_COARSE_1000.equals(mode)) {
            return coarse(rawLat, rawLng, rawAlt, rawBearing, 1000.0, mode);
        }

        return new LocationDecision(
                true,
                rawLat, normalizeLng(rawLng), rawAlt,
                Math.max(0.0, rawSpeed), rawBearing,
                0.8f,
                true,
                LOCATION_EXACT);
    }

    private static LocationDecision coarse(
            double rawLat,
            double rawLng,
            double rawAlt,
            float rawBearing,
            double meters,
            String mode) {

        double latStep = meters / 111320.0;
        double cos = Math.max(0.05, Math.cos(Math.toRadians(rawLat)));
        double lngStep = meters / (111320.0 * cos);

        double qLat = Math.round(rawLat / latStep) * latStep;
        double qLng = Math.round(rawLng / lngStep) * lngStep;
        qLat = Math.max(-90.0, Math.min(90.0, qLat));
        qLng = normalizeLng(qLng);

        return new LocationDecision(
                true,
                qLat, qLng, rawAlt,
                0.0, rawBearing,
                (float) meters,
                false,
                mode);
    }

    private static double normalizeLng(double value) {
        double lng = value;
        while (lng > 180.0) lng -= 360.0;
        while (lng < -180.0) lng += 360.0;
        return lng;
    }

    private static boolean validLocation(String value) {
        return LOCATION_EXACT.equals(value)
                || LOCATION_COARSE_250.equals(value)
                || LOCATION_COARSE_1000.equals(value)
                || LOCATION_PAUSED.equals(value);
    }

    private static boolean validNetwork(String value) {
        return NETWORK_LIVE.equals(value)
                || NETWORK_REDACTED.equals(value)
                || NETWORK_OFFLINE_SHADOW.equals(value);
    }

    private static boolean validSensor(String value) {
        return SENSOR_LIVE.equals(value)
                || SENSOR_QUANTIZED.equals(value)
                || SENSOR_UNAVAILABLE_SHADOW.equals(value);
    }

    public String describeNetworkShadow(String rawDescription) {
        if (NETWORK_OFFLINE_SHADOW.equals(networkMode)) {
            return "Broker shadow view: OFFLINE\n"
                    + "（仅 GoGoGo Lab 的实验视图，不会关闭系统真实网络）";
        }
        if (NETWORK_REDACTED.equals(networkMode)) {
            return "Broker shadow view: REDACTED\n"
                    + "网络存在，但详细 IP / DNS / 运营商被策略层隐藏。";
        }
        return rawDescription;
    }

    public String describeSensorShadow(
            boolean hasOrientation,
            float heading,
            float pitch,
            float roll) {

        if (SENSOR_UNAVAILABLE_SHADOW.equals(sensorMode)) {
            return "Broker shadow view: SENSOR_UNAVAILABLE\n"
                    + "（只影响 GoGoGo Lab 的实验视图，不会关闭手机传感器）";
        }

        if (SENSOR_QUANTIZED.equals(sensorMode)) {
            if (!hasOrientation) return "Broker shadow view: 暂无方向数据";
            float qHeading = Math.round(heading / 15f) * 15f;
            float qPitch = Math.round(pitch / 10f) * 10f;
            float qRoll = Math.round(roll / 10f) * 10f;
            return String.format(Locale.US,
                    "Broker shadow view: QUANTIZED\nHeading %.0f° · Pitch %.0f° · Roll %.0f°",
                    qHeading, qPitch, qRoll);
        }

        if (!hasOrientation) return "Broker shadow view: 暂无方向数据";
        return String.format(Locale.US,
                "Broker shadow view: LIVE\nHeading %.1f° · Pitch %.1f° · Roll %.1f°",
                heading, pitch, roll);
    }

    public static final class LocationDecision {
        public final boolean publish;
        public final double latitude;
        public final double longitude;
        public final double altitude;
        public final double speedMps;
        public final float bearingDegrees;
        public final float accuracyMeters;
        public final boolean includeMotion;
        public final String mode;

        LocationDecision(
                boolean publish,
                double latitude,
                double longitude,
                double altitude,
                double speedMps,
                float bearingDegrees,
                float accuracyMeters,
                boolean includeMotion,
                String mode) {
            this.publish = publish;
            this.latitude = latitude;
            this.longitude = longitude;
            this.altitude = altitude;
            this.speedMps = speedMps;
            this.bearingDegrees = bearingDegrees;
            this.accuracyMeters = accuracyMeters;
            this.includeMotion = includeMotion;
            this.mode = mode;
        }
    }
}
