package com.zcshou.gogogo;

import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;

import com.zcshou.gogogo.shizuku.ILabPrivilegedService;
import com.zcshou.gogogo.shizuku.LabPrivilegedService;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import rikka.shizuku.Shizuku;

/**
 * Completely optional system-wide time zone writer. Enabled separately from
 * VPN coordinate following and only when the user authorized Shizuku.
 * Never changes the device clock, automatic time-zone detection or GPS.
 */
public final class LabVpnSystemTimeZone {
    public static final String KEY_ENABLED = "system_timezone_follow";
    public static final String KEY_STATUS = "system_timezone_status";

    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Shizuku.UserServiceArgs args = new Shizuku.UserServiceArgs(
            new ComponentName(BuildConfig.APPLICATION_ID,
                    LabPrivilegedService.class.getName()))
            .daemon(false).processNameSuffix("lab_privileged")
            .debuggable(BuildConfig.DEBUG).version(BuildConfig.VERSION_CODE);
    private ILabPrivilegedService privileged;
    private boolean binding;
    private boolean closed;
    private String pendingZone = "";
    private int epoch;

    private final ServiceConnection connection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            binding = false;
            if (closed || !isEnabled(context)) return;
            privileged = ILabPrivilegedService.Stub.asInterface(binder);
            submitPending();
        }
        @Override public void onServiceDisconnected(ComponentName name) {
            privileged = null;
            binding = false;
            if (!closed) status("Shizuku UserService 连接中断；系统时区未保证更新");
        }
    };

    public LabVpnSystemTimeZone(Context context) {
        this.context = context.getApplicationContext();
    }

    public static boolean isEnabled(Context context) {
        return context.getSharedPreferences(LabVpnExitSync.PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_ENABLED, false);
    }

    public static void setEnabled(Context context, boolean enabled) {
        context.getSharedPreferences(LabVpnExitSync.PREFS, Context.MODE_PRIVATE).edit()
                .putBoolean(KEY_ENABLED, enabled)
                .putString(KEY_STATUS, enabled
                        ? "已授权系统时区跟随；等待合适的 VPN 时区和 Shizuku"
                        : "系统时区自动跟随已关闭；不会还原或修改手机时区")
                .apply();
    }

    public static String statusText(Context context) {
        return context.getSharedPreferences(LabVpnExitSync.PREFS, Context.MODE_PRIVATE)
                .getString(KEY_STATUS, "默认关闭，不修改手机系统时区");
    }

    /** Triggered only when a VPN result has actually been applied or a locked target is used. */
    public void onTarget(String zone) {
        if (closed || !isEnabled(context)) return;
        if (!LabVpnTimeZone.valid(zone)) {
            status("IP 出口没有可靠的 IANA 时区，保留当前系统时区");
            return;
        }
        // Never silently change the owner's Android automatic time-zone setting.
        if (Settings.Global.getInt(context.getContentResolver(),
                Settings.Global.AUTO_TIME_ZONE, 1) != 0) {
            status("手机自动设置时区仍开启；请先在日期与时间设置中关闭，避免系统改回");
            return;
        }
        if (java.util.TimeZone.getDefault().getID().equals(zone)) {
            status("手机系统时区已一致：" + zone);
            return;
        }
        pendingZone = zone;
        bindOrSubmit();
    }

    /** Used on service start and after preference changes. */
    public void refresh() {
        if (!isEnabled(context)) {
            unbind();
            return;
        }
        onTarget(LabVpnExitSync.timezoneId(context));
    }

    private void bindOrSubmit() {
        if (privileged != null) {
            submitPending();
            return;
        }
        if (binding) return;
        try {
            if (!Shizuku.pingBinder()
                    || Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                status("Shizuku 未运行或未授权：无法自动修改手机系统时区");
                return;
            }
            binding = true;
            Shizuku.bindUserService(args, connection);
            status("正在连接 Shizuku 高权限时区服务…");
        } catch (Throwable problem) {
            binding = false;
            status("Shizuku 服务不可用：" + problem.getClass().getSimpleName());
        }
    }

    private void submitPending() {
        String zone = pendingZone;
        ILabPrivilegedService service = privileged;
        if (closed || !isEnabled(context) || service == null
                || !LabVpnTimeZone.valid(zone)) return;
        int token = ++epoch;
        executor.execute(() -> {
            if (closed || !isEnabled(context) || token != epoch) return;
            // Do not apply stale timezone from an earlier VPN exit.
            String report;
            try {
                report = service.setSystemTimeZone(zone);
            } catch (Throwable problem) {
                report = "UNAVAILABLE:" + problem.getClass().getSimpleName();
            }
            final String answer = report;
            main.post(() -> {
                if (closed || token != epoch) return;
                if (answer != null && answer.startsWith("APPLIED:")) {
                    status("已由 Shizuku 修改手机系统时区：" + zone
                            + "（如系统自动检测重新开启，可能再次覆盖）");
                } else {
                    status("系统时区修改未确认：" + (answer == null ? "UNKNOWN" : answer));
                }
            });
        });
    }

    private void status(String value) {
        context.getSharedPreferences(LabVpnExitSync.PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY_STATUS, value).apply();
    }

    private void unbind() {
        epoch++;
        pendingZone = "";
        if (privileged != null || binding) {
            try {
                Shizuku.unbindUserService(args, connection, false);
            } catch (Throwable ignored) {
            }
        }
        privileged = null;
        binding = false;
    }

    public void close() {
        closed = true;
        unbind();
        executor.shutdownNow();
        main.removeCallbacksAndMessages(null);
    }
}
