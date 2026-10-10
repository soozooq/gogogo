package com.zcshou.gogogo;

import android.app.AppOpsManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.location.LocationManager;
import android.os.Build;
import android.os.Process;

import java.text.SimpleDateFormat;
import java.util.Date;

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
    private static final String AUDIT_PREFS = "lab25_provider_cleanup_evidence";


    private final Context context;
    private final LocationManager locationManager;
    private final SharedPreferences prefs;
    private final SharedPreferences audit;
    private final Map<String, ProviderState> states = new LinkedHashMap<>();
    private AppOpsManager watchingAppOps;
    private boolean watchingAppOpsChanges;
    private final AppOpsManager.OnOpChangedListener mockOpListener;

    public LabProviderReliabilityController(
            Context context,
            LocationManager locationManager) {
        this.context = context.getApplicationContext();
        this.mockOpListener = (operation, packageName) -> {
            if (!AppOpsManager.OPSTR_MOCK_LOCATION.equals(operation)
                    || (packageName != null
                    && !this.context.getPackageName().equals(packageName))) {
                return;
            }
            onMockAppOpChanged();
        };
        this.locationManager = locationManager;
        this.prefs = this.context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        this.audit = this.context.getSharedPreferences(AUDIT_PREFS, Context.MODE_PRIVATE);
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
            cleanupProvider(provider, "COLD_START");
        }
    }

    public synchronized void retryDeferredCleanup() {
        if (!isMockOpAllowed()) return;
        // Never tear down a previously orphaned provider while any publisher
        // in this service is still active, including failure-retry paths.
        boolean anyActive = false;
        for (ProviderState state : states.values()) {
            anyActive |= state.state == State.ACTIVE;
        }
        if (anyActive) return;
        for (Map.Entry<String, ProviderState> entry : states.entrySet()) {
            if (entry.getValue().state == State.ORPHANED) {
                cleanupProvider(entry.getKey(), "DEFERRED_RETRY");
            }
        }
    }

    /** Watches only this app's mock-location AppOp; does not modify permissions. */
    public synchronized void startMonitoring() {
        if (watchingAppOpsChanges) return;
        try {
            AppOpsManager manager = (AppOpsManager)
                    context.getSystemService(Context.APP_OPS_SERVICE);
            if (manager == null) {
                audit.edit().putString("watcher", "UNAVAILABLE").apply();
                return;
            }
            manager.startWatchingMode(AppOpsManager.OPSTR_MOCK_LOCATION,
                    context.getPackageName(), mockOpListener);
            watchingAppOps = manager;
            watchingAppOpsChanges = true;
            audit.edit().putString("watcher", "REGISTERED").apply();
        } catch (RuntimeException e) {
            audit.edit().putString("watcher", "FAILED_"
                    + e.getClass().getSimpleName()).apply();
        }
    }

    public synchronized void stopMonitoring() {
        if (!watchingAppOpsChanges) return;
        try {
            watchingAppOps.stopWatchingMode(mockOpListener);
        } catch (RuntimeException ignored) {
        } finally {
            watchingAppOpsChanges = false;
            watchingAppOps = null;
            audit.edit().putString("watcher", "STOPPED").apply();
        }
    }

    private synchronized void onMockAppOpChanged() {
        boolean allowed = isMockOpAllowed();
        audit.edit().putLong("appops_changed_at", System.currentTimeMillis())
                .putString("appops_last", allowed ? "ALLOWED" : "NOT_ALLOWED")
                .commit();
        boolean anyActive = false;
        boolean anyOrphaned = false;
        for (ProviderState state : states.values()) {
            anyActive |= state.state == State.ACTIVE;
            anyOrphaned |= state.state == State.ORPHANED;
        }
        // Never race an actively publishing service in an AppOps callback.
        if (LabProviderEvidencePolicy.shouldRetryAfterAppOps(
                allowed, anyActive, anyOrphaned)) {
            retryDeferredCleanup();
        }
    }

    public synchronized void markActive(String provider) {
        ProviderState state = stateFor(provider);
        state.state = State.ACTIVE;
        state.lastError = "";
        setOwned(provider, true);
    }

    public synchronized void cleanupDuringService(String provider) {
        cleanupProvider(provider, "SERVICE_CLEANUP");
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

    private void cleanupProvider(String provider, String trigger) {
        ProviderState state = stateFor(provider);
        boolean ownedBefore = wasOwned(provider);
        // Preserve cold-start evidence separately: ServiceGo immediately
        // follows its sweep with an additional cleanup before registration.
        String key = ("COLD_START".equals(trigger) ? "startup_" : "provider_")
                + provider + "_";
        // Carry a previous incomplete attempt forward before overwriting it.
        // PENDING from an earlier process is evidence of a mid-cleanup interruption,
        // but does not by itself prove that the provider is still registered.
        String priorOutcome = audit.getString(key + "outcome", "NONE");
        long priorStarted = audit.getLong(key + "started_at", 0L);
        // Persist an IN_PROGRESS marker before touching system_server.
        audit.edit().putString(key + "previous_outcome", priorOutcome)
                .putLong(key + "previous_started_at", priorStarted)
                .remove(key + "finished_at")
                .remove(key + "owned_after")
                .remove(key + "allowed_after")
                .remove(key + "disable_warning")
                .putString(key + "trigger", trigger)
                .putBoolean(key + "owned_before", ownedBefore)
                .putBoolean(key + "allowed_before", isMockOpAllowed())
                .putLong(key + "started_at", System.currentTimeMillis())
                .putString(key + "outcome",
                        LabProviderEvidencePolicy.CleanupOutcome.PENDING.name())
                .commit();

        LabProviderEvidencePolicy.CleanupOutcome outcome =
                LabProviderEvidencePolicy.CleanupOutcome.PENDING;
        String disableWarning = "";
        try {
            // Disabling is best-effort; even if it fails, still attempt removal.
            try {
                locationManager.setTestProviderEnabled(provider, false);
            } catch (IllegalArgumentException ignored) {
                // Nothing registered under this test-provider name.
            } catch (RuntimeException e) {
                disableWarning = e.getClass().getSimpleName();
            }
            try {
                locationManager.removeTestProvider(provider);
                outcome = LabProviderEvidencePolicy.CleanupOutcome.REMOVE_RETURNED;
            } catch (IllegalArgumentException absent) {
                outcome = LabProviderEvidencePolicy.CleanupOutcome.ALREADY_ABSENT;
            }

            if (LabProviderEvidencePolicy.mayReportClean(outcome)) {
                state.state = State.CLEAN;
                state.lastError = "";
                setOwned(provider, false);
            }
        } catch (SecurityException e) {
            outcome = LabProviderEvidencePolicy.CleanupOutcome.SECURITY_DENIED;
            state.cleanupFailures++;
            state.state = ownedBefore ? State.ORPHANED : State.DEGRADED;
            state.lastError = shortError(e);
        } catch (Throwable t) {
            outcome = LabProviderEvidencePolicy.CleanupOutcome.OTHER_FAILURE;
            state.cleanupFailures++;
            state.state = State.DEGRADED;
            state.lastError = shortError(t);
        } finally {
            // This is API-level evidence only; it cannot certify that Tencent,
            // GMS or any third-party consumer discarded its cached location.
            audit.edit().putString(key + "outcome", outcome.name())
                    .putLong(key + "finished_at", System.currentTimeMillis())
                    .putBoolean(key + "owned_after", wasOwned(provider))
                    .putBoolean(key + "allowed_after", isMockOpAllowed())
                    .putString(key + "disable_warning", disableWarning)
                    .commit();
        }
    }

    /**
     * GMS setMockMode returns an asynchronous Task. Persist request/result
     * separately so an unobserved or failed reset is never reported as clean.
     * No coordinates or remote SDK payloads are stored.
     */
    /**
     * Compatibility entry point for events without an asynchronous Task.
     * Advances the sequence so an older Task cannot overwrite this evidence.
     */
    public static void recordGmsEvent(Context context, String event) {
        beginGmsRequest(context, event);
    }

    /** Persist request creation BEFORE issuing an asynchronous GMS Task. */
    public static synchronized long beginGmsRequest(Context context, String event) {
        SharedPreferences p = context.getApplicationContext()
                .getSharedPreferences(AUDIT_PREFS, Context.MODE_PRIVATE);
        long requestId = LabGmsRequestOrder.next(p.getLong("gms_request_seq", 0L));
        p.edit()
                .putLong("gms_request_seq", requestId)
                .putString("gms_last_event", event)
                .putLong("gms_last_at", System.currentTimeMillis())
                .commit();
        return requestId;
    }

    /**
     * Accept only a result for the most recently initiated request. A late
     * callback may be diagnostically interesting, but must not replace the
     * status of a newer request (including one from another ServiceGo instance).
     */
    public static synchronized boolean finishGmsRequest(
            Context context, long requestId, String event) {
        SharedPreferences p = context.getApplicationContext()
                .getSharedPreferences(AUDIT_PREFS, Context.MODE_PRIVATE);
        if (!LabGmsRequestOrder.isCurrent(requestId,
                p.getLong("gms_request_seq", 0L))) {
            p.edit().putLong("gms_late_callback_count",
                            p.getLong("gms_late_callback_count", 0L) + 1L)
                    .putLong("gms_late_callback_at", System.currentTimeMillis())
                    .commit();
            return false;
        }
        p.edit().putString("gms_last_event", event)
                .putLong("gms_last_at", System.currentTimeMillis())
                .commit();
        return true;
    }

    /** Visible in Lab Diagnostics even after ServiceGo was force-stopped. */
    public static String savedAuditSummary(Context context) {
        SharedPreferences p = context.getApplicationContext().getSharedPreferences(
                AUDIT_PREFS, Context.MODE_PRIVATE);
        StringBuilder out = new StringBuilder();
        out.append("监听状态（上次记录）: ").append(p.getString("watcher", "NOT_STARTED"))
                .append("；进程退出后不代表仍在监听").append('\n');
        out.append("GMS Mock 上次事件: ")
                .append(p.getString("gms_last_event", "NOT_RECORDED"))
                .append(" @ ").append(formatTime(p.getLong("gms_last_at", 0L)))
                .append('\n');
        out.append("GMS 已忽略的过期异步回调次数: ")
                .append(p.getLong("gms_late_callback_count", 0L))
                .append("（只影响审计记录，不代表 GMS 已修复）").append('\n');
        long opAt = p.getLong("appops_changed_at", 0L);
        if (opAt > 0L) {
            out.append("最近 AppOps 事件: ").append(p.getString("appops_last", "UNKNOWN"))
                    .append(" @ ").append(formatTime(opAt)).append('\n');
        }
        for (String provider : new String[]{LocationManager.GPS_PROVIDER,
                LocationManager.NETWORK_PROVIDER, LocationManager.FUSED_PROVIDER}) {
            for (String prefix : new String[]{"startup_", "provider_"}) {
            String key = prefix + provider + "_";
            if (!p.contains(key + "outcome")) continue;
            out.append(provider.toUpperCase(Locale.US))
                    .append(" [").append(prefix.equals("startup_") ? "启动扫尾" : "服务清理").append("]")
                    .append(": beforeOwned=")
                    .append(p.getBoolean(key + "owned_before", false))
                    .append(" / beforeAllowed=")
                    .append(p.getBoolean(key + "allowed_before", false))
                    .append(" / afterOwned=")
                    .append(p.contains(key + "owned_after")
                            ? String.valueOf(p.getBoolean(key + "owned_after", false))
                            : "PENDING")
                    .append(" / afterAllowed=")
                    .append(p.contains(key + "allowed_after")
                            ? String.valueOf(p.getBoolean(key + "allowed_after", false))
                            : "PENDING")
                    .append(" / outcome=")
                    .append(p.getString(key + "outcome", "UNKNOWN"))
                    .append(" / trigger=")
                    .append(p.getString(key + "trigger", "UNKNOWN"))
                    .append(" / previous=")
                    .append(p.getString(key + "previous_outcome", "NONE"))
                    .append(" / previousAt=")
                    .append(formatTime(p.getLong(key + "previous_started_at", 0L)))
                    .append(" / started=")
                    .append(formatTime(p.getLong(key + "started_at", 0L)))
                    .append(" / finished=")
                    .append(formatTime(p.getLong(key + "finished_at", 0L)))
                    .append('\n');
            }
        }
        out.append("注意: beforeOwned 为本应用上次记录，不是系统残留证据；")
                .append("REMOVE_RETURNED 仅表明 API 返回，不证明其他应用已刷新位置。");
        return out.toString();
    }

    private static String formatTime(long timestamp) {
        if (timestamp <= 0L) return "UNKNOWN";
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                .format(new Date(timestamp));
    }

    private boolean wasOwned(String provider) {
        return prefs.getBoolean("owned_" + provider, false);
    }

    private void setOwned(String provider, boolean owned) {
        // Persist ownership before process termination so cold-start evidence is useful.
        prefs.edit().putBoolean("owned_" + provider, owned).commit();
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
