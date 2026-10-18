package com.soozooq.tianwaitian.fixture;

import org.junit.Test;
import static org.junit.Assert.*;

public final class GuestV2SessionTest {
    private static GuestV2Session prepare() {
        GuestV2Session guest = new GuestV2Session();
        guest.onHostLifecycle("onCreate");
        guest.onHostLifecycle("onStart");
        guest.onHostLifecycle("onResume");
        guest.runScript();
        return guest;
    }

    @Test public void sequentialCancelAndUnexpectedIdRejected() {
        GuestV2Session guest = prepare();
        assertTrue(guest.snapshot().contains("accepted=4；completed=2；cancelled=1；expectedRejected=1"));
        assertTrue(guest.snapshot().contains("pending=D"));
        assertFalse(guest.passed());
    }

    @Test public void actualBackgroundAndReturnFinishesPendingRequest() {
        GuestV2Session guest = prepare();
        for (String name : new String[]{"onPause","onStop","onRestart","onStart","onResume"}) {
            guest.onHostLifecycle(name);
        }
        guest.finishAfterRealReturn();
        assertTrue(guest.passed());
        assertTrue(guest.snapshot().contains("completed=3"));
        guest.onHostLifecycle("onPause");
        guest.onHostLifecycle("onStop");
        guest.onHostLifecycle("onDestroy");
        guest.dispose();
        assertTrue(guest.snapshot().contains("disposed=true"));
    }

    @Test(expected=IllegalStateException.class)
    public void cannotClaimBackgroundWithoutStop() {
        GuestV2Session guest = prepare();
        guest.onHostLifecycle("onPause");
        guest.onHostLifecycle("onResume");
        guest.finishAfterRealReturn();
    }

    @Test(expected=IllegalStateException.class)
    public void repeatedScriptIsNotAccepted() {
        GuestV2Session guest = prepare();
        guest.runScript();
    }

    @Test public void abortedPendingRequestIsCleaned() {
        GuestV2Session guest = prepare();
        guest.onHostLifecycle("onDestroy");
        guest.dispose();
        assertFalse(guest.passed());
        assertTrue(guest.snapshot().contains("cancelled=2"));
        assertTrue(guest.snapshot().contains("pending=none"));
    }

    @Test(expected=IllegalArgumentException.class)
    public void rejectsFakeLifecycle() {
        new GuestV2Session().onHostLifecycle("pretendPause");
    }

    @Test(expected=IllegalStateException.class)
    public void cannotCompleteTwice() {
        GuestV2Session guest = prepare();
        for (String name : new String[]{"onPause","onStop","onRestart","onStart","onResume"}) {
            guest.onHostLifecycle(name);
        }
        guest.finishAfterRealReturn();
        guest.finishAfterRealReturn();
    }
}
