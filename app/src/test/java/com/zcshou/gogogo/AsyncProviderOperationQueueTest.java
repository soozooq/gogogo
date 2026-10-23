package com.zcshou.gogogo;

import org.junit.Test;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

public class AsyncProviderOperationQueueTest {
    private static final class Worker implements Executor {
        final ArrayDeque<Runnable> jobs = new ArrayDeque<>();
        @Override public void execute(Runnable job) { jobs.addLast(job); }
        void drain() { while (!jobs.isEmpty()) jobs.removeFirst().run(); }
    }

    @Test public void returnedRequestDoesNotReleaseNextModeUntilTaskCompletes() {
        Worker worker = new Worker();
        AsyncProviderOperationQueue.Dispatcher shared = new AsyncProviderOperationQueue.Dispatcher(worker);
        List<String> events = new ArrayList<>();
        List<Runnable> completions = new ArrayList<>();
        AsyncProviderOperationQueue<Integer> old = new AsyncProviderOperationQueue<>(shared, (frame,done) -> done.run());
        AsyncProviderOperationQueue<Integer> next = new AsyncProviderOperationQueue<>(shared, (frame,done) -> done.run());
        old.runWhenOpen(done -> { events.add("old enable"); completions.add(done); });
        worker.drain();
        old.close(done -> { events.add("old disable"); completions.add(done); });
        next.runWhenOpen(done -> { events.add("new enable"); completions.add(done); });
        worker.drain();
        assertEquals(Arrays.asList("old enable"), events);
        completions.get(0).run(); worker.drain();
        assertEquals(Arrays.asList("old enable","old disable"), events);
        completions.get(1).run(); worker.drain();
        assertEquals(Arrays.asList("old enable","old disable","new enable"), events);
    }

    @Test public void closeWaitsForPublishedTaskAndDropsOldQueuedCoordinates() {
        Worker worker = new Worker();
        AsyncProviderOperationQueue.Dispatcher shared = new AsyncProviderOperationQueue.Dispatcher(worker);
        List<String> events = new ArrayList<>();
        List<Runnable> pending = new ArrayList<>();
        AsyncProviderOperationQueue<Integer> old = new AsyncProviderOperationQueue<>(shared,
                (frame,done) -> { events.add("publish:"+frame); pending.add(done); });
        old.submit(1); worker.drain();
        for (int i=2;i<1002;i++) old.submit(i);
        assertTrue(old.close(done -> { events.add("disable"); done.run(); }));
        AsyncProviderOperationQueue<Integer> next = new AsyncProviderOperationQueue<>(shared,
                (frame,done) -> { events.add("new:"+frame); done.run(); });
        next.runWhenOpen(done -> { events.add("enable"); done.run(); });
        next.submit(2000); worker.drain();
        assertEquals(Arrays.asList("publish:1"), events);
        assertFalse(old.submit(1003));
        pending.get(0).run(); worker.drain();
        assertEquals(Arrays.asList("publish:1","disable","enable","new:2000"),events);
        assertEquals(999,old.coalescedFrames());
    }

    @Test public void pendingTaskRetainsOnlyTheLatestNextFrame() {
        Worker worker = new Worker();
        AsyncProviderOperationQueue.Dispatcher shared = new AsyncProviderOperationQueue.Dispatcher(worker);
        List<Integer> frames = new ArrayList<>();
        List<Runnable> pending = new ArrayList<>();
        AsyncProviderOperationQueue<Integer> queue = new AsyncProviderOperationQueue<>(shared,
                (frame,done) -> { frames.add(frame); pending.add(done); });
        queue.submit(0); worker.drain();
        for(int i=1;i<=1000;i++) queue.submit(i);
        assertTrue(worker.jobs.isEmpty());
        pending.get(0).run(); worker.drain();
        assertEquals(Arrays.asList(0,1000),frames);
        assertEquals(999,queue.coalescedFrames());
    }

    @Test public void duplicateCompletionCannotReleaseAnotherTask() {
        Worker worker = new Worker();
        AsyncProviderOperationQueue.Dispatcher shared = new AsyncProviderOperationQueue.Dispatcher(worker);
        List<Runnable> pending = new ArrayList<>();
        List<Integer> events = new ArrayList<>();
        AsyncProviderOperationQueue<Integer> queue = new AsyncProviderOperationQueue<>(shared,
                (frame,done) -> { events.add(frame); pending.add(done); });
        queue.submit(1); worker.drain();
        queue.submit(2); pending.get(0).run(); worker.drain();
        queue.submit(3); pending.get(0).run(); worker.drain();
        assertEquals(Arrays.asList(1,2),events);
        pending.get(1).run(); worker.drain();
        assertEquals(Arrays.asList(1,2,3),events);
    }

