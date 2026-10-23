package com.zcshou.gogogo;

import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * One service's operations on a shared asynchronous FIFO, single-thread executor.
 * A close drops pending frames, rejects new work and queues final cleanup after
 * every already-running operation. Different service instances must share the
 * same executor so old cleanup also precedes a new instance's registration.
 * No provider I/O or caller callback runs under this object's monitor.
 */
public final class ProviderOperationQueue<T> {
    private final Executor executor;
    private final Consumer<T> publisher;
    private boolean closed;
    private boolean frameQueued;
    private T latestFrame;
    private long coalescedFrames;
    private long failures;

    public ProviderOperationQueue(Executor executor, Consumer<T> publisher) {
        this.executor = Objects.requireNonNull(executor);
        this.publisher = Objects.requireNonNull(publisher);
    }

    public synchronized boolean runWhenOpen(Runnable action) {
        Objects.requireNonNull(action);
        if (closed) return false;
        return enqueue(() -> {
            if (!isClosed()) action.run();
        });
    }

    public synchronized boolean submit(T frame) {
        Objects.requireNonNull(frame);
        if (closed) return false;
        if (latestFrame != null) coalescedFrames++;
        latestFrame = frame;
        if (frameQueued) return true;
        frameQueued = true;
        if (enqueue(this::publishLatest)) return true;
        frameQueued = false;
        latestFrame = null;
        return false;
    }

    private void publishLatest() {
        T frame;
        synchronized (this) {
            frameQueued = false;
            frame = latestFrame;
            latestFrame = null;
            if (closed) return;
        }
        if (frame != null) publisher.accept(frame);
    }

    /** true means cleanup was queued, not that Android removed its providers. */
    public synchronized boolean close(Runnable cleanup) {
        Objects.requireNonNull(cleanup);
        if (closed) return false;
        closed = true;
        latestFrame = null;
        return enqueue(cleanup);
    }

    public synchronized boolean isClosed() { return closed; }
    public synchronized long coalescedFrames() { return coalescedFrames; }
    public synchronized long failures() { return failures; }

    private boolean enqueue(Runnable operation) {
        try {
            executor.execute(() -> {
                try {
                    operation.run();
                } catch (RuntimeException failure) {
                    recordFailure();
                }
            });
            return true;
        } catch (RuntimeException rejected) {
            recordFailure();
            return false;
        }
    }

    private synchronized void recordFailure() { failures++; }
}
