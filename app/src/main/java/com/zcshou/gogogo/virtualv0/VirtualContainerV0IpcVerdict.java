package com.zcshou.gogogo.virtualv0;

/** Pure-Java predicate: a distinct Android process is not an isolated app UID. */
public final class VirtualContainerV0IpcVerdict {
    private VirtualContainerV0IpcVerdict() {}

    public static boolean passed(int hostPid, int hostUid, int remotePid, int remoteUid) {
        return hostPid > 0 && remotePid > 0 && hostPid != remotePid
                && hostUid >= 0 && hostUid == remoteUid;
    }
}