    @Test public void stoppedBeforeDispatchSkipsEnableButStillAttemptsCleanupOnce() {
        Worker worker = new Worker();
        List<String> events = new ArrayList<>();
        AsyncProviderOperationQueue<Integer> queue = new AsyncProviderOperationQueue<>(
                new AsyncProviderOperationQueue.Dispatcher(worker),(frame,done) -> { events.add("frame"); done.run(); });
        queue.runWhenOpen(done -> { events.add("enable"); done.run(); });
        queue.submit(1);
        queue.close(done -> { events.add("cleanup"); done.run(); });
        assertFalse(queue.close(done -> fail("second cleanup")));
        assertFalse(queue.runWhenOpen(done -> fail("closed")));
        worker.drain();
        assertEquals(Arrays.asList("cleanup"),events);
    }

    @Test public void unknownCompletionNeverInventsATerminalResultOrTimesOutOpen() {
        Worker worker = new Worker();
        AsyncProviderOperationQueue.Dispatcher shared = new AsyncProviderOperationQueue.Dispatcher(worker);
        List<Runnable> pending = new ArrayList<>();
        List<String> events = new ArrayList<>();
        AsyncProviderOperationQueue<Integer> queue = new AsyncProviderOperationQueue<>(shared,(frame,done) -> done.run());
        queue.runWhenOpen(done -> { pending.add(done); shared.markUnobserved(); });
        worker.drain();
        queue.close(done -> { events.add("cleanup"); done.run(); });
        worker.drain();
        assertTrue(events.isEmpty());
        assertEquals(1,shared.failures());
        // A real late callback can still unblock it; no timer may do so.
        pending.get(0).run(); worker.drain();
        assertEquals(Arrays.asList("cleanup"),events);
    }

    @Test public void unexpectedAdapterFailureCannotStartConflictingCalls() {
        Worker worker = new Worker();
        AsyncProviderOperationQueue.Dispatcher shared = new AsyncProviderOperationQueue.Dispatcher(worker);
        AsyncProviderOperationQueue<Integer> queue = new AsyncProviderOperationQueue<>(shared,(frame,done) -> done.run());
        queue.runWhenOpen(done -> { throw new IllegalStateException("unknown dispatch outcome"); });
        worker.drain();
        queue.close(done -> fail("must not run without terminal evidence"));
        worker.drain();
        assertEquals(1,shared.failures());
    }

    @Test public void rejectedWorkerReportsFailureWithoutClaimingExecution() {
        AsyncProviderOperationQueue.Dispatcher shared = new AsyncProviderOperationQueue.Dispatcher(
                operation -> { throw new RejectedExecutionException(); });
        AsyncProviderOperationQueue<Integer> queue = new AsyncProviderOperationQueue<>(shared,(frame,done) -> fail());
        assertFalse(queue.runWhenOpen(done -> fail()));
        assertEquals(1,shared.failures());
    }

    @Test public void pendingTaskLeavesWorkerFreeAndCompletionFromAnotherThreadPreservesOrder() throws Exception {
        ExecutorService worker = Executors.newSingleThreadExecutor();
        AsyncProviderOperationQueue.Dispatcher shared = new AsyncProviderOperationQueue.Dispatcher(worker);
        List<String> events = new CopyOnWriteArrayList<>();
        AtomicReference<Runnable> completion = new AtomicReference<>();
        CountDownLatch oldStarted = new CountDownLatch(1);
        CountDownLatch nextStarted = new CountDownLatch(1);
        AsyncProviderOperationQueue<Integer> old = new AsyncProviderOperationQueue<>(shared,(frame,done) -> done.run());
        AsyncProviderOperationQueue<Integer> next = new AsyncProviderOperationQueue<>(shared,(frame,done) -> done.run());
        try {
            old.runWhenOpen(done -> { events.add("old enable"); completion.set(done); oldStarted.countDown(); });
            assertTrue(oldStarted.await(2,TimeUnit.SECONDS));
            old.close(done -> { events.add("old disable"); done.run(); });
            next.runWhenOpen(done -> { events.add("new enable"); nextStarted.countDown(); done.run(); });
            worker.submit(() -> {}).get(2,TimeUnit.SECONDS);
            assertEquals(1L,nextStarted.getCount());
            completion.get().run(); // A GMS terminal callback need not arrive on the worker.
            assertTrue(nextStarted.await(2,TimeUnit.SECONDS));
            assertEquals(Arrays.asList("old enable","old disable","new enable"),events);
        } finally { worker.shutdownNow(); }
    }
}
