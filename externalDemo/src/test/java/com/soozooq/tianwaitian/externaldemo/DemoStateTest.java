package com.soozooq.tianwaitian.externaldemo;

import org.junit.Test;

import static org.junit.Assert.*;

public final class DemoStateTest {
    @Test public void instancesAndRestoredCountsAreIndependent() {
        DemoState first = new DemoState(4);
        DemoState second = new DemoState(0);
        assertEquals(5, first.click());
        assertEquals(0, second.getClickCount());
        DemoState restored = new DemoState(first.getClickCount());
        assertEquals(6, restored.click());
        assertEquals(5, first.getClickCount());
    }

    @Test public void actualLifecycleCanPauseResumeAndRestart() {
        DemoState state = new DemoState(0);
        for (String event : new String[]{"onCreate", "onStart", "onResume", "onPause",
                "onSaveInstanceState", "onResume", "onPause", "onStop", "onRestart", "onStart",
                "onResume", "onPause", "onStop", "onDestroy"}) {
            assertTrue(state.onLifecycle(event).startsWith(event));
        }
        state.dispose();
        assertTrue(state.isDisposed());
        assertTrue(state.snapshot().contains("LIFECYCLE onDestroy"));
        assertTrue(state.snapshot().contains("LIFECYCLE onRestart"));
        assertTrue(state.snapshot().contains("DISPOSE"));
    }

    @Test public void disposedSessionRejectsEventsAndClicks() {
        DemoState state = new DemoState(2);
        state.dispose();
        state.dispose();
        assertThrows(IllegalStateException.class, state::click);
        assertThrows(IllegalStateException.class, () -> state.onLifecycle("onCreate"));
        assertEquals(2, state.getClickCount());
    }

    @Test public void invalidOrOutOfOrderLifecycleFails() {
        DemoState state = new DemoState(0);
        assertThrows(IllegalStateException.class, () -> state.onLifecycle("onResume"));
        state.onLifecycle("onCreate");
        assertThrows(IllegalStateException.class, () -> state.onLifecycle("onCreate"));
        assertThrows(IllegalStateException.class, () -> state.onLifecycle("unknown"));
        state.onLifecycle("onDestroy");
        assertThrows(IllegalStateException.class, state::click);
    }

    @Test public void restoredCountsAreBoundedAndCounterDoesNotOverflow() {
        assertThrows(IllegalArgumentException.class, () -> new DemoState(-1));
        assertThrows(IllegalArgumentException.class,
                () -> new DemoState(DemoState.MAX_CLICKS + 1));
        DemoState maximum = new DemoState(DemoState.MAX_CLICKS);
        assertEquals(DemoState.MAX_CLICKS, maximum.click());
    }

    @Test public void restartIsOnlyAcceptedAfterStopAndDoesNotReplaceStoppedState() {
        DemoState state = new DemoState(3);
        assertThrows(IllegalStateException.class, () -> state.onLifecycle("onRestart"));
        state.onLifecycle("onCreate");
        assertThrows(IllegalStateException.class, () -> state.onLifecycle("onRestart"));
        state.onLifecycle("onStart");
        state.onLifecycle("onResume");
        assertThrows(IllegalStateException.class, () -> state.onLifecycle("onRestart"));
        state.onLifecycle("onPause");
        state.onLifecycle("onStop");
        assertTrue(state.onLifecycle("onRestart").startsWith("onRestart"));
        assertTrue(state.snapshot().contains("lifecycle=onStop"));
        state.onLifecycle("onStart");
        state.onLifecycle("onResume");
        assertEquals(4, state.click());
    }
}
