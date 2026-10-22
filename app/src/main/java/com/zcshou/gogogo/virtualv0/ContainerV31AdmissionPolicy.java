package com.zcshou.gogogo.virtualv0;

/** Fail-closed identity rules for the one self-authored external demo. */
public final class ContainerV31AdmissionPolicy {
    public static final String PACKAGE = "com.soozooq.tianwaitian.externaldemo";
    public static final String ENTRY = PACKAGE + ".DemoEntry";
    public static final String ACTIVITY = PACKAGE + ".DemoActivity";
    public static final long VERSION_CODE = 1;
    public static final long MAX_BYTES = 8L * 1024 * 1024;

    private ContainerV31AdmissionPolicy() {}

    public static boolean matchesDigest(String expected, String actual) {
        if (!validDigest(expected) || !validDigest(actual)) return false;
        int difference = 0;
        for (int i = 0; i < expected.length(); i++) {
            difference |= expected.charAt(i) ^ actual.charAt(i);
        }
        return difference == 0;
    }

    public static boolean matchesIdentity(String packageName, long versionCode,
            String activityName, int activityCount, int permissionCount,
            int otherComponentCount) {
        return PACKAGE.equals(packageName) && versionCode == VERSION_CODE
                && ACTIVITY.equals(activityName) && activityCount == 1
                && permissionCount == 0 && otherComponentCount == 0;
    }

    public static boolean validLength(long bytes) {
        return bytes >= 1024 && bytes <= MAX_BYTES;
    }

    private static boolean validDigest(String digest) {
        return digest != null && digest.matches("[0-9a-f]{64}");
    }
}
