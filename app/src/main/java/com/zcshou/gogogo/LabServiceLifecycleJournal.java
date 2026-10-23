package com.zcshou.gogogo;

import android.app.AppOpsManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Process;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.AtomicFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;

/**
 * Read-only evidence for distinguishing a normal mock-service stop from an
 * interrupted process. No locations, account IDs or nearby network identifiers
 * are stored. This does not control providers or Shizuku permissions.
 */
public final class LabServiceLifecycleJournal {
    private static final String PREFERENCES = "lab_service_lifecycle_v1";
    private static final String CROSS_PROCESS_EVENT = "lab_service_last_event_v1.txt";
    private static final String KEY_STATE = "state";
    private static final String KEY_SESSION_TOKEN = "session_token";
    private static final String KEY_PROVIDER_CLEANUP = "provider_cleanup";
    private static final String KEY_PROVIDER_CLEANUP_QUEUED = "provider_cleanup_queued";
    private static final String KEY_PROVIDER_CLEANUP_FINISHED = "provider_cleanup_finished";
    private static final String KEY_PID = "service_pid";
    private static final String KEY_GENERATION = "service_generation";
    private static final String KEY_STARTED_WALL = "started_wall";
    private static final String KEY_STARTED_ELAPSED = "started_elapsed";
    private static final String KEY_STARTED_BOOT_COUNT = "started_boot_count";
    private static final String KEY_STOPPED_WALL = "stopped_wall";
    private static final String KEY_SESSION_COUNT = "session_count";
    private static final String KEY_INTERRUPTION_COUNT = "interruption_count";
    private static final String KEY_LAST_INTERRUPTED_WALL = "last_interrupted_wall";
    private static final String STATE_ACTIVE = "ACTIVE";
    private static final String STATE_STOPPED = "STOPPED";
    private static final long BOOT_EPOCH_TOLERANCE_MS = 5L * 60L * 1000L;

    // New on each process load, even when Android happens to reuse a PID.
    private static final String PROCESS_GENERATION = UUID.randomUUID().toString();

    private LabServiceLifecycleJournal() {
    }

    private static SharedPreferences preferences(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
    }

    /** Call once in ServiceGo.onCreate(), before provider setup. */
    public static synchronized String onServiceCreated(Context context) {
        SharedPreferences prefs = preferences(context);
        long nowElapsed = SystemClock.elapsedRealtime();
        long nowWall = System.currentTimeMillis();
        long nowBootCount = readBootCount(context);
        boolean priorOpen = STATE_ACTIVE.equals(prefs.getString(KEY_STATE, ""));
        boolean priorProcess = PROCESS_GENERATION.equals(
                prefs.getString(KEY_GENERATION, ""));
        long priorStart = prefs.getLong(KEY_STARTED_ELAPSED, -1L);
        boolean sameBoot = isSameBoot(
                prefs.getLong(KEY_STARTED_BOOT_COUNT, -1L), nowBootCount,
                prefs.getLong(KEY_STARTED_WALL, 0L), priorStart, nowWall, nowElapsed);
        String sessionToken = UUID.randomUUID().toString();
        SharedPreferences.Editor editor = prefs.edit();

        // A new process cannot close a previously open session; record only
        // an interruption *candidate*. We never claim providers stayed mocked.
        if (priorOpen && !priorProcess && sameBoot) {
            editor.putLong(KEY_INTERRUPTION_COUNT,
                    prefs.getLong(KEY_INTERRUPTION_COUNT, 0L) + 1L);
            editor.putLong(KEY_LAST_INTERRUPTED_WALL, nowWall);
        }

        editor.putString(KEY_STATE, STATE_ACTIVE)
                .putString(KEY_SESSION_TOKEN, sessionToken)
                .putString(KEY_PROVIDER_CLEANUP, "NOT_REQUESTED")
                .remove(KEY_PROVIDER_CLEANUP_QUEUED)
                .remove(KEY_PROVIDER_CLEANUP_FINISHED)
                .putString(KEY_GENERATION, PROCESS_GENERATION)
                .putInt(KEY_PID, Process.myPid())
                .putLong(KEY_STARTED_WALL, nowWall)
                .putLong(KEY_STARTED_ELAPSED, nowElapsed)
                .putLong(KEY_STARTED_BOOT_COUNT, nowBootCount)
                .putLong(KEY_SESSION_COUNT, prefs.getLong(KEY_SESSION_COUNT, 0L) + 1L)
                .commit(); // A process may be force-stopped without onDestroy().
        writeCrossProcessEvent(context, STATE_ACTIVE, nowWall, nowElapsed, nowBootCount);
        return sessionToken;
    }

