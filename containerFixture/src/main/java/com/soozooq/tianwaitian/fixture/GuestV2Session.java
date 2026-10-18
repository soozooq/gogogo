package com.soozooq.tianwaitian.fixture;

/**
 * Bounded, self-authored session protocol bundled in the independent fixture.
 * The host forwards Android lifecycle notifications to this ordinary Java
 * object; this object is NOT a system-launched guest Activity.
 */
public final class GuestV2Session {
    private String pendingId;
    private String pendingPayload;
    private boolean scripted;
    private boolean finished;
    private boolean disposed;
    private boolean sawRealPause;
    private boolean sawRealStop;
    private boolean sawRealRestart;
    private boolean sawReturnResume;
    private int accepted;
    private int cancelled;
    private int rejected;
    private int completed;
    private int lifecycleEvents;
    private final StringBuilder trace = new StringBuilder();

    public GuestV2Session() {
        log("GUEST session NEW");
    }

    public String onHostLifecycle(String event) {
        if (disposed) throw new IllegalStateException("session already disposed");
        if (!("onCreate".equals(event) || "onStart".equals(event)
                || "onResume".equals(event) || "onPause".equals(event)
                || "onStop".equals(event) || "onRestart".equals(event)
                || "onDestroy".equals(event))) {
            throw new IllegalArgumentException("invalid lifecycle event");
        }
        lifecycleEvents++;
        if ("onPause".equals(event) && scripted && !finished) sawRealPause = true;
        if ("onStop".equals(event) && sawRealPause) sawRealStop = true;
        if ("onRestart".equals(event) && sawRealStop) sawRealRestart = true;
        if ("onResume".equals(event) && sawRealRestart) sawReturnResume = true;
        log("GUEST forwarded HOST " + event);
        return event;
    }

    public String runScript() {
        if (disposed || scripted) throw new IllegalStateException("script already run");
        scripted = true;

        begin("A", "ONE_123");
        expect("GUEST_V2_RESULT:321_ENO", complete("A"));

        begin("B", "CANCEL_ME");
        cancel("B");
        if (pendingId != null) throw new IllegalStateException("cancel left pending request");

        begin("C", "RECOVER_42");
        try {
            complete("WRONG");
            throw new IllegalStateException("wrong request id unexpectedly accepted");
        } catch (IllegalArgumentException expected) {
            rejected++;
            log("GUEST expected invalid request ID rejected");
        }
        expect("GUEST_V2_RESULT:24_REVOCER", complete("C"));

        // D remains pending while the real HOST Activity backgrounds.
        begin("D", "AFTER_RETURN");
        assertCounters(4, 1, 1, 2);
        log("GUEST script prepared pending D; waiting for real host background/return");
        return snapshot();
    }

    public String finishAfterRealReturn() {
        if (!scripted || disposed || finished) {
            throw new IllegalStateException("session not ready for completion");
        }
        if (!(sawRealPause && sawRealStop && sawRealRestart && sawReturnResume)) {
            throw new IllegalStateException("real onPause/onStop/onRestart/onResume not observed");
        }
        expect("GUEST_V2_RESULT:NRUTER_RETFA", complete("D"));
        assertCounters(4, 1, 1, 3);
        finished = true;
        log("GUEST SESSION PASS: requests A,C,D complete; B cancelled; one expected rejection");
        return snapshot();
    }

    public boolean passed() {
        return finished && !disposed && pendingId == null
                && sawRealPause && sawRealStop && sawRealRestart && sawReturnResume
                && accepted == 4 && cancelled == 1 && rejected == 1 && completed == 3;
    }

    public void dispose() {
        if (disposed) return;
        if (pendingId != null) {
            log("GUEST disposal cancels unfinished " + pendingId);
            cancelled++;
            pendingId = null;
            pendingPayload = null;
        }
        disposed = true;
        log("GUEST session disposed=true");
    }

    public String snapshot() {
        return "accepted=" + accepted + "；completed=" + completed
                + "；cancelled=" + cancelled + "；expectedRejected=" + rejected
                + "；pending=" + (pendingId == null ? "none" : pendingId)
                + "；scripted=" + scripted + "；finished=" + finished
                + "；disposed=" + disposed + "\n"
                + "realHostPause=" + sawRealPause + "；realHostStop=" + sawRealStop
                + "；realHostRestart=" + sawRealRestart
                + "；realHostReturnResume=" + sawReturnResume
                + "；forwardedLifecycleEvents=" + lifecycleEvents + "\n"
                + trace;
    }

    private void begin(String id, String payload) {
        if (pendingId != null) throw new IllegalStateException("another guest request is pending");
        if (id == null || !id.matches("[A-Z]{1,2}")) throw new IllegalArgumentException("id");
        if (payload == null || !payload.matches("[A-Z0-9_]{1,40}")) {
            throw new IllegalArgumentException("payload");
        }
        pendingId = id;
        pendingPayload = payload;
        accepted++;
        log("GUEST begin " + id);
    }

    private String complete(String id) {
        if (pendingId == null || !pendingId.equals(id)) {
            throw new IllegalArgumentException("no matching pending guest request");
        }
        String response = GuestV2Route.handle(GuestV2Route.ACTION, pendingPayload);
        pendingId = null;
        pendingPayload = null;
        completed++;
        log("GUEST completed " + id);
        return response;
    }

    private void cancel(String id) {
        if (pendingId == null || !pendingId.equals(id)) {
            throw new IllegalArgumentException("cannot cancel nonmatching request");
        }
        pendingId = null;
        pendingPayload = null;
        cancelled++;
        log("GUEST cancelled " + id);
    }

    private void assertCounters(int a, int x, int reject, int done) {
        if (accepted != a || cancelled != x || rejected != reject || completed != done) {
            throw new IllegalStateException("session counters changed unexpectedly");
        }
    }

    private static void expect(String expected, String actual) {
        if (!expected.equals(actual)) {
            throw new IllegalStateException("guest DEX route response mismatch");
        }
    }

    private void log(String line) {
        if (trace.length() < 3200) trace.append(line).append('\n');
    }
}
