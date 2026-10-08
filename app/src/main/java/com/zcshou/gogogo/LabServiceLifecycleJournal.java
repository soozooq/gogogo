package com.zcshou.gogogo;

import android.app.AppOpsManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Process;
import android.os.SystemClock;

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
    private static final String KEY_STATE = "state";
    private static final String KEY_PID = "service_pid";
    private static final String KEY_GENERATION = "service_generation";
    private static final String KEY_STARTED_WALL = "started_wall";
    private static final String KEY_STARTED_ELAPSED = "started_elapsed";
    private static final String KEY_STOPPED_WALL = "stopped_wall";
    private static final String KEY_SESSION_COUNT = "session_count";
    private static final String KEY_INTERRUPTION_COUNT = "interruption_count";
    private static final String KEY_LAST_INTERRUPTED_WALL = "last_interrupted_wall";
    private static final String STATE_ACTIVE = "ACTIVE";
    private static final String STATE_STOPPED = "STOPPED";

    // New on each process load, even when Android happens to reuse a PID.
    private static final String PROCESS_GENERATION = UUID.randomUUID().toString();

    private LabServiceLifecycleJournal() {
    }

    private static SharedPreferences preferences(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
    }

    /** Call once in ServiceGo.onCreate(), before provider setup. */
    public static synchronized void onServiceCreated(Context context) {
        SharedPreferences prefs = preferences(context);
        long nowElapsed = SystemClock.elapsedRealtime();
        long nowWall = System.currentTimeMillis();
        boolean priorOpen = STATE_ACTIVE.equals(prefs.getString(KEY_STATE, ""));
        boolean priorProcess = PROCESS_GENERATION.equals(
                prefs.getString(KEY_GENERATION, ""));
        long priorStart = prefs.getLong(KEY_STARTED_ELAPSED, -1L);
        boolean sameBoot = priorStart >= 0L && priorStart <= nowElapsed;
        SharedPreferences.Editor editor = prefs.edit();

        // A new process cannot close a previously open session; record only
        // an interruption *candidate*. We never claim providers stayed mocked.
        if (priorOpen && !priorProcess && sameBoot) {
            editor.putLong(KEY_INTERRUPTION_COUNT,
                    prefs.getLong(KEY_INTERRUPTION_COUNT, 0L) + 1L);
            editor.putLong(KEY_LAST_INTERRUPTED_WALL, nowWall);
        }

        editor.putString(KEY_STATE, STATE_ACTIVE)
                .putString(KEY_GENERATION, PROCESS_GENERATION)
                .putInt(KEY_PID, Process.myPid())
                .putLong(KEY_STARTED_WALL, nowWall)
                .putLong(KEY_STARTED_ELAPSED, nowElapsed)
                .putLong(KEY_SESSION_COUNT, prefs.getLong(KEY_SESSION_COUNT, 0L) + 1L)
                .commit(); // A process may be force-stopped without onDestroy().
    }

    /** Call after ServiceGo's normal best-effort cleanup has completed. */
    public static synchronized void onServiceDestroyed(Context context) {
        SharedPreferences prefs = preferences(context);
        if (!PROCESS_GENERATION.equals(prefs.getString(KEY_GENERATION, ""))) {
            return;
        }
        prefs.edit()
                .putString(KEY_STATE, STATE_STOPPED)
                .putLong(KEY_STOPPED_WALL, System.currentTimeMillis())
                .commit();
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

    public static String report(Context context, boolean serviceRunning) {
        SharedPreferences prefs = preferences(context);
        String state = prefs.getString(KEY_STATE, "");
        boolean hasRecord = STATE_ACTIVE.equals(state) || STATE_STOPPED.equals(state);
        boolean sameProcess = PROCESS_GENERATION.equals(
                prefs.getString(KEY_GENERATION, ""));
        long elapsed = SystemClock.elapsedRealtime();
        long startedElapsed = prefs.getLong(KEY_STARTED_ELAPSED, -1L);
        boolean sameBoot = startedElapsed >= 0L && startedElapsed <= elapsed;
        Verdict verdict = classify(hasRecord, STATE_STOPPED.equals(state),
                sameProcess, serviceRunning, sameBoot);

        StringBuilder text = new StringBuilder();
        text.append("当前主进程 PID: ").append(Process.myPid()).append('\n');
        text.append("ServiceGo 当前运行标记: ").append(serviceRunning ? "RUNNING" : "STOPPED").append('\n');
        text.append("模拟位置 AppOps: ").append(mockLocationAppOps(context)).append('\n');
        text.append("已记录模拟会话: ").append(prefs.getLong(KEY_SESSION_COUNT, 0L)).append('\n');
        text.append("最近一次服务启动: ")
                .append(formatTime(prefs.getLong(KEY_STARTED_WALL, 0L))).append('\n');
        if (hasRecord) {
            text.append("记录所属进程 PID: ").append(prefs.getInt(KEY_PID, -1)).append('\n');
        }
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
