package com.soozooq.tianwaitian.fixture;

/**
 * A bounded Java-only guest session contract, built in the separate fixture APK.
 * This class does not represent an Android Activity or a persistence/sandbox API.
 */
public final class GuestV23Recreation {
    private String activeToken;
    private boolean pending;
    private boolean cancelled;
    private boolean completed;
    private boolean disposed;
    private boolean staleRejected;
    private int starts;
    private int completions;
    private final StringBuilder timeline = new StringBuilder();

    public GuestV23Recreation() {
        trace("GUEST V2.3 object created");
    }

    public String prepareOld(String token) {
        ensureActive();
        if (starts != 0 || !validToken(token)) throw new IllegalArgumentException("old token");
        activeToken = token;
        pending = true;
        starts++;
        trace("GUEST OLD started pending D");
        return "PENDING_D";
    }

    public String cancelOld(String token) {
        ensureActive();
        if (!pending || !token.equals(activeToken)) {
            throw new IllegalArgumentException("wrong pending token");
        }
        pending = false;
        cancelled = true;
        trace("GUEST OLD pending D cancelled before Activity.recreate()");
        return "CANCELLED_D";
    }

    public boolean acceptLate(String token) {
        ensureActive();
        boolean accepted = pending && token != null && token.equals(activeToken);
        if (!accepted) {
            staleRejected = true;
            trace("GUEST rejected old/stale token");
        }
        return accepted;
    }

    public String startNew(String oldToken, String freshToken) {
        ensureActive();
        if (starts != 0 || !validToken(oldToken) || !validToken(freshToken)
                || oldToken.equals(freshToken)) {
            throw new IllegalArgumentException("new token must differ from old");
        }
        // A brand-new guest instance never contains the old pending request.
        if (acceptLate(oldToken)) {
            throw new IllegalStateException("stale request leaked to new guest instance");
        }
        activeToken = freshToken;
        pending = true;
        starts++;
        trace("GUEST NEW started fresh D; old D rejected");
        return "NEW_PENDING_D";
    }

    public String completeNew(String token) {
        ensureActive();
        if (!pending || !token.equals(activeToken) || completed) {
            throw new IllegalArgumentException("request not current or already completed");
        }
        String result = GuestV2Route.handle(GuestV2Route.ACTION, "RECOVER_V23");
        if (!"GUEST_V2_RESULT:32V_REVOCER".equals(result)) {
            throw new IllegalStateException("guest route unexpectedly changed");
        }
        pending = false;
        completed = true;
        completions++;
        trace("GUEST NEW completed one fresh D");
        return result;
    }

    public boolean oldCleared() {
        return cancelled && !pending && completions == 0;
    }

    public boolean newReady() {
        return completed && !pending && staleRejected
                && starts == 1 && completions == 1 && !disposed;
    }

    public void dispose() {
        if (disposed) return;
        if (pending) {
            trace("GUEST disposed pending request; fail-closed");
            pending = false;
            cancelled = true;
        }
        disposed = true;
        trace("GUEST disposed=true");
    }

    public String snapshot() {
        return "starts=" + starts + "；completions=" + completions
                + "；pending=" + pending + "；cancelled=" + cancelled
                + "；staleRejected=" + staleRejected + "；disposed=" + disposed
                + "\n" + timeline;
    }

    private void ensureActive() {
        if (disposed) throw new IllegalStateException("guest object already disposed");
    }

    private static boolean validToken(String token) {
        return token != null && token.matches("[a-f0-9-]{36}");
    }

    private void trace(String event) {
        if (timeline.length() < 2400) timeline.append(event).append('\n');
    }
}
