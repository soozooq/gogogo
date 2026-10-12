package com.zcshou.gogogo;

/**
 * Single-flight gate for asynchronously acknowledged GMS FLP mock publishing.
 *
 * This protects the binder/Task queue from 30 Hz unbounded submissions when
 * GMS is slower than ServiceGo. Framework GPS/NETWORK/FUSED publishers are
 * intentionally NOT throttled by this gate.
 *
 * An acknowledged Task is not proof that any other app consumed that fix.
 */
public final class LabGmsPublishGate {
    private boolean closed;
    private boolean pending;
    private long currentToken;
    private long submitted;
    private long succeeded;
    private long failed;
    private long coalesced;
    private long pendingSinceElapsed = -1L;
    private long lastSuccessElapsed = -1L;

    /** 0 indicates skip: another asynchronous location is still in flight. */
    public synchronized long begin(long elapsedMs) {
        if (closed) return 0L;
        if (pending) {
            coalesced++;
            return 0L;
        }
        pending = true;
        pendingSinceElapsed = elapsedMs;
        submitted++;
        return ++currentToken;
    }

    /** An obsolete callback may never release a newer task's slot. */
    public synchronized boolean complete(long token, boolean success, long elapsedMs) {
        if (closed || !pending || token <= 0L || token != currentToken) return false;
        pending = false;
        pendingSinceElapsed = -1L;
        if (success) {
            succeeded++;
            lastSuccessElapsed = elapsedMs;
        } else {
            failed++;
        }
        return true;
    }

    /** Called before ServiceGo cleanup. Late callbacks cannot revive a stopped gate. */
    public synchronized void close() {
        closed = true;
        pending = false;
        currentToken++;
    }

    public synchronized long submitted() { return submitted; }
    public synchronized long succeeded() { return succeeded; }
    public synchronized long failed() { return failed; }
    public synchronized long coalesced() { return coalesced; }
    public synchronized boolean pending() { return pending; }

    public synchronized long pendingAgeMs(long nowMs) {
        return pending && pendingSinceElapsed >= 0L
                ? Math.max(0L, nowMs - pendingSinceElapsed) : -1L;
    }

    public synchronized long lastSuccessAgeMs(long nowMs) {
        return lastSuccessElapsed >= 0L
                ? Math.max(0L, nowMs - lastSuccessElapsed) : -1L;
    }
}
