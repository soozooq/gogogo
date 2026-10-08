package com.zcshou.gogogo;

import org.junit.Test;
import static org.junit.Assert.*;

public class TencentProbeSessionStateTest {
    @Test public void activeRequestAcceptsOnlyItsToken() {
        TencentProbeSessionState state = new TencentProbeSessionState();
        long current = state.begin();
        assertTrue(state.accepts(current));
        assertFalse(state.accepts(current + 1));
    }

    @Test public void lateCallbackFromCancelledRequestIsIgnored() {
        TencentProbeSessionState state = new TencentProbeSessionState();
        long old = state.begin();
        state.cancel();
        assertFalse(state.accepts(old));
        long next = state.begin();
        assertTrue(state.accepts(next));
        assertFalse(state.accepts(old));
    }

    @Test public void completedSingleRequestCannotAcceptAnotherCallback() {
        TencentProbeSessionState state = new TencentProbeSessionState();
        long id = state.begin();
        assertTrue(state.finish(id));
        assertFalse(state.accepts(id));
        assertFalse(state.finish(id));
    }

    @Test public void completingOldRequestDoesNotStopReplacement() {
        TencentProbeSessionState state = new TencentProbeSessionState();
        long old = state.begin();
        long current = state.begin();
        assertFalse(state.finish(old));
        assertTrue(state.accepts(current));
    }
}
