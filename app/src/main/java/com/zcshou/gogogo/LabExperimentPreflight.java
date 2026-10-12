package com.zcshou.gogogo;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.LauncherApps;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Process;
import android.os.UserHandle;
import android.provider.Settings;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

/**
 * User-visible experiment preflight, no location retrieval and no third-party
 * force-stop. Main mock state is observed in the actual service process; current
 * user WeChat state comes from PackageManager's FLAG_STOPPED only.
 */
public final class LabExperimentPreflight {
    public static final String WECHAT_PACKAGE = "com.tencent.mm";

    private final Activity activity;
    private final LinearLayout root;
    private final TextView serviceStatus;
    private final TextView weChatStatus;
    private final TextView cloneStatus;
    private int refreshGeneration;

    public LabExperimentPreflight(Activity activity) {
        this.activity = activity;
        root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.addView(GoGoUi.sectionTitle(activity, "实验前置检查"),
                GoGoUi.matchWrap());
        root.addView(GoGoUi.muted(activity,
                "主进程状态与微信停用标记单独检查；不触发定位、不强停第三方应用"),
                GoGoUi.matchWrap());
        root.addView(GoGoUi.gap(activity, 8));
        serviceStatus = GoGoUi.muted(activity, "模拟服务：等待读取…");
        weChatStatus = GoGoUi.muted(activity, "主微信：等待读取…");
        cloneStatus = GoGoUi.muted(activity, "vivo 微信分身：等待读取…");
        root.addView(serviceStatus, GoGoUi.matchWrap());
        root.addView(weChatStatus, GoGoUi.matchWrap());
        root.addView(cloneStatus, GoGoUi.matchWrap());
        root.addView(GoGoUi.gap(activity, 8));
        root.addView(GoGoUi.primaryButton(activity, "去首页启动／停止模拟",
                v -> activity.startActivity(new Intent(activity, SimpleMockActivity.class))),
                GoGoUi.matchWrap());
        root.addView(GoGoUi.gap(activity, 6));
        root.addView(GoGoUi.secondaryButton(activity, "刷新模拟与微信状态",
                v -> refresh()), GoGoUi.matchWrap());
        root.addView(GoGoUi.gap(activity, 6));
        root.addView(GoGoUi.secondaryButton(activity, "检查微信后台进程（Shizuku 只读）",
                v -> activity.startActivity(
                        new Intent(activity, LabDiagnosticsActivity.class)
                                .putExtra(LabDiagnosticsActivity.EXTRA_WECHAT_PROCESS_SNAPSHOT, true))),
                GoGoUi.matchWrap());
        root.addView(GoGoUi.gap(activity, 6));
        root.addView(GoGoUi.secondaryButton(activity, "打开主微信应用详情（手动强停）",
                v -> openSettings(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + WECHAT_PACKAGE)))), GoGoUi.matchWrap());
        root.addView(GoGoUi.gap(activity, 6));
        root.addView(GoGoUi.secondaryButton(activity, "打开应用管理（查找分身微信）",
                v -> openSettings(new Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS))),
                GoGoUi.matchWrap());
        root.addView(GoGoUi.gap(activity, 8));
        root.addView(GoGoUi.muted(activity,
                "说明：FLAG_STOPPED 是当前用户的包状态，不是进程是否存活。"
                        + "Shizuku 只读快照可显示当时可见的微信主进程/子进程，"
                        + "但不能仅凭进程名可靠区分主号和 vivo 分身；系统强停按钮能否再次点击不作依据。"
                        + "不要关闭分身或清除应用数据。"),
                GoGoUi.matchWrap());
    }

    public LinearLayout view() {
        return root;
    }

    public void refresh() {
        final int generation = ++refreshGeneration;
        serviceStatus.setText("模拟服务：读取主进程状态中…");
        weChatStatus.setText("主微信包状态：查询 stopped 标记中（非后台进程）…");
        cloneStatus.setText("vivo 分身：查询可见配置中…");
        // A cold content-provider bind can start the app's main process.
        // Never perform that Binder call on the UI thread.
        new Thread(() -> {
            final Boolean running = observeMainProcessService(activity);
            final Boolean stopped = observeCurrentUserWeChat(activity);
            final String clone = observeVisibleClone(activity);
            activity.runOnUiThread(() -> {
                if (generation != refreshGeneration || activity.isFinishing()
                        || activity.isDestroyed()) return;
                serviceStatus.setText("模拟服务：" + LabExperimentStatusText.serviceFlag(running));
                weChatStatus.setText("主微信包状态：" + LabExperimentStatusText.weChatStopped(stopped));
                cloneStatus.setText("vivo 分身：" + clone);
            });
        }, "GoGoGo-ExperimentPreflight").start();
    }

    public void close() {
        ++refreshGeneration;
    }

    public static Boolean observeMainProcessService(Context context) {
        try {
            Uri uri = Uri.parse("content://" + BuildConfig.APPLICATION_ID
                    + LabRuntimeStatusProvider.AUTHORITY_SUFFIX);
            Bundle result = context.getContentResolver().call(
                    uri, LabRuntimeStatusProvider.METHOD_OBSERVE, null, null);
            return result == null || !result.containsKey("running")
                    ? null : result.getBoolean("running");
        } catch (RuntimeException unavailable) {
            return null;
        }
    }

    public static Boolean observeCurrentUserWeChat(Context context) {
        try {
            ApplicationInfo info = context.getPackageManager()
                    .getApplicationInfo(WECHAT_PACKAGE, 0);
            return (info.flags & ApplicationInfo.FLAG_STOPPED) != 0;
        } catch (PackageManager.NameNotFoundException | SecurityException unavailable) {
            return null;
        }
    }

    /** Only launcher-visible alternate profiles. No claim this enumerates vivo clones. */
    public static String observeVisibleClone(Context context) {
        try {
            LauncherApps apps = (LauncherApps) context.getSystemService(
                    Context.LAUNCHER_APPS_SERVICE);
            if (apps == null) return "系统未开放可见用户配置，状态未知";
            List<UserHandle> profiles = apps.getProfiles();
            if (profiles == null) return "系统未开放可见用户配置，状态未知";
            for (UserHandle profile : profiles) {
                if (!Process.myUserHandle().equals(profile)
                        && !apps.getActivityList(WECHAT_PACKAGE, profile).isEmpty()) {
                    return "检测到其他可见配置的微信入口；停止状态仍未知";
                }
            }
            return "未发现可见分身入口（不代表没有分身）；停止状态未知";
        } catch (RuntimeException unavailable) {
            return "无法读取分身所在用户空间；停止状态未知";
        }
    }

    private void openSettings(Intent intent) {
        try {
            activity.startActivity(intent);
        } catch (RuntimeException unavailable) {
            try {
                activity.startActivity(new Intent(Settings.ACTION_SETTINGS));
            } catch (RuntimeException ignored) {
                Toast.makeText(activity, "无法打开系统设置", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
