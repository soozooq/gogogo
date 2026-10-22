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

    @Test public void savedSessionRestoresNoteCountAndNavigationAcrossARealClose() {
        DemoState first = new DemoState(0);
        first.onLifecycle("onCreate");
        first.onLifecycle("onStart");
        first.onLifecycle("onResume");
        first.click();
        first.click();
        first.setNote("下次打开继续编辑");
        first.openDetails();
        DemoState.SavedState saved = first.save();
        for (String event : new String[]{"onPause", "onSaveInstanceState", "onStop", "onDestroy"}) {
            first.onLifecycle(event);
        }
        first.dispose();

        DemoState reopened = new DemoState(0);
        reopened.restore(saved);
        reopened.onLifecycle("onCreate");
        reopened.onLifecycle("onStart");
        reopened.onLifecycle("onResume");
        assertEquals(2, reopened.getClickCount());
        assertEquals("下次打开继续编辑", reopened.getNote());
        assertEquals(DemoState.PAGE_DETAILS, reopened.getPage());
        assertTrue(reopened.handleBack());
        assertFalse(reopened.handleBack());
        assertEquals(3, reopened.click());
        assertEquals(2, first.getClickCount());
        assertTrue(first.isDisposed());
    }

    @Test public void malformedSavedStateCannotBeConstructedOrApplied() {
        assertThrows(IllegalArgumentException.class,
                () -> new DemoState.SavedState(2, 1, "", DemoState.PAGE_HOME));
        assertThrows(IllegalArgumentException.class,
                () -> new DemoState.SavedState(1, -1, "", DemoState.PAGE_HOME));
        assertThrows(IllegalArgumentException.class,
                () -> new DemoState.SavedState(1, DemoState.MAX_CLICKS + 1, "", DemoState.PAGE_HOME));
        assertThrows(IllegalArgumentException.class,
                () -> new DemoState.SavedState(1, 1, null, DemoState.PAGE_HOME));
        assertThrows(IllegalArgumentException.class,
                () -> new DemoState.SavedState(1, 1, "x".repeat(501), DemoState.PAGE_HOME));
        assertThrows(IllegalArgumentException.class,
                () -> new DemoState.SavedState(1, 1, "", "unknown"));
        assertThrows(IllegalArgumentException.class,
                () -> new DemoState.SavedState(1, 1, "", null));
        DemoState target = new DemoState(7);
        assertThrows(IllegalStateException.class, () -> target.restore(null));
        assertEquals(7, target.getClickCount());
    }

    @Test public void restoreCannotOverwriteAnAlreadyRunningSession() {
        DemoState.SavedState saved = new DemoState.SavedState(1, 4, "saved", DemoState.PAGE_DETAILS);
        DemoState running = new DemoState(1);
        running.onLifecycle("onCreate");
        running.setNote("live");
        assertThrows(IllegalStateException.class, () -> running.restore(saved));
        assertEquals(1, running.getClickCount());
        assertEquals("live", running.getNote());
        assertEquals(DemoState.PAGE_HOME, running.getPage());
        running.dispose();
        assertThrows(IllegalStateException.class, () -> running.restore(saved));
    }

    @Test public void notesAndPagesArePerInstanceAndDiagnosticsDoNotExportNoteText() {
        DemoState first = new DemoState(0);
        DemoState second = new DemoState(0);
        String privateNote = "A private personal note";
        first.setNote(privateNote);
        first.openDetails();
        assertEquals("", second.getNote());
        assertEquals(DemoState.PAGE_HOME, second.getPage());
        assertFalse(first.snapshot().contains(privateNote));
        assertTrue(first.snapshot().contains("noteLength=" + privateNote.length()));
        assertThrows(IllegalArgumentException.class, () -> first.setNote("x".repeat(501)));
        assertEquals(privateNote, first.getNote());
    }

    @Test public void aClosedSessionCanBeReadButCannotBeEdited() {
        DemoState first = new DemoState(2);
        first.setNote("retained");
        first.dispose();
        DemoState.SavedState saved = first.save();
        assertEquals("retained", saved.note);
        assertThrows(IllegalStateException.class, () -> first.setNote("changed"));
        assertThrows(IllegalStateException.class, first::openDetails);
        assertThrows(IllegalStateException.class, first::handleBack);
        DemoState reopened = new DemoState(0);
        reopened.restore(saved);
        reopened.setNote("next session");
        assertEquals("retained", first.getNote());
    }

    @Test public void historicalStateAndLifecycleNeverCountAsCurrentInteraction() {
        DemoState resumed = new DemoState(9);
        assertEquals(0, resumed.getInteractionCount());
        resumed.restore(new DemoState.SavedState(1, 17, "saved note", DemoState.PAGE_DETAILS));
        for (String event : new String[]{"onCreate", "onStart", "onResume", "onPause",
                "onSaveInstanceState", "onResume"}) resumed.onLifecycle(event);
        assertEquals(17, resumed.save().clicks);
        assertEquals(0, resumed.getInteractionCount());
        resumed.setNote("saved note");
        resumed.openDetails();
        assertEquals(0, resumed.getInteractionCount());
        resumed.setNote("edited this time");
        assertEquals(1, resumed.getInteractionCount());
        assertTrue(resumed.handleBack());
        assertEquals(2, resumed.getInteractionCount());
        assertFalse(resumed.handleBack());
        assertEquals(2, resumed.getInteractionCount());
        resumed.openDetails();
        resumed.openDetails();
        assertEquals(3, resumed.getInteractionCount());
        assertEquals(18, resumed.click());
        assertEquals(4, resumed.getInteractionCount());
        resumed.save();
        assertEquals(4, resumed.getInteractionCount());
    }

    @Test public void eachRealClickCountsEvenWhenTheStoredCounterIsAtItsLimit() {
        DemoState maximum = new DemoState(DemoState.MAX_CLICKS);
        assertEquals(DemoState.MAX_CLICKS, maximum.click());
        assertEquals(DemoState.MAX_CLICKS, maximum.click());
        assertEquals(2, maximum.getInteractionCount());
        maximum.dispose();
        assertThrows(IllegalStateException.class, maximum::click);
        assertThrows(IllegalStateException.class, () -> maximum.setNote("closed"));
        assertEquals(2, maximum.getInteractionCount());
    }

    @Test public void restoreStartsANewInteractionBaselineWithoutSerializingOldOperations() {
        DemoState state = new DemoState(0);
        state.click();
        state.setNote("first");
        assertEquals(2, state.getInteractionCount());
        DemoState.SavedState saved = state.save();
        state.restore(saved);
        assertEquals(0, state.getInteractionCount());
        assertEquals(1, state.getClickCount());
        assertEquals("first", state.getNote());
    }
}
