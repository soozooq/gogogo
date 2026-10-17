package com.zcshou.gogogo.virtualv0;

/** Pure Java evidence check; a passing result is NOT proof of independent app UIDs. */
public final class VirtualContainerV0TwoSlotVerdict {
    private VirtualContainerV0TwoSlotVerdict() {}

    public static boolean passed(int hostPid, int hostUid,
            int pidA, int uidA, int pidB, int uidB,
            String expectedA, String observedA, String expectedB, String observedB) {
        return hostPid > 0 && hostUid >= 0
                && pidA > 0 && pidB > 0
                && pidA != hostPid && pidB != hostPid && pidA != pidB
                && uidA == hostUid && uidB == hostUid
                && expectedA != null && expectedB != null
                && !expectedA.equals(expectedB)
                && expectedA.equals(observedA) && expectedB.equals(observedB);
    }
}
