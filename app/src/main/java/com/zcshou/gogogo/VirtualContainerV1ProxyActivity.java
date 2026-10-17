package com.zcshou.gogogo;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Process;
import android.provider.Settings;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.zcshou.gogogo.virtualv0.ContainerV0TrustedGuest;

import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

/**
 * Real HOST Activity + UI/lifecycle bridge from the self-authored standalone
 * fixture APK. Android does NOT start an uninstalled guest Activity here.
 */
public final class VirtualContainerV1ProxyActivity extends AppCompatActivity {
    public static final String PREFS = "twt_v1_lifecycle";
    public static final String LAST_RESULT = "last_result";

    private static final String GUEST_CLASS =
            "com.soozooq.tianwaitian.fixture.GuestLifecycleV1";
    private static final String GUEST_UI_CLASS =
            "com.soozooq.tianwaitian.fixture.GuestUiV1";
    private static final String KEY_STARTED = "twt_v1_started";
    private static final String KEY_HISTORY = "twt_v1_history";
    private static final String KEY_CLICKS = "twt_v1_clicks";
    private static final String KEY_RECREATES = "twt_v1_recreates";

    private final ArrayList<String> pending = new ArrayList<>();
    private final StringBuilder events = new StringBuilder(2400);
    private String startedAt;
    private TextView status;
    private TextView report;
    private FrameLayout guestFrame;
    private ContainerV0TrustedGuest fixture;
    private Object lifecycle;
    private Method guestEvent;
    private Method guestSnapshot;
    private Object guestUi;
    private Method guestClicks;
    private Method guestUiSnapshot;
    private Method guestDispose;
    private int restoredClicks;
    private int recreates;
    private boolean restoreVerified;
    private boolean loaded;
    private boolean viewCreated;
    private boolean destroyed;
    private String digest = "(未加载)";
    private String error = "";

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        startedAt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA)
                .format(new Date());
        if (state != null) {
            startedAt = state.getString(KEY_STARTED, startedAt);
            String previous = state.getString(KEY_HISTORY, "");
            if (previous.length() > 0) events.append(previous);
            restoredClicks = state.getInt(KEY_CLICKS, 0);
            recreates = state.getInt(KEY_RECREATES, 0) + 1;
            events.append("HOST instance recreated; savedClicks=")
                    .append(restoredClicks).append('\n');
        }

        ScrollView scroll = new ScrollView(this);
        GoGoUi.applyScreenBackground(scroll);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int p = GoGoUi.dp(this, 18);
        root.setPadding(p, p, p, p);
        scroll.addView(root);
        GoGoUi.addLabHeader(root, "SYSTEMS / CONTAINER V1",
                "受控 APK 的 UI / 生命周期桥",
                "独立 APK 自己创建 Android View · 宿主代理负责显示",
                v -> finish());
        root.addView(GoGoUi.muted(this,
                "点击下方深色区域内的按钮：它的 View 与事件处理器来自独立测试 APK。"
                        + "页面属于天外天宿主，不代表系统直接启动了未安装 APK 的 Activity。"),
                GoGoUi.matchWrap());
        status = GoGoUi.muted(this, "正在装载独立 APK 的 UI 与生命周期对象…");
        root.addView(status, GoGoUi.matchWrap());
        guestFrame = new FrameLayout(this);
        root.addView(guestFrame, GoGoUi.matchWrap());

        root.addView(GoGoUi.secondaryButton(this,
                "测试后台恢复：打开系统设置，再返回", v -> {
                    try {
                        startActivity(new Intent(Settings.ACTION_SETTINGS));
                    } catch (RuntimeException failure) {
                        error = "FAIL：打开系统设置 · " + failure.getClass().getSimpleName();
                        updateReport();
                    }
                }), GoGoUi.matchWrap());
        root.addView(GoGoUi.secondaryButton(this,
                "测试界面重建：保留点击数重新加载", v -> recreate()),
                GoGoUi.matchWrap());
        root.addView(GoGoUi.secondaryButton(this, "刷新当前 UI 与生命周期报告",
                v -> updateReport()), GoGoUi.matchWrap());
        root.addView(GoGoUi.secondaryButton(this,
                "完成并退出（记录 guest View 清理与 onDestroy）", v -> finish()),
                GoGoUi.matchWrap());
        root.addView(GoGoUi.muted(this,
                "只需回 V0 页复制一次「总报告」，此页日志会自动作为附录。"),
                GoGoUi.matchWrap());

        report = GoGoUi.reportPanel(this);
        root.addView(report, GoGoUi.matchWrap());
        setContentView(scroll);
        forward("onCreate");
        loadGuestAsync();
    }

    private void loadGuestAsync() {
        Context context = getApplicationContext();
        new Thread(() -> {
            ContainerV0TrustedGuest candidate = null;
            Object bridge = null;
            Method eventMethod = null;
            Method snapshotMethod = null;
            Class<?> uiClass = null;
            String failure = null;
            try {
                candidate = ContainerV0TrustedGuest.open(context);
                ClassLoader loader = candidate.getGuestClass().getClassLoader();
                Class<?> cls = Class.forName(GUEST_CLASS, true, loader);
                uiClass = Class.forName(GUEST_UI_CLASS, true, loader);
                if (cls.getClassLoader() != loader || uiClass.getClassLoader() != loader) {
                    throw new SecurityException("V1 classes must come from the guest APK DEX");
                }
                bridge = cls.getDeclaredConstructor().newInstance();
                eventMethod = cls.getMethod("onEvent", String.class);
                snapshotMethod = cls.getMethod("snapshot");
                uiClass.getMethod("createView");
                uiClass.getMethod("getClickCount");
                uiClass.getMethod("snapshot");
                uiClass.getMethod("dispose");
            } catch (Throwable caught) {
                failure = caught.getClass().getSimpleName()
                        + (caught.getMessage() == null ? "" : " · " + caught.getMessage());
            }
            final ContainerV0TrustedGuest readyGuest = candidate;
            final Object readyBridge = bridge;
            final Method readyEvent = eventMethod;
            final Method readySnapshot = snapshotMethod;
            final Class<?> readyUiClass = uiClass;
            final String readyFailure = failure;
            runOnUiThread(() -> {
                if (destroyed || isDestroyed()) {
                    if (readyGuest != null) readyGuest.close();
                    return;
                }
                if (readyFailure != null) {
                    error = "FAIL：装载独立 APK UI/生命周期类 · " + readyFailure;
                    if (readyGuest != null) readyGuest.close();
                } else {
                    try {
                        fixture = readyGuest;
                        lifecycle = readyBridge;
                        guestEvent = readyEvent;
                        guestSnapshot = readySnapshot;
                        digest = readyGuest.getSha256();
                        loaded = true;
                        for (String event : pending) dispatchGuest(event);
                        // View creation MUST happen on Android's main/UI thread.
                        guestUi = readyUiClass.getConstructor(Context.class, int.class)
                                .newInstance(this, restoredClicks);
                        guestClicks = readyUiClass.getMethod("getClickCount");
                        guestUiSnapshot = readyUiClass.getMethod("snapshot");
                        guestDispose = readyUiClass.getMethod("dispose");
                        View guestView = (View) readyUiClass.getMethod("createView")
                                .invoke(guestUi);
                        guestFrame.addView(guestView);
                        viewCreated = guestView.getParent() == guestFrame;
                        restoreVerified = clickCount() == restoredClicks;
                        events.append("GUEST VIEW created by fixture DEX, restoredClicks=")
                                .append(restoredClicks)
                                .append(", verified=").append(restoreVerified).append('\n');
                    } catch (Throwable failure) {
                        error = "FAIL：guest UI 构建/嵌入 · "
                                + failure.getClass().getSimpleName()
                                + (failure.getMessage() == null ? "" : " · " + failure.getMessage());
                        events.append(error).append('\n');
                    }
                }
                pending.clear();
                updateReport();
                persist();
            });
        }, "twt-v1-load-guest").start();
    }

    private int clickCount() {
        if (guestUi == null || guestClicks == null) return restoredClicks;
        try {
            return (Integer) guestClicks.invoke(guestUi);
        } catch (Throwable failure) {
            error = "FAIL：读取 guest 点击数 · " + failure.getClass().getSimpleName();
            return restoredClicks;
        }
    }

    private void forward(String event) {
        if (destroyed) return;
        events.append("HOST ").append(event).append('\n');
        if (loaded) dispatchGuest(event);
        else if (pending.size() < 48) pending.add(event);
        updateReport();
        persist();
    }

    private void dispatchGuest(String event) {
        try {
            String line = (String) guestEvent.invoke(lifecycle, event);
            events.append("GUEST ").append(line).append('\n');
        } catch (Throwable failure) {
            error = "FAIL：独立 APK 生命周期 " + event + " · "
                    + failure.getClass().getSimpleName()
                    + (failure.getMessage() == null ? "" : " · " + failure.getMessage());
            events.append(error).append('\n');
        }
    }

    private String buildReport() {
        StringBuilder out = new StringBuilder(3600);
        out.append("天外天 / V1 独立 APK UI + 生命周期桥诊断\n")
                .append("开始时间：").append(startedAt).append('\n')
                .append("Android API=").append(Build.VERSION.SDK_INT)
                .append("；宿主 PID=").append(Process.myPid())
                .append("；UID=").append(Process.myUid()).append('\n')
                .append("受控 APK SHA-256：").append(digest).append('\n')
                .append("生命周期桥加载：").append(loaded).append('\n')
                .append("独立 APK 创建 View：").append(viewCreated).append('\n')
                .append("guest 点击数：").append(clickCount()).append('\n')
                .append("Activity 重建次数：").append(recreates).append('\n')
                .append("状态恢复一致：")
                .append(recreates > 0 ? String.valueOf(restoreVerified)
                        : "未测试（请点击界面重建）").append('\n')
                .append("后台恢复观察：")
                .append(events.indexOf("HOST onRestart") >= 0).append('\n');
        if (!error.isEmpty()) out.append(error).append('\n');
        out.append("HOST / GUEST 生命周期时间线：\n").append(events);
        if (loaded && guestSnapshot != null) {
            try {
                out.append("\n来自独立 APK 的生命周期快照：\n")
                        .append(guestSnapshot.invoke(lifecycle)).append('\n');
            } catch (Throwable t) {
                out.append("\nFAIL：guest lifecycle snapshot ")
                        .append(t.getClass().getSimpleName()).append('\n');
            }
        }
        if (guestUi != null && guestUiSnapshot != null) {
            try {
                out.append("\n来自独立 APK 的 UI 快照：\n")
                        .append(guestUiSnapshot.invoke(guestUi)).append('\n');
            } catch (Throwable t) {
                out.append("\nFAIL：guest UI snapshot ")
                        .append(t.getClass().getSimpleName()).append('\n');
            }
        }
        out.append("\n边界：View 创建逻辑位于受控 APK，Android Activity、Context、")
                .append("UID 与权限仍然属于天外天宿主。\n");
        if (destroyed) {
            boolean ok = error.isEmpty() && viewCreated && clickCount() > 0
                    && recreates > 0 && restoreVerified
                    && events.indexOf("HOST onRestart") >= 0
                    && events.indexOf("GUEST VIEW disposed=true") >= 0;
            out.append("\nV1 UI 交互/重建/清理联合验收：")
                    .append(ok ? "PASS" : "INCOMPLETE/FAIL（请核对各项证据）")
                    .append('\n');
        }
        return out.toString();
    }

    private void updateReport() {
        if (report == null) return;
        status.setText(!error.isEmpty() ? "实验有失败，请回 V0 一次复制总报告"
                : viewCreated ? "受控 APK 的 View 已显示。请测试点击、后台与重建"
                : "等待独立 APK 装载/创建 View…");
        report.setText(buildReport());
    }

    private void persist() {
        if (startedAt != null) getSharedPreferences(PREFS, MODE_PRIVATE)
                .edit().putString(LAST_RESULT, buildReport()).apply();
    }

    @Override protected void onSaveInstanceState(Bundle outState) {
        events.append("HOST onSaveInstanceState clicks=").append(clickCount()).append('\n');
        outState.putString(KEY_STARTED, startedAt);
        outState.putString(KEY_HISTORY, events.toString());
        outState.putInt(KEY_CLICKS, clickCount());
        outState.putInt(KEY_RECREATES, recreates);
        persist();
        super.onSaveInstanceState(outState);
    }

    @Override protected void onStart() { super.onStart(); forward("onStart"); }
    @Override protected void onResume() { super.onResume(); forward("onResume"); }
    @Override protected void onRestart() { super.onRestart(); forward("onRestart"); }
    @Override protected void onPause() { forward("onPause"); super.onPause(); }
    @Override protected void onStop() { forward("onStop"); super.onStop(); }

    @Override protected void onDestroy() {
        forward("onDestroy");
        if (guestUi != null && guestDispose != null) {
            try {
                guestDispose.invoke(guestUi);
                events.append("GUEST VIEW disposed=true\n");
            } catch (Throwable failure) {
                error = "FAIL：guest View dispose · "
                        + failure.getClass().getSimpleName();
                events.append(error).append('\n');
            }
        }
        destroyed = true;
        persist();
        if (fixture != null) {
            fixture.close();
            fixture = null;
        }
        super.onDestroy();
    }
}
