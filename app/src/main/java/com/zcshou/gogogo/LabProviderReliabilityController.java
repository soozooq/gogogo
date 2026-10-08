package com.zcshou.gogogo;

import android.app.AppOpsManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.location.LocationManager;
import android.os.Build;
import android.os.Process;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Lab 20: lifecycle guard for Android test providers.
 *
 * It never hides mock state. Its only job is to make provider registration/teardown
 * deterministic and to surface leftovers that cannot currently be reclaimed.
 */
public final class LabProviderReliabilityController {
    public enum State {
        CLEAN,
        ACTIVE,
        DEGRADED,
        ORPHANED
    }

    private static final String PREFS = "lab20_provider_reliability";

    private final Context context;
    private final LocationManager locationManager;
    private final SharedPreferences prefs;
    private final Map<String, ProviderState> states = new LinkedHashMap<>();

    public LabProviderReliabilityController(
            Context context,
            LocationManager locationManager) {
        this.context = context.getApplicationContext();
        this.locationManager = locationManager;
        this.prefs = this.context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        states.put(LocationManager.GPS_PROVIDER, new ProviderState());
        states.put(LocationManager.NETWORK_PROVIDER, new ProviderState());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            states.put(LocationManager.FUSED_PROVIDER, new ProviderState());
        }
    }

    /**
     * Cold-start sweep. Test providers live in system_server, so a fresh process should
     * not assume there is nothing left from an earlier crash.
     */
    public synchronized void sweepBeforeRegistration() {
        for (String provider : states.keySet()) {
            cleanupProvider(provider);
        }
    }

    public synchronized void retryDeferredCleanup() {
        if (!isMockOpAllowed()) return;
        for (Map.Entry<String, ProviderState> entry : states.entrySet()) {
            if (entry.getValue().state == State.ORPHANED) {
                cleanupProvider(entry.getKey());
            }
        }
    }

    public synchronized void markActive(String provider) {
        ProviderState state = stateFor(provider);
        state.state = State.ACTIVE;
        state.lastError = "";
        setOwned(provider, true);
    }

    public synchronized void markClean(String provider) {
        ProviderState state = stateFor(provider);
        state.state = State.CLEAN;
        state.lastError = "";
        setOwned(provider, false);
    }

    public synchronized void markRegistrationFailure(String provider, Throwable error) {
        ProviderState state = stateFor(provider);
        if ((error instanceof SecurityException || !isMockOpAllowed())
                && wasOwned(provider)) {
            state.state = State.ORPHANED;
        } else {
            state.state = State.DEGRADED;
        }
        state.lastError = shortError(error);
    }

    public synchronized void markPublishFailure(String provider, Throwable error) {
        ProviderState state = stateFor(provider);
        state.publishFailures++;
        if (error instanceof SecurityException && wasOwned(provider)) {
            state.state = State.ORPHANED;
        } else if (state.state != State.ORPHANED) {
            state.state = State.DEGRADED;
        }
        state.lastError = shortError(error);
    }

    public synchronized void markCleanupFailure(String provider, Throwable error) {
        ProviderState state = stateFor(provider);
        state.cleanupFailures++;
        if ((error instanceof SecurityException || !isMockOpAllowed())
                && wasOwned(provider)) {
            state.state = State.ORPHANED;
        } else {
            state.state = State.DEGRADED;
        }
        state.lastError = shortError(error);
    }

    public synchronized int orphanedCount() {
        int count = 0;
        for (ProviderState state : states.values()) {
            if (state.state == State.ORPHANED) count++;
        }
        return count;
    }

    public synchronized String summary() {
        StringBuilder out = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, ProviderState> entry : states.entrySet()) {
            if (!first) out.append(" · ");
            first = false;
            ProviderState s = entry.getValue();
            out.append(entry.getKey().toUpperCase(Locale.US))
                    .append("=")
                    .append(s.state);
            if (s.publishFailures > 0 || s.cleanupFailures > 0) {
                out.append("(")
                        .append(s.publishFailures)
                        .append("/")
                        .append(s.cleanupFailures)
                        .append(")");
            }
        }
        out.append(" · mockOp=")
                .append(isMockOpAllowed() ? "ALLOWED" : "NOT_ALLOWED");
        return out.toString();
    }

    public synchronized String state(String provider) {
        return stateFor(provider).state.name();
    }

    private void cleanupProvider(String provider) {
        ProviderState state = stateFor(provider);
        try {
            try {
                locationManager.setTestProviderEnabled(provider, false);
            } catch (IllegalArgumentException ignored) {
                // Not currently a test provider. removeTestProvider below is still attempted.
            }

            try {
                locationManager.removeTestProvider(provider);
            } catch (IllegalArgumentException ignored) {
                // Expected when no test provider exists. This is effectively clean.
            }

            state.state = State.CLEAN;
            state.lastError = "";
            setOwned(provider, false);
        } catch (SecurityException e) {
            state.cleanupFailures++;
            state.state = wasOwned(provider) ? State.ORPHANED : State.DEGRADED;
            state.lastError = shortError(e);
        } catch (Throwable t) {
            state.cleanupFailures++;
            state.state = State.DEGRADED;
            state.lastError = shortError(t);
        }
    }

    private boolean wasOwned(String provider) {
        return prefs.getBoolean("owned_" + provider, false);
    }

    private void setOwned(String provider, boolean owned) {
        prefs.edit().putBoolean("owned_" + provider, owned).apply();
    }

    private boolean isMockOpAllowed() {
        try {
            AppOpsManager appOps =
                    (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
            if (appOps == null) return false;
            int mode = appOps.checkOpNoThrow(
                    AppOpsManager.OPSTR_MOCK_LOCATION,
                    Process.myUid(),
                    context.getPackageName());
            return mode == AppOpsManager.MODE_ALLOWED;
        } catch (Throwable t) {
            return false;
        }
    }

    private ProviderState stateFor(String provider) {
        ProviderState existing = states.get(provider);
        if (existing != null) return existing;
        ProviderState created = new ProviderState();
        states.put(provider, created);
        return created;
    }

    private static String shortError(Throwable t) {
        if (t == null) return "";
        String message = t.getMessage();
        if (message == null || message.trim().isEmpty()) {
            return t.getClass().getSimpleName();
        }
        return t.getClass().getSimpleName() + ": " + message;
    }

    private static final class ProviderState {
        State state = State.CLEAN;
        long publishFailures = 0L;
        long cleanupFailures = 0L;
        String lastError = "";
    }
}
