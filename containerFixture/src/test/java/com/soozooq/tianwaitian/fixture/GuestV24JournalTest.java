package com.soozooq.tianwaitian.fixture;

import org.junit.Test;
import static org.junit.Assert.*;

public final class GuestV24JournalTest {
    private static final String OLD = "11111111-2222-3333-4444-555555555555";
    private static final String NEW = "66666666-7777-8888-9999-aaaaaaaaaaaa";
    private static final String ALT = "bbbbbbbb-cccc-dddd-eeee-ffffffffffff";
    private static final String SHA = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    @Test public void pendingOldRequestHasZeroCompletions() {
        String j = GuestV24Journal.prepare(OLD,SHA,124);
        assertTrue(GuestV24Journal.isPending(j,OLD,SHA,124));
        assertEquals("PENDING",GuestV24Journal.state(j));
        assertTrue(GuestV24Journal.acceptOldResult(j,OLD));
    }
    @Test public void coldPidIsDifferentAndOldIsAborted() {
        String first = GuestV24Journal.prepare(OLD,SHA,124);
        String recovered = GuestV24Journal.recover(first,OLD,NEW,SHA,303);
        assertEquals("RECOVERED",GuestV24Journal.state(recovered));
        assertTrue(GuestV24Journal.isRecovered(recovered,OLD,NEW,SHA,124,303));
        assertFalse(GuestV24Journal.acceptOldResult(recovered,OLD));
    }
    @Test public void duplicateRecoveryDoesNotExecuteAgain() {
        String recovered = GuestV24Journal.recover(GuestV24Journal.prepare(OLD,SHA,124),OLD,NEW,SHA,303);
        assertEquals(recovered,GuestV24Journal.recover(recovered,OLD,NEW,SHA,303));
    }
    @Test(expected=IllegalStateException.class) public void rejectDifferentRecoveryToken() {
        String j=GuestV24Journal.recover(GuestV24Journal.prepare(OLD,SHA,124),OLD,NEW,SHA,303);
        GuestV24Journal.recover(j,OLD,ALT,SHA,303);
    }
    @Test(expected=IllegalStateException.class) public void rejectSameWorkerPid() {
        GuestV24Journal.recover(GuestV24Journal.prepare(OLD,SHA,124),OLD,NEW,SHA,124);
    }
    @Test(expected=IllegalStateException.class) public void rejectMismatchedDigest() {
        GuestV24Journal.recover(GuestV24Journal.prepare(OLD,SHA,124),OLD,NEW,SHA.replace('0','a'),303);
    }
    @Test(expected=IllegalArgumentException.class) public void refuseInvalidJournal() {
        GuestV24Journal.recover("anything",OLD,NEW,SHA,303);
    }
    @Test(expected=IllegalArgumentException.class) public void refuseTokenReuse() {
        GuestV24Journal.recover(GuestV24Journal.prepare(OLD,SHA,124),OLD,OLD,SHA,303);
    }
    @Test(expected=IllegalArgumentException.class) public void refuseIncompletePayload() {
        GuestV24Journal.prepare("bad",SHA,124);
    }
}
