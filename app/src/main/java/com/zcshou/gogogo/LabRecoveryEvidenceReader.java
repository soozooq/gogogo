package com.zcshou.gogogo;

import android.content.Context;
import android.content.SharedPreferences;
import android.location.LocationManager;

import com.zcshou.service.ServiceGo;

import java.util.ArrayList;
import java.util.List;

/** Read existing audit preferences only; never accesses or edits Android providers. */
public final class LabRecoveryEvidenceReader {
    private static final String PREFS = "lab25_provider_cleanup_evidence";
    private LabRecoveryEvidenceReader() {}

    public static LabRecoveryTriage.Snapshot read(Context context) {
        SharedPreferences prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        boolean running = ServiceGo.sRunning;
        long lastStartWall = LabServiceLifecycleJournal.lastServiceStartWall(context);
        long nowWall = System.currentTimeMillis();
        LabServiceLifecycleJournal.Verdict verdict =
                LabServiceLifecycleJournal.currentVerdict(context, running);
        LabRecoveryTriage.Lifecycle lifecycle;
        switch (verdict) {
            case NO_RECORD: lifecycle = LabRecoveryTriage.Lifecycle.NO_RECORD; break;
            case NORMAL_STOP: lifecycle = LabRecoveryTriage.Lifecycle.NORMAL_STOP; break;
            case RUNNING_IN_THIS_PROCESS:
                lifecycle = LabRecoveryTriage.Lifecycle.RUNNING; break;
            case INTERRUPTED_OR_STALE:
                lifecycle = LabRecoveryTriage.Lifecycle.INTERRUPTED_CANDIDATE; break;
            default:
                lifecycle = LabRecoveryTriage.Lifecycle.REBOOT_UNCERTAIN;
                break;
        }

        List<LabRecoveryTriage.ProviderRow> rows = new ArrayList<>();
        String[] names = {LocationManager.GPS_PROVIDER,
                LocationManager.NETWORK_PROVIDER, LocationManager.FUSED_PROVIDER};
        for (String name : names) {
            for (String prefix : new String[]{"startup_", "provider_"}) {
                String key = prefix + name + "_";
                rows.add(new LabRecoveryTriage.ProviderRow(
                        name.toUpperCase(java.util.Locale.US),
                        "startup_".equals(prefix) ? "启动扫尾" : "服务清理",
                        prefs.getString(key + "outcome", null),
                        prefs.getString(key + "previous_outcome", null),
                        LabEvidenceTimeWindow.classify(lastStartWall,
                                prefs.getLong(key + "started_at", 0L), nowWall)));
            }
        }

        return new LabRecoveryTriage.Snapshot(
                lifecycle, running, rows,
                prefs.getString("gms_last_event", null),
                LabEvidenceTimeWindow.classify(lastStartWall,
                        prefs.getLong("gms_last_at", 0L), nowWall),
                prefs.getLong("gms_late_callback_count", 0L),
                prefs.getLong("gms_suppressed_late_retry_count", 0L));
    }
}
