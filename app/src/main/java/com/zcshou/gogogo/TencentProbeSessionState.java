package com.zcshou.gogogo;

/**
 * Tracks one active Tencent diagnostic request. A callback from a cancelled or
 * replaced request must never be credited to the current request.
 * Pure Java so request lifetime rules are covered by ordinary JUnit tests.
 */
public final class TencentProbeSessionState {
    private long generation;
    private boolean active;

    public synchronized long begin() {
        generation++;
        active = true;
        return generation;
    }

    public synchronized void cancel() {
        generation++;
        active = false;
    }

    public synchronized boolean accepts(long id) {
        return active && id == generation;
    }

    public synchronized boolean finish(long id) {
        if (!accepts(id)) return false;
        active = false;
        return true;
    }
}
