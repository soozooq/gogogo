package com.zcshou.gogogo;

import java.util.ArrayDeque;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;

/** Per-service latest frames on a process-wide, Task-completion FIFO. */
public final class AsyncProviderOperationQueue<T> {
    @FunctionalInterface
    public interface Operation {
        /** Call completion only after the operation is known to be terminal. */
        void start(Runnable completion);
    }

    /** Requires an asynchronous serial executor, also suitable for Task callbacks. */
    public static final class Dispatcher {
        private static final class Job {
            final Operation operation;
            Job(Operation operation) { this.operation = operation; }
        }

        private final Executor executor;
        private final ArrayDeque<Job> waiting = new ArrayDeque<>();
        private Job current;
        private long failures;

        public Dispatcher(Executor executor) { this.executor = Objects.requireNonNull(executor); }

        private boolean enqueue(Operation operation) {
            Job launch;
            synchronized (this) {
                waiting.addLast(new Job(operation));
                launch = takeNext();
            }
            return launch == null || dispatch(launch);
        }

        private Job takeNext() {
            if (current != null || waiting.isEmpty()) return null;
            current = waiting.removeFirst();
            return current;
        }

        private boolean dispatch(Job job) {
            try {
                executor.execute(() -> {
                    try {
                        job.operation.start(() -> complete(job));
                    } catch (RuntimeException unknown) {
                        // An adapter may have issued an asynchronous request before
                        // throwing. Do not invent completion or start a conflicting job.
                        markUnobserved();
                    }
                });
                return true;
            } catch (RuntimeException rejected) {
                markUnobserved();
                return false;
            }
        }

        private void complete(Job job) {
            Job launch;
            synchronized (this) {
                if (current != job) return; // Duplicate/old callbacks cannot release a newer job.
                current = null;
                launch = takeNext();
            }
            if (launch != null) dispatch(launch);
        }

        /** Records unavailable completion evidence without releasing the current job. */
        public synchronized void markUnobserved() { failures++; }
        public synchronized long failures() { return failures; }
    }

    private final Dispatcher dispatcher;
    private final BiConsumer<T, Runnable> publisher;
    private boolean closed;
    private boolean frameQueued;
    private T latestFrame;
    private long coalesced;

    public AsyncProviderOperationQueue(Dispatcher dispatcher, BiConsumer<T, Runnable> publisher) {
        this.dispatcher = Objects.requireNonNull(dispatcher);
        this.publisher = Objects.requireNonNull(publisher);
    }

    public synchronized boolean runWhenOpen(Operation operation) {
        Objects.requireNonNull(operation);
        if (closed) return false;
        return dispatcher.enqueue(done -> {
            if (isClosed()) done.run(); else operation.start(done);
        });
    }

    public synchronized boolean submit(T frame) {
        Objects.requireNonNull(frame);
        if (closed) return false;
        if (latestFrame != null) coalesced++;
        latestFrame = frame;
        if (frameQueued) return true;
        frameQueued = true;
        return dispatcher.enqueue(this::publishLatest);
    }

    private void publishLatest(Runnable done) {
        T frame;
        synchronized (this) {
            frameQueued = false;
            frame = closed ? null : latestFrame;
            latestFrame = null;
        }
        if (frame == null) done.run(); else publisher.accept(frame, done);
    }

    /** Queued cleanup is not evidence that the asynchronous provider is reset. */
    public synchronized boolean close(Operation cleanup) {
        Objects.requireNonNull(cleanup);
        if (closed) return false;
        closed = true;
        latestFrame = null;
        return dispatcher.enqueue(cleanup);
    }

    public synchronized boolean isClosed() { return closed; }
    public synchronized long coalescedFrames() { return coalesced; }
}
