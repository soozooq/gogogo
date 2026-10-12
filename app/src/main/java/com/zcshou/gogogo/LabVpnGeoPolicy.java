package com.zcshou.gogogo;

/** Guardrails for approximate IP-to-coordinate syncing. No Android dependency. */
public final class LabVpnGeoPolicy {
    private LabVpnGeoPolicy() {}

    public static boolean valid(double latitude, double longitude, String country) {
        return Double.isFinite(latitude) && Double.isFinite(longitude)
                && latitude >= -90d && latitude <= 90d
                && longitude >= -180d && longitude <= 180d
                && !(latitude == 0d && longitude == 0d)
                && country != null && !country.trim().isEmpty();
    }

    public static boolean canApply(boolean enabled, boolean vpnIsDefaultForApp,
                                   boolean fixedMode, boolean resultValid) {
        return enabled && vpnIsDefaultForApp && fixedMode && resultValid;
    }

    /** Freeze only a successfully applied VPN coordinate, never raw IP lookup data
     * or a historical location superseded by a later manual target. */
    public static boolean canFreeze(boolean followEnabled, boolean alreadyLocked,
                                    long appliedAtMs, boolean validSavedTarget) {
        return followEnabled && !alreadyLocked && appliedAtMs > 0L && validSavedTarget;
    }

    /** Small enough to avoid per-IP quota exhaustion; changes are separately signaled. */
    public static final long POLL_INTERVAL_MS = 180_000L;
    public static final long MIN_MANUAL_INTERVAL_MS = 15_000L;
}
