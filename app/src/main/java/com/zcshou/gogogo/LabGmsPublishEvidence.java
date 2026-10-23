package com.zcshou.gogogo;

/**
 * Per-ServiceGo evidence for asynchronous GMS setMockLocation Tasks.
 *
 * A returned Task is only a submission, not confirmation that any consumer
 * received the location. Counts intentionally have no coordinates or device IDs.
 */
public final class LabGmsPublishEvidence {
    private long submitted;
    private long taskSucceeded;
    private long taskFailed;
    private long callbackUnavailable;
    private long synchronousFailure;

    public synchronized void onTaskReturned() {
        submitted++;
    }

    public synchronized void onTaskCompleted(boolean successful) {
        if (successful) {
            taskSucceeded++;
        } else {
            taskFailed++;
        }
    }

    public synchronized void onCallbackUnavailable() {
        callbackUnavailable++;
    }

    /** A previously unobserved Task finally delivered its terminal callback. */
    public synchronized void onCallbackRecovered() {
        if (callbackUnavailable > 0L) callbackUnavailable--;
    }

    public synchronized void onSynchronousFailure() {
        synchronousFailure++;
    }

    public synchronized Snapshot snapshot() {
        long pending = Math.max(0L,
                submitted - taskSucceeded - taskFailed - callbackUnavailable);
        return new Snapshot(submitted, taskSucceeded, taskFailed,
                callbackUnavailable, synchronousFailure, pending);
    }

    public static final class Snapshot {
        public final long submitted;
        public final long taskSucceeded;
        public final long taskFailed;
        public final long callbackUnavailable;
        public final long synchronousFailure;
        public final long pending;

        private Snapshot(long submitted, long taskSucceeded, long taskFailed,
                         long callbackUnavailable, long synchronousFailure,
                         long pending) {
            this.submitted = submitted;
            this.taskSucceeded = taskSucceeded;
            this.taskFailed = taskFailed;
            this.callbackUnavailable = callbackUnavailable;
            this.synchronousFailure = synchronousFailure;
            this.pending = pending;
        }
    }
}
