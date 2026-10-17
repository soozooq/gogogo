package com.soozooq.tianwaitian.fixture;

/**
 * Controlled fixture compiled into a SEPARATE Android APK, not the host dex.
 * Deliberately no Android, filesystem, network, or location APIs.
 */
public final class GuestEntry {
    private static String marker = "";

    private GuestEntry() {}

    public static String record(String value) {
        if (value == null || !value.matches("[A-Za-z0-9_-]{1,80}")) {
            throw new IllegalArgumentException("marker");
        }
        marker = value;
        return marker;
    }

    public static String read() {
        return marker;
    }
}
