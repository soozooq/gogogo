package com.soozooq.tianwaitian.fixture;

import org.junit.Test;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;

public final class GuestLifecycleV1Test {
    @Test public void completeForegroundBackgroundAndExit() {
        GuestLifecycleV1 guest = new GuestLifecycleV1();
        for (String event : new String[] {
                "onCreate", "onStart", "onResume", "onPause", "onStop",
                "onRestart", "onStart", "onResume", "onPause", "onStop",
                "onDestroy"
        }) guest.onEvent(event);
        assertTrue(guest.snapshot().contains("guestState=DESTROYED"));
        assertTrue(guest.snapshot().contains("resumes=2"));
        assertTrue(guest.snapshot().contains("events=11"));
    }

    @Test public void supportsPauseWithoutStopThenResume() {
        GuestLifecycleV1 guest = new GuestLifecycleV1();
        for (String event : new String[] {
                "onCreate", "onStart", "onResume", "onPause", "onResume",
                "onPause", "onStop", "onDestroy"
        }) guest.onEvent(event);
        assertTrue(guest.snapshot().contains("resumes=2"));
    }

    @Test(expected=IllegalStateException.class)
    public void rejectsResumeBeforeCreate() {
        new GuestLifecycleV1().onEvent("onResume");
    }

    @Test(expected=IllegalArgumentException.class)
    public void rejectsUnknownMethod() {
        new GuestLifecycleV1().onEvent("fakeResume");
    }

    @Test(expected=IllegalStateException.class)
    public void rejectsEventsAfterDestroy() {
        GuestLifecycleV1 guest = new GuestLifecycleV1();
        guest.onEvent("onCreate");
        guest.onEvent("onDestroy");
        guest.onEvent("onResume");
    }

    @Test public void snapshotStartsEmpty() {
        assertEquals("guestState=NEW；events=0；resumes=0\n", new GuestLifecycleV1().snapshot());
    }
}