    /** Lifecycle callback only; asynchronous framework cleanup is recorded separately. */
    public static synchronized void onServiceDestroyed(Context context, String sessionToken) {
        SharedPreferences prefs = preferences(context);
        if (sessionToken == null || !sessionToken.equals(prefs.getString(KEY_SESSION_TOKEN, ""))
                || !PROCESS_GENERATION.equals(prefs.getString(KEY_GENERATION, ""))) {
            return;
        }
        long stoppedWall = System.currentTimeMillis();
        prefs.edit()
                .putString(KEY_STATE, STATE_STOPPED)
                .putLong(KEY_STOPPED_WALL, stoppedWall)
                .commit();
        writeCrossProcessEvent(context, STATE_STOPPED, stoppedWall,
                SystemClock.elapsedRealtime(), readBootCount(context));
    }

    public static synchronized void onProviderCleanupQueued(Context context, String sessionToken) {
        SharedPreferences prefs = preferences(context);
        if (sessionToken == null || !sessionToken.equals(prefs.getString(KEY_SESSION_TOKEN, ""))) return;
        prefs.edit().putString(KEY_PROVIDER_CLEANUP, "QUEUED_NOT_FINISHED")
                .putLong(KEY_PROVIDER_CLEANUP_QUEUED, System.currentTimeMillis())
                .remove(KEY_PROVIDER_CLEANUP_FINISHED).commit();
    }

    public static synchronized void onProviderCleanupFinished(
            Context context, String sessionToken, boolean unexpectedFailure) {
        SharedPreferences prefs = preferences(context);
        // An old worker must never complete a newer service's cleanup record.
        if (sessionToken == null || !sessionToken.equals(prefs.getString(KEY_SESSION_TOKEN, ""))) return;
        prefs.edit().putString(KEY_PROVIDER_CLEANUP,
                        unexpectedFailure ? "STAGE_FAILURE_OR_NOT_QUEUED" : "ATTEMPTED_NOT_PROOF_CLEARED")
                .putLong(KEY_PROVIDER_CLEANUP_FINISHED, System.currentTimeMillis()).commit();
    }

    /**
     * AtomicFile is read afresh from disk in :consumer, unlike process-cached
     * SharedPreferences. An event is still historical, not a live service check.
     */
    private static void writeCrossProcessEvent(Context context, String state,
                                               long wall, long elapsed, long boot) {
        AtomicFile file = new AtomicFile(new File(
                context.getApplicationContext().getFilesDir(), CROSS_PROCESS_EVENT));
        FileOutputStream output = null;
        try {
            output = file.startWrite();
            String record = state + "," + wall + "," + elapsed + "," + boot;
            output.write(record.getBytes(StandardCharsets.UTF_8));
            file.finishWrite(output);
        } catch (Exception ignored) {
            if (output != null) file.failWrite(output);
            // Diagnostic I/O must not block the mock service lifecycle.
        }
    }

    private static String lastCrossProcessEvent(Context context) {
        AtomicFile file = new AtomicFile(new File(
                context.getApplicationContext().getFilesDir(), CROSS_PROCESS_EVENT));
        try (FileInputStream input = file.openRead()) {
            byte[] bytes = new byte[160];
            int size = input.read(bytes);
            if (size <= 0 || size >= bytes.length) return "NO_VALID_RECORD";
            String[] parts = new String(bytes, 0, size, StandardCharsets.UTF_8).split(",");
            if (parts.length != 4
                    || (!STATE_ACTIVE.equals(parts[0]) && !STATE_STOPPED.equals(parts[0]))) {
                return "NO_VALID_RECORD";
            }
            long wall = Long.parseLong(parts[1]);
            long elapsed = Long.parseLong(parts[2]);
            long boot = Long.parseLong(parts[3]);
            long nowElapsed = SystemClock.elapsedRealtime();
            boolean sameBoot = isSameBoot(boot, readBootCount(context), wall, elapsed,
                    System.currentTimeMillis(), nowElapsed);
            String status = STATE_ACTIVE.equals(parts[0])
                    ? "LAST_START_SEEN_NOT_PROOF_RUNNING"
                    : "LAST_STOP_CALLBACK_SEEN_NOT_PROOF_PROVIDER_CLEARED";
            return status + "; wall=" + formatTime(wall)
                    + "; elapsed_age_ms=" + (sameBoot ? nowElapsed - elapsed : -1L)
                    + "; boot=" + (sameBoot ? "SAME" : "UNKNOWN_OR_CHANGED");
        } catch (Exception e) {
            return "NO_RECORD_OR_UNAVAILABLE";
        }
    }

