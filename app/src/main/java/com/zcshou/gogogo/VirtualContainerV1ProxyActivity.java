package com.zcshou.gogogo;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Process;
import android.provider.Settings;
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
 * V1 first step: REAL Android lifecycle on this registered host proxy Activity,
 * forwarded to an auditable object loaded from the SEPARATE trusted fixture APK.
 *
 * The guest is NOT an Android Activity. No uninstalled Activity is launched
 * by ActivityManager, and no guest privileges or identities are virtualized.
 */
public final class VirtualContainerV1ProxyActivity extends AppCompatActivity {
    public static final String PREFS = "twt_v1_lifecycle";
    public static final String LAST_RESULT = "last_result";

    private static final String GUEST_CLASS =
            "com.soozooq.tianwaitian.fixture.GuestLifecycleV1";
    private final ArrayList<String> pending = new ArrayList<>();
    private final StringBuilder events = new StringBuilder(1600);
    private String startedAt;
    private TextView status;
    private TextView report;
    private ContainerV0TrustedGuest fixture;
    private Object lifecycle;
    private Method guestEvent;
    private Method guestSnapshot;
    private boolean loaded;
    private boolean destroyed;
    private String digest = "(未加载)";
    private String error = "";

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        startedAt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA)
                .format(new Date());

        ScrollView scroll = new ScrollView(this);
        GoGoUi.applyScreenBackground(scroll);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int p = GoGoUi.dp(this, 18);
        root.setPadding(p, p, p, p);
        scroll.addView(root);
        GoGoUi.addLabHeader(root, "SYSTEMS / CONTAINER V1",
                "受控 APK 生命周期代理",
                "宿主 Activity 接收真实系统回调 → 独立 APK 对象记录事件",
                v -> finish());
        root.addView(GoGoUi.muted(this,
                "此页是天外天注册的代理 Activity，并非直接启动未安装 APK 的 Activity。"
                        + "退出后会将完整报告保留给 V0 页的「复制总报告」。"),
                GoGoUi.matchWrap());
        status = GoGoUi.muted(this, "正在装载受控 APK 生命周期对象…");
        root.addView(status, GoGoUi.matchWrap());
        root.addView(GoGoUi.secondaryButton(this,
                "打开系统设置，再返回本页（测试暂停/恢复）", v -> {
                    try {
                        startActivity(new Intent(Settings.ACTION_SETTINGS));
                    } catch (RuntimeException failure) {
                        error = "无法打开系统设置：" + failure.getClass().getSimpleName();
                        updateReport();
                    }
                }), GoGoUi.matchWrap());
        root.addView(GoGoUi.secondaryButton(this,
                "完成并退出（记录 onDestroy）", v -> finish()),
                GoGoUi.matchWrap());
        root.addView(GoGoUi.secondaryButton(this, "复制当前生命周期报告", v -> {
            ClipboardManager cm = (ClipboardManager)
                    getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) {
                cm.setPrimaryClip(ClipData.newPlainText("Tianwaitian V1", buildReport()));
                Toast.makeText(this, "生命周期报告已复制", Toast.LENGTH_SHORT).show();
            }
        }), GoGoUi.matchWrap());
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
            String failure = null;
            try {
                candidate = ContainerV0TrustedGuest.open(context);
                Class<?> cls = Class.forName(GUEST_CLASS, true,
                        candidate.getGuestClass().getClassLoader());
                if (cls.getClassLoader() != candidate.getGuestClass().getClassLoader()) {
                    throw new SecurityException("V1 guest object is not from fixture DEX");
                }
                bridge = cls.getDeclaredConstructor().newInstance();
                eventMethod = cls.getMethod("onEvent", String.class);
                snapshotMethod = cls.getMethod("snapshot");
            } catch (Throwable caught) {
                failure = caught.getClass().getSimpleName()
                        + (caught.getMessage() == null ? "" : " · " + caught.getMessage());
            }
            final ContainerV0TrustedGuest readyGuest = candidate;
            final Object readyBridge = bridge;
            final Method readyEvent = eventMethod;
            final Method readySnapshot = snapshotMethod;
            final String readyFailure = failure;
            runOnUiThread(() -> {
                if (destroyed || isDestroyed()) {
                    if (readyGuest != null) readyGuest.close();
                    return;
                }
                if (readyFailure != null) {
                    error = "FAIL：加载独立 APK 生命周期对象失败 · " + readyFailure;
                    if (readyGuest != null) readyGuest.close();
                } else {
                    fixture = readyGuest;
                    lifecycle = readyBridge;
                    guestEvent = readyEvent;
                    guestSnapshot = readySnapshot;
                    digest = readyGuest.getSha256();
                    loaded = true;
                    for (String event : pending) dispatchGuest(event);
                }
                pending.clear();
                updateReport();
            });
        }, "twt-v1-load-guest").start();
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
        StringBuilder out = new StringBuilder(2700);
        out.append("天外天 / V1 受控 APK 生命周期代理诊断\n")
                .append("开始时间：").append(startedAt).append('\n')
                .append("Android API=").append(Build.VERSION.SDK_INT)
                .append("；宿主 PID=").append(Process.myPid())
                .append("；UID=").append(Process.myUid()).append('\n')
                .append("受控 APK SHA-256：").append(digest).append('\n')
                .append("转发完成：").append(loaded).append('\n');
        if (!error.isEmpty()) out.append(error).append('\n');
        out.append("生命周期时间线：\n").append(events);
        if (loaded && guestSnapshot != null) {
            try {
                out.append("\n来自独立 APK 的生命周期快照：\n")
                        .append(guestSnapshot.invoke(lifecycle)).append('\n');
            } catch (Throwable t) {
                out.append("\nFAIL：guest snapshot ").append(t.getClass().getSimpleName()).append('\n');
            }
        } else {
            out.append("\n独立 APK 尚未装载完成；不可将回调记录视为 PASS。\n");
        }
        out.append("\n限制：实际 Android Activity 属于天外天，"
                + "guest 只是动态装载的受控生命周期对象；仍共享同一 UID。\n");
        return out.toString();
    }

    private void updateReport() {
        if (report == null) return;
        status.setText(!error.isEmpty() ? "生命周期实验存在失败，请复制完整报告"
                : loaded ? "独立 APK 生命周期桥已连接，切换后台后返回可观察回调"
                : "等待独立 APK 载入…");
        report.setText(buildReport());
    }

    private void persist() {
        if (startedAt != null) getSharedPreferences(PREFS, MODE_PRIVATE)
                .edit().putString(LAST_RESULT, buildReport()).apply();
    }

    @Override protected void onStart() { super.onStart(); forward("onStart"); }
    @Override protected void onResume() { super.onResume(); forward("onResume"); }
    @Override protected void onRestart() { super.onRestart(); forward("onRestart"); }
    @Override protected void onPause() { forward("onPause"); super.onPause(); }
    @Override protected void onStop() { forward("onStop"); super.onStop(); }

    @Override protected void onDestroy() {
        forward("onDestroy");
        persist();
        destroyed = true;
        if (fixture != null) {
            fixture.close();
            fixture = null;
        }
        super.onDestroy();
    }
}
