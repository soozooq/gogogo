package com.soozooq.tianwaitian.fixture;

import org.junit.Test;
import static org.junit.Assert.*;

public final class GuestV23RecreationTest {
    private static final String OLD = "11111111-2222-3333-4444-555555555555";
    private static final String NEW = "66666666-7777-8888-9999-aaaaaaaaaaaa";

    @Test public void pendingRequestCancelledBeforeOldGuestDisposal() {
        GuestV23Recreation old = new GuestV23Recreation();
        assertEquals("PENDING_D", old.prepareOld(OLD));
        assertEquals("CANCELLED_D", old.cancelOld(OLD));
        assertTrue(old.oldCleared());
        assertFalse(old.acceptLate(OLD));
        old.dispose();
        assertTrue(old.snapshot().contains("completions=0"));
        assertTrue(old.snapshot().contains("disposed=true"));
    }

    @Test public void freshInstanceRejectsStaleTokenAndCompletesOnlyOnce() {
        GuestV23Recreation fresh = new GuestV23Recreation();
        assertEquals("NEW_PENDING_D", fresh.startNew(OLD, NEW));
        assertFalse(fresh.acceptLate(OLD));
        assertEquals("GUEST_V2_RESULT:32V_REVOCER", fresh.completeNew(NEW));
        assertTrue(fresh.newReady());
        assertTrue(fresh.snapshot().contains("completions=1"));
    }

    @Test(expected=IllegalArgumentException.class)
    public void cannotCompleteWithOldToken() {
        GuestV23Recreation fresh = new GuestV23Recreation();
        fresh.startNew(OLD, NEW);
        fresh.completeNew(OLD);
    }

    @Test(expected=IllegalArgumentException.class)
    public void cannotReuseOldSessionToken() {
        new GuestV23Recreation().startNew(OLD, OLD);
    }

    @Test(expected=IllegalArgumentException.class)
    public void cannotCompleteFreshTwice() {
        GuestV23Recreation fresh = new GuestV23Recreation();
        fresh.startNew(OLD, NEW);
        fresh.completeNew(NEW);
        fresh.completeNew(NEW);
    }

    @Test public void teardownAbortsPendingWork() {
        GuestV23Recreation old = new GuestV23Recreation();
        old.prepareOld(OLD);
        old.dispose();
        assertFalse(old.oldCleared());
        assertTrue(old.snapshot().contains("pending=false"));
        assertTrue(old.snapshot().contains("disposed=true"));
    }

    @Test(expected=IllegalStateException.class)
    public void disposedGuestCannotAcceptNewWork() {
        GuestV23Recreation old = new GuestV23Recreation();
        old.dispose();
        old.prepareOld(OLD);
    }
}