    /**
     * The verdict is deliberately limited to lifecycle evidence. Neither an
     * unclean exit nor a normal stop proves an external consumer's coordinates.
     */
    public enum Verdict {
        NO_RECORD,
        NORMAL_STOP,
        RUNNING_IN_THIS_PROCESS,
        INTERRUPTED_OR_STALE,
        DEVICE_REBOOT_UNCERTAIN
    }

    public static Verdict classify(boolean hasRecord, boolean stopped,
                                   boolean sameProcess, boolean running,
                                   boolean sameBoot) {
        if (!hasRecord) return Verdict.NO_RECORD;
        if (stopped) return Verdict.NORMAL_STOP;
        if (!sameBoot) return Verdict.DEVICE_REBOOT_UNCERTAIN;
        if (sameProcess && running) return Verdict.RUNNING_IN_THIS_PROCESS;
        return Verdict.INTERRUPTED_OR_STALE;
    }

    /**
     * Elapsed time alone is not a boot identity: a later reboot can have a
     * greater uptime than the old session. Prefer Android boot count; for
     * older records or restricted OEMs, use a conservative boot-time estimate.
     * If the wall clock was changed significantly, return uncertain instead
     * of falsely labeling the previous session as interrupted.
     */
    public static boolean isSameBoot(long previousBootCount, long currentBootCount,
                                     long previousWall, long previousElapsed,
                                     long currentWall, long currentElapsed) {
        if (previousElapsed < 0L || currentElapsed < previousElapsed) return false;
        if (previousBootCount >= 0L && currentBootCount >= 0L) {
            return previousBootCount == currentBootCount;
        }
        if (previousWall <= 0L || currentWall <= 0L) return false;
        long previousBootEpoch = previousWall - previousElapsed;
        long currentBootEpoch = currentWall - currentElapsed;
        return Math.abs(previousBootEpoch - currentBootEpoch) <= BOOT_EPOCH_TOLERANCE_MS;
    }

    private static long readBootCount(Context context) {
        try {
            return Settings.Global.getInt(context.getContentResolver(),
                    Settings.Global.BOOT_COUNT, -1);
        } catch (RuntimeException ignored) {
            // Some OEM builds restrict this setting. Fall back conservatively.
            return -1L;
        }
    }

    /**
     * Structured equivalent of the lifecycle report verdict.
     * Readers must not parse translated text or infer live provider state.
     */
    /** Wall-clock anchor only; not a cryptographic or monotonic session identity. */
    public static long lastServiceStartWall(Context context) {
        return preferences(context).getLong(KEY_STARTED_WALL, 0L);
    }

    public static Verdict currentVerdict(Context context, boolean serviceRunning) {
        SharedPreferences prefs = preferences(context);
        String state = prefs.getString(KEY_STATE, "");
        boolean hasRecord = STATE_ACTIVE.equals(state) || STATE_STOPPED.equals(state);
        boolean sameProcess = PROCESS_GENERATION.equals(
                prefs.getString(KEY_GENERATION, ""));
        long elapsed = SystemClock.elapsedRealtime();
        long wall = System.currentTimeMillis();
        long bootCount = readBootCount(context);
        long startedElapsed = prefs.getLong(KEY_STARTED_ELAPSED, -1L);
        boolean sameBoot = isSameBoot(
                prefs.getLong(KEY_STARTED_BOOT_COUNT, -1L), bootCount,
                prefs.getLong(KEY_STARTED_WALL, 0L), startedElapsed, wall, elapsed);
        return classify(hasRecord, STATE_STOPPED.equals(state),
                sameProcess, serviceRunning, sameBoot);
    }

