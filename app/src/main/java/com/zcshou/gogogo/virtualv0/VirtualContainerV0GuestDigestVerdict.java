package com.zcshou.gogogo.virtualv0;

/** JVM-testable identity check; SHA equality does not imply UID isolation. */
public final class VirtualContainerV0GuestDigestVerdict {
    private VirtualContainerV0GuestDigestVerdict() {}

    public static boolean validDigest(String sha) {
        return sha != null && sha.matches("[0-9a-f]{64}");
    }

    public static boolean passed(String digestA, String digestB) {
        return validDigest(digestA) && digestA.equals(digestB);
    }
}
