package com.zcshou.gogogo;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.SystemClock;
import android.util.AtomicFile;
import android.widget.Toast;

import com.zcshou.service.ServiceGo;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * One-tap, cache-only diagnostic bundle for a user to paste into a support chat.
 * Never calls LocationManager/Fused getCurrentLocation, requests listeners,
 * starts/stops the mock service, performs network I/O, or acquires permissions.
 */
public final class LabSupportLog {
    public static final String ACTIVE = "consumer_active";
    public static final String PASSIVE = "consumer_passive";
    public static final String TENCENT = "tencent";
    private static final int MAX_ENTRIES = 14;
    private static final int MAX_FILE_BYTES = 32_000;

    private LabSupportLog() {}

    private static File file(Context context, String section) {
        if (!ACTIVE.equals(section) && !PASSIVE.equals(section)
                && !TENCENT.equals(section)) {
            throw new IllegalArgumentException("Unknown support log section");
        }
        return new File(context.getApplicationContext().getFilesDir(),
                "support_" + section + ".log");
    }

    private static String read(Context context, String section) {
        AtomicFile atomic = new AtomicFile(file(context, section));
        try (FileInputStream input = atomic.openRead()) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] chunk = new byte[1024];
            int n;
            while ((n = input.read(chunk)) >= 0) {
                bytes.write(chunk, 0, n);
                if (bytes.size() > MAX_FILE_BYTES) return "LOG_TOO_LARGE_REDACTED\n";
            }
            return new String(bytes.toByteArray(), StandardCharsets.UTF_8);
        } catch (java.io.FileNotFoundException e) {
            return "NO_RECORD\n";
        } catch (Exception e) {
            return "READ_ERROR_" + e.getClass().getSimpleName() + "\n";
        }
    }

    /** A bounded timestamped observation journal. Each producer writes only its own section. */
    public static synchronized void append(Context context, String section, String event) {
        if (event == null || event.isEmpty()) return;
        try {
            String previous = read(context, section);
            if ("NO_RECORD\n".equals(previous) || previous.startsWith("READ_ERROR_")
                    || previous.startsWith("LOG_TOO_LARGE")) previous = "";
            String now = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ",
                    Locale.US).format(new Date());
            String sanitized = event.replace('\n', ' ').replace('\r', ' ');
            String entry = now + " elapsed_ms=" + SystemClock.elapsedRealtime()
                    + " " + sanitized.substring(0, Math.min(1800, sanitized.length()));
            String next = LabSupportLogText.appendBounded(previous, entry, MAX_ENTRIES);
            AtomicFile atomic = new AtomicFile(file(context, section));
            FileOutputStream output = null;
            try {
                output = atomic.startWrite();
                output.write(next.getBytes(StandardCharsets.UTF_8));
                atomic.finishWrite(output);
            } catch (Exception e) {
                if (output != null) atomic.failWrite(output);
            }
        } catch (Exception ignored) {
            // Logging failure must never affect mock services or consumers.
        }
    }

    public static synchronized void clear(Context context, String section) {
        // No sensitive trace is automatically re-created by copying.
        try {
            File target = file(context, section);
            new AtomicFile(target).delete();
        } catch (Exception ignored) {
        }
    }

    private static File requestedTargetFile(Context context) {
        return new File(context.getApplicationContext().getFilesDir(),
                "support_last_requested_target.txt");
    }

    /** Synthetic request target only; never read/store a user's true Location. */
    public static synchronized void rememberRequestedTarget(
            Context context, double longitude, double latitude) {
        if (!Double.isFinite(longitude) || !Double.isFinite(latitude)
                || Math.abs(longitude) > 180 || Math.abs(latitude) > 90) return;
        AtomicFile atomic = new AtomicFile(requestedTargetFile(context));
        FileOutputStream output = null;
        try {
            output = atomic.startWrite();
            String record = longitude + "," + latitude + "," + System.currentTimeMillis();
            output.write(record.getBytes(StandardCharsets.UTF_8));
            atomic.finishWrite(output);
        } catch (Exception e) {
            if (output != null) atomic.failWrite(output);
        }
    }

    private static String requestedTarget(Context context) {
        AtomicFile atomic = new AtomicFile(requestedTargetFile(context));
        try (FileInputStream input = atomic.openRead()) {
            byte[] buffer = new byte[120];
            int size = input.read(buffer);
            if (size <= 0 || size >= buffer.length) return "NO_TARGET";
            return new String(buffer, 0, size, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "NO_TARGET";
        }
    }

    /** Used only on fixes already delivered by existing consumer callbacks. */
    public static String distanceToRequestedTarget(Context context,
                                                   double longitude, double latitude) {
        String[] fields = requestedTarget(context).split(",");
        if (fields.length != 3) return "NO_TARGET";
        try {
            return LabSupportLogText.distanceBucket(
                    Double.parseDouble(fields[0]), Double.parseDouble(fields[1]),
                    longitude, latitude);
        } catch (RuntimeException e) {
            return "UNKNOWN";
        }
    }

    private static void section(StringBuilder out, String label, String detail) {
        out.append("\n--- ").append(label).append(" ---\n").append(detail).append('\n');
    }

    private static String captureSafe(Context context, int type) {
        try {
            if (type == 0) {
                return Lab33DiagnosticBundle.render(LabQuickCheckReader.read(context),
                        LabRecoveryEvidenceReader.read(context));
            }
            if (type == 1) {
                return LabServiceLifecycleJournal.report(context, ServiceGo.sRunning);
            }
        } catch (Exception error) {
            return "SECTION_ERROR_" + error.getClass().getSimpleName();
        }
        return "NOT_AVAILABLE";
    }

    public static String generate(Context context, String expectedLongitude,
                                  String expectedLatitude) {
        StringBuilder out = new StringBuilder(12_000);
        out.append("GOGOGO_SUPPORT_LOG_V1\n");
        out.append("captured_at=")
                .append(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ",
                        Locale.US).format(new Date())).append('\n');
        out.append("elapsed_ms=").append(SystemClock.elapsedRealtime()).append('\n');
        out.append("version=").append(BuildConfig.VERSION_NAME)
                .append(" / code=").append(BuildConfig.VERSION_CODE).append('\n');
        out.append("source=local_read_only_report; no_new_location_request=true\n");
        String processName = android.os.Build.VERSION.SDK_INT >= 28
                ? android.app.Application.getProcessName() : "UNKNOWN_PRE_28";
        out.append("report_process=")
                .append(LabSupportLogText.safeToken(processName)).append('\n');
        out.append("service_flag_scope=THIS_PROCESS_ONLY; consumer_process_false_not_global_stop\n");
        out.append("privacy=real_consumer_coordinates_hidden; mock_coordinates_included; ")
                .append("no_ssid_ip_keys_phone_identifiers\n");
        String lastRequested = requestedTarget(context);
        String[] saved = lastRequested.split(",");
        boolean pageTarget = expectedLongitude != null && !expectedLongitude.trim().isEmpty()
                && expectedLatitude != null && !expectedLatitude.trim().isEmpty();
        String longitude = expectedLongitude == null || expectedLongitude.trim().isEmpty()
                ? (saved.length == 3 ? saved[0] : null) : expectedLongitude;
        String latitude = expectedLatitude == null || expectedLatitude.trim().isEmpty()
                ? (saved.length == 3 ? saved[1] : null) : expectedLatitude;
        out.append("target_source=").append(pageTarget ? "PAGE_INPUT"
                : saved.length == 3 ? "LAST_REQUESTED_NOT_CONFIRMED" : "UNAVAILABLE").append('\n');
        out.append("target_lon=").append(longitude == null ? "UNKNOWN"
                : LabSupportLogText.finiteTarget(longitude, true)).append('\n');
        out.append("target_lat=").append(latitude == null ? "UNKNOWN"
                : LabSupportLogText.finiteTarget(latitude, false)).append('\n');
        out.append("last_requested_mock_target=").append(lastRequested)
                .append(" (WGS84 lng,lat,wall_ms; requested_not_confirmed)\n");
        section(out, "QUICK_AND_RECOVERY", captureSafe(context, 0));
        section(out, "SERVICE_LIFECYCLE", captureSafe(context, 1));
        Boolean mainWeChatStopped = LabExperimentPreflight.observeCurrentUserWeChat(context);
        out.append("wechat_main_flag_stopped=")
                .append(mainWeChatStopped == null ? "UNKNOWN" : mainWeChatStopped ? "YES" : "NO")
                .append(" (not_process_proof)\n");
        out.append("wechat_clone_stopped=UNKNOWN_CROSS_PROFILE_NOT_READABLE\n");
        out.append("history_scope=RECENT_ONLY; stored_history_remains_bounded\n");
        section(out, "CONSUMER_ACTIVE_RECENT", LabSupportLogText.lastLines(read(context, ACTIVE), 4));
        section(out, "CONSUMER_PASSIVE_RECENT", LabSupportLogText.lastLines(read(context, PASSIVE), 9));
        section(out, "TENCENT_SDK_RECENT", LabSupportLogText.lastLines(read(context, TENCENT), 4));
        out.append("END_GOGOGO_SUPPORT_LOG_V1\n");
        return out.toString();
    }

    /**
     * Shared user-requested export for location diagnostics and saved container
     * records. Each section is read independently; copying never runs an
     * experiment or changes the original session's outcome.
     */
    public static String generateAll(Context context, String expectedLongitude,
                                     String expectedLatitude) {
        StringBuilder out = new StringBuilder(20_000);
        out.append("GOGOGO_COMPLETE_SUPPORT_LOG_V1\n");
        out.append("scope=read_only_current_diagnostics_and_saved_records; ")
                .append("no_experiments_started=true; historical_results_not_rerun=true\n");
        try {
            section(out, "LOCATION_DIAGNOSTICS",
                    generate(context, expectedLongitude, expectedLatitude));
        } catch (Exception error) {
            section(out, "LOCATION_DIAGNOSTICS",
                    "SECTION_ERROR_" + error.getClass().getSimpleName());
        }
        try {
            section(out, "SAVED_APPLICATION_RECORDS",
                    VirtualContainerV0Activity.collectSavedContainerReports(context));
        } catch (Exception error) {
            section(out, "SAVED_APPLICATION_RECORDS",
                    "SECTION_ERROR_" + error.getClass().getSimpleName());
        }
        out.append("END_GOGOGO_COMPLETE_SUPPORT_LOG_V1\n");
        return out.toString();
    }

    /** User action only. Do not upload automatically. */
    public static void copy(Context context, String expectedLongitude,
                            String expectedLatitude) {
        copyWithResult(context, expectedLongitude, expectedLatitude);
    }

    /** Also used by the user-started countdown, never by background polling. */
    public static boolean copyWithResult(Context context, String expectedLongitude,
                                         String expectedLatitude) {
        try {
            ClipboardManager manager = (ClipboardManager) context.getSystemService(
                    Context.CLIPBOARD_SERVICE);
            if (manager == null) throw new IllegalStateException("NO_CLIPBOARD");
            manager.setPrimaryClip(ClipData.newPlainText("GoGoGo 诊断日志",
                    generateAll(context, expectedLongitude, expectedLatitude)));
            Toast.makeText(context, "日志已复制，直接粘贴发给 GPT 即可", Toast.LENGTH_LONG).show();
            return true;
        } catch (Exception e) {
            Toast.makeText(context, "复制失败：" + e.getClass().getSimpleName(),
                    Toast.LENGTH_LONG).show();
            return false;
        }
    }
}