    public static String report(Context context, boolean serviceRunning) {
        SharedPreferences prefs = preferences(context);
        String state = prefs.getString(KEY_STATE, "");
        boolean hasRecord = STATE_ACTIVE.equals(state) || STATE_STOPPED.equals(state);
        long bootCount = readBootCount(context);
        Verdict verdict = currentVerdict(context, serviceRunning);

        StringBuilder text = new StringBuilder();
        text.append("跨进程最近生命周期事件（不是实时服务状态）: ")
                .append(lastCrossProcessEvent(context)).append('\n');
        text.append("以下偏好存档在独立消费者进程可能过时，不能确认主服务状态。\n");
        text.append("当前报告进程 PID: ").append(Process.myPid()).append('\n');
        text.append("ServiceGo 本报告进程标记: ")
                .append(serviceRunning ? "RUNNING" : "STOPPED").append('\n');
        text.append("模拟位置 AppOps: ").append(mockLocationAppOps(context)).append('\n');
        text.append("开机身份来源: ").append(bootCount >= 0L ? "BOOT_COUNT" : "开机时间估算").append('\n');
        text.append("已记录模拟会话: ").append(prefs.getLong(KEY_SESSION_COUNT, 0L)).append('\n');
        text.append("最近一次服务启动: ")
                .append(formatTime(prefs.getLong(KEY_STARTED_WALL, 0L))).append('\n');
        if (hasRecord) {
            text.append("记录所属进程 PID: ").append(prefs.getInt(KEY_PID, -1)).append('\n');
        }
        text.append("Framework Provider 清理: ")
                .append(prefs.getString(KEY_PROVIDER_CLEANUP, "NO_RECORD"))
                .append("；queued=").append(formatTime(prefs.getLong(KEY_PROVIDER_CLEANUP_QUEUED, 0)))
                .append("；finished=").append(formatTime(prefs.getLong(KEY_PROVIDER_CLEANUP_FINISHED, 0)))
                .append("（执行过清理不证明系统已恢复真实定位）\n");
        text.append("最近一次正常结束: ")
                .append(formatTime(prefs.getLong(KEY_STOPPED_WALL, 0L))).append('\n');

        switch (verdict) {
            case NO_RECORD:
                text.append("生命周期判断: 尚无模拟服务记录");
                break;
            case NORMAL_STOP:
                text.append("生命周期判断: 上一次服务执行过正常停止回调");
                break;
            case RUNNING_IN_THIS_PROCESS:
                text.append("生命周期判断: 记录与本进程运行状态一致");
                break;
            case DEVICE_REBOOT_UNCERTAIN:
                text.append("生命周期判断: 可能跨设备重启，旧记录无法判断");
                break;
            default:
                text.append("生命周期判断: 上一次启动后未记录正常停止");
                text.append("\n可能原因: Shizuku 撤权强停、系统杀进程、崩溃或服务初始化失败");
                text.append("\n注意: 这不证明系统 test provider 仍然存在");
                break;
        }
        long interruptions = prefs.getLong(KEY_INTERRUPTION_COUNT, 0L);
        if (interruptions > 0L) {
            text.append("\n历史疑似非正常结束: ").append(interruptions);
            text.append("\n上次发现时间: ")
                    .append(formatTime(prefs.getLong(KEY_LAST_INTERRUPTED_WALL, 0L)));
        }
        return text.toString();
    }

    private static String mockLocationAppOps(Context context) {
        try {
            AppOpsManager manager = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
            if (manager == null) return "不可读取";
            int mode = manager.checkOpNoThrow(AppOpsManager.OPSTR_MOCK_LOCATION,
                    Process.myUid(), context.getPackageName());
            if (mode == AppOpsManager.MODE_ALLOWED) return "ALLOWED";
            if (mode == AppOpsManager.MODE_IGNORED) return "IGNORED";
            if (mode == AppOpsManager.MODE_ERRORED) return "ERRORED";
            if (mode == AppOpsManager.MODE_DEFAULT) return "DEFAULT";
            return "MODE_" + mode;
        } catch (Throwable t) {
            return "读取失败 (" + t.getClass().getSimpleName() + ")";
        }
    }

    private static String formatTime(long time) {
        if (time <= 0L) return "无记录";
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                .format(new Date(time));
    }
}
