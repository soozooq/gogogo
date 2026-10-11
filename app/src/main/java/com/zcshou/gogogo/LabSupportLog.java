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
    private static final int MAX_FILE_BYTES = 14_000;

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
                    + " " + sanitized.substring(0, Math.min(650, sanitized.length()));
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
        out.append("privacy=real_consumer_coordinates_hidden; mock_coordinates_included; ")
                .append("no_ssid_ip_keys_phone_identifiers\n");
        out.append("target_lon=").append(LabSupportLogText.finiteTarget(
                expectedLongitude, true)).append('\n');
        out.append("target_lat=").append(LabSupportLogText.finiteTarget(
                expectedLatitude, false)).append('\n');
        section(out, "QUICK_AND_RECOVERY", captureSafe(context, 0));
        section(out, "SERVICE_LIFECYCLE", captureSafe(context, 1));
        section(out, "CONSUMER_ACTIVE_SAVED", read(context, ACTIVE));
        section(out, "CONSUMER_PASSIVE_SAVED", read(context, PASSIVE));
        section(out, "TENCENT_SDK_SAVED", read(context, TENCENT));
        out.append("END_GOGOGO_SUPPORT_LOG_V1\n");
        return out.toString();
    }

    /** User action only. Do not upload automatically. */
    public static void copy(Context context, String expectedLongitude,
                            String expectedLatitude) {
        try {
            ClipboardManager manager = (ClipboardManager) context.getSystemService(
                    Context.CLIPBOARD_SERVICE);
            if (manager == null) throw new IllegalStateException("NO_CLIPBOARD");
            manager.setPrimaryClip(ClipData.newPlainText("GoGoGo 诊断日志",
                    generate(context, expectedLongitude, expectedLatitude)));
            Toast.makeText(context, "日志已复制，直接粘贴发给 GPT 即可", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(context, "复制失败：" + e.getClass().getSimpleName(),
                    Toast.LENGTH_LONG).show();
        }
    }
}
