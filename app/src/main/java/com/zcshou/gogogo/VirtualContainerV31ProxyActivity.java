package com.zcshou.gogogo;

import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Process;
import android.provider.Settings;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.zcshou.gogogo.virtualv0.ContainerV31GuestResources;
import com.zcshou.gogogo.virtualv0.ContainerV31TrustedDemo;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.UUID;

/** Host-owned Activity showing only an explicitly selected, exact-hash demo APK. */
public final class VirtualContainerV31ProxyActivity extends AppCompatActivity {
    public static final String PREFS = "twt_v31_proxy";
    public static final String LAST_RESULT = "last_result";
    private static final String OWNER = "report_owner";
    private static final String ATTEMPTED = "twt_v31_attempted";
    private static final int PICK_DEMO = 3101;

    private final StringBuilder events = new StringBuilder();
    private final ArrayList<String> pendingLifecycle = new ArrayList<>();
    private FrameLayout guestFrame;
    private TextView status;
    private TextView report;
    private AlertDialog confirmation;
    private ContainerV31TrustedDemo payload;
    private ContainerV31GuestResources resourceScope;
    private Object guest;
    private Method lifecycleMethod;
    private Method clicksMethod;
    private Method snapshotMethod;
    private Method disposeMethod;
    private Method disposedMethod;
    private boolean attempted;
    private boolean loading;
    private boolean destroyed;
    private boolean viewCreated;
    private boolean resourceVerified;
    private boolean lifecycleVerified = true;
    private boolean guestCreated;
    private boolean guestDestroyed;
    private boolean disposed;
    private boolean viewDetached;
    private boolean resourcesClosed;
    private boolean apkDeleted;
    private boolean cleanupAttempted;
    private boolean recreated;
    private int generation;
    private int clicks;
    private String owner;
    private long startedAtWallMs;
    private String digest = "NOT_LOADED";
    private String resourcePackage = "NOT_LOADED";
    private String loaderEvidence = "NOT_LOADED";
    private String resourceEvidence = "NOT_LOADED";
    private String guestSnapshot = "NOT_LOADED";
    private String error = "";
    private String stage = "AWAITING_EXPLICIT_SELECTION";

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        buildUi();
        forward("onCreate");
        if (state != null && state.getBoolean(ATTEMPTED, false)) {
            // A URI, loader or code-cache path in a saved Bundle is never an
            // execution authorization. Require fresh explicit selection.
            attempted = true;
            recreated = true;
            claimReport();
            stage = "RECREATED_FAIL_CLOSED";
            event("HOST recreated: prior import was not automatically re-executed");
            updateReport();
        }
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        GoGoUi.applyScreenBackground(scroll);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = GoGoUi.dp(this, 18);
        root.setPadding(pad, pad, pad, pad);
        scroll.addView(root);
        GoGoUi.addLabHeader(root, "SYSTEMS / CONTAINER V3.1",
                "外部演示 APK · 宿主代理界面",
                "选择本版配套 demo 文件，验证资源、点击和退出清理", v -> finish());
        root.addView(GoGoUi.muted(this,
                "仅接受本次构建配套的开发者演示 APK；不会安装它。"
                        + "选择文件后，在下方点击演示按钮，再完成并退出。"),
                GoGoUi.matchWrap());
        status = GoGoUi.muted(this, "等待选择演示 APK");
        root.addView(status, GoGoUi.matchWrap());
        root.addView(GoGoUi.primaryButton(this,
                "选择配套 demo APK 文件", v -> chooseDemo()), GoGoUi.matchWrap());
        guestFrame = new FrameLayout(this);
        root.addView(guestFrame, GoGoUi.matchWrap());
        root.addView(GoGoUi.secondaryButton(this,
                "测试后台返回：打开系统设置", v -> {
                    if (guest == null) return;
                    try { startActivity(new Intent(Settings.ACTION_SETTINGS)); }
                    catch (RuntimeException failure) {
                        fail("OPEN_SETTINGS", failure);
                        updateReport();
                    }
                }), GoGoUi.matchWrap());
        root.addView(GoGoUi.secondaryButton(this,
                "测试重建：结束本次运行并要求重新选文件", v -> {
                    if (attempted && !loading) recreate();
                }), GoGoUi.matchWrap());
        root.addView(GoGoUi.secondaryButton(this,
                "刷新点击与生命周期报告", v -> updateReport()), GoGoUi.matchWrap());
        root.addView(GoGoUi.secondaryButton(this,
                "完成并退出（清理后回 V0 复制总报告）", v -> finish()),
                GoGoUi.matchWrap());
        report = GoGoUi.reportPanel(this);
        report.setVisibility(View.GONE);
        root.addView(GoGoUi.secondaryButton(this, "展开 / 收起本次运行记录",
                v -> report.setVisibility(report.getVisibility() == View.VISIBLE
                        ? View.GONE : View.VISIBLE)), GoGoUi.matchWrap());
        root.addView(report, GoGoUi.matchWrap());
        setContentView(scroll);
        updateReport();
    }

    private void chooseDemo() {
        if (attempted || loading) {
            status.setText("本页一次只运行一份演示 APK；返回 V0 后可重新进入");
            return;
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            attempted = true;
            claimReport();
            fail("API_CHECK", new UnsupportedOperationException("requires Android API 30+"));
            updateReport();
            return;
        }
        Intent picker = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        picker.addCategory(Intent.CATEGORY_OPENABLE);
        picker.setType("*/*");
        picker.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try { startActivityForResult(picker, PICK_DEMO); }
        catch (RuntimeException failure) {
            attempted = true;
            claimReport();
            fail("OPEN_DOCUMENT", failure);
            updateReport();
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK_DEMO || resultCode != RESULT_OK || data == null
                || data.getData() == null || attempted || destroyed) return;
        final Uri selected = data.getData();
        confirmation = new AlertDialog.Builder(this)
                .setTitle("运行配套开发者演示 APK？")
                .setMessage("核验通过后才会运行本次构建配套的演示文件。"
                        + "其他 APK 不会运行；可在 V3 入口进行只读分析。")
                .setNegativeButton("取消", null)
                .setPositiveButton("核验并运行演示", (dialog, which) -> loadDemo(selected))
                .show();
    }

    private void claimReport() {
        owner = UUID.randomUUID().toString();
        startedAtWallMs = System.currentTimeMillis();
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(OWNER, owner).apply();
    }

    private void loadDemo(Uri selected) {
        if (attempted || destroyed) return;
        attempted = true;
        claimReport();
        if (!lifecycleVerified) {
            stage = "LIFECYCLE_QUEUE_REJECTED";
            updateReport();
            return;
        }
        loading = true;
        stage = "VERIFYING_EXPLICIT_DOCUMENT";
        final int request = ++generation;
        final Context app = getApplicationContext();
        updateReport();
        new Thread(() -> {
            ContainerV31TrustedDemo candidate = null;
            Throwable failure = null;
            try { candidate = ContainerV31TrustedDemo.open(app, selected); }
            catch (Throwable caught) { failure = caught; }
            final ContainerV31TrustedDemo ready = candidate;
            final Throwable caught = failure;
            runOnUiThread(() -> {
                if (destroyed || isFinishing() || isDestroyed() || generation != request) {
                    // Close even when Android destroyed the proxy during import.
                    // The owner guard prevents this old result replacing a new report.
                    if (ready != null) {
                        try {
                            ready.close();
                            apkDeleted = ready.isApkDeleted();
                            event("LATE_IMPORT discarded; apkDeleted=" + apkDeleted);
                        } catch (RuntimeException cleanup) { fail("LATE_IMPORT_CLEANUP", cleanup); }
                    }
                    loading = false;
                    persist();
                    return;
                }
                loading = false;
                if (caught != null) {
                    fail("TRUSTED_IMPORT", caught);
                    updateReport();
                    return;
                }
                payload = ready;
                try {
                    stage = "CREATE_RESOURCE_SCOPE";
                    digest = payload.getSha256();
                    resourcePackage = payload.getGuestPackageName();
                    Class<?> entry = payload.getGuestClass();
                    ClassLoader loader = entry.getClassLoader();
                    loaderEvidence = loader == null ? "BOOTSTRAP" : loader.getClass().getName()
                            + "@" + Integer.toHexString(System.identityHashCode(loader));
                    resourceScope = ContainerV31GuestResources.open(this, payload);
                    Resources resources = resourceScope.getResources();
                    stage = "CONSTRUCT_DEMO_ENTRY";
                    guest = entry.getConstructor(Context.class, Resources.class, int.class)
                            .newInstance(this, resources, 0);
                    lifecycleMethod = entry.getMethod("onHostLifecycle", String.class);
                    clicksMethod = entry.getMethod("getClickCount");
                    snapshotMethod = entry.getMethod("snapshot");
                    disposeMethod = entry.getMethod("dispose");
                    disposedMethod = entry.getMethod("isDisposed");
                    for (String event : pendingLifecycle) dispatchLifecycle(event);
                    pendingLifecycle.clear();
                    stage = "CREATE_DEMO_VIEW";
                    Object result = entry.getMethod("createView").invoke(guest);
                    if (!(result instanceof View)) throw new SecurityException("demo returned no View");
                    View view = (View) result;
                    if (view.getParent() != null) throw new SecurityException("demo View already attached");
                    resourceVerified = Boolean.TRUE.equals(
                            entry.getMethod("resourcesVerified").invoke(guest));
                    resourceEvidence = String.valueOf(entry.getMethod("getResourceEvidence").invoke(guest));
                    if (!resourceVerified) throw new SecurityException("demo resources not verified");
                    guestFrame.addView(view);
                    viewCreated = view.getParent() == guestFrame;
                    if (!viewCreated) throw new IllegalStateException("demo View attachment failed");
                    captureGuest();
                    if (clicks != 0) throw new SecurityException("new demo session did not start at zero");
                    stage = "DEMO_VIEW_RUNNING";
                    event("GUEST View created from verified external demo DEX/resources");
                } catch (Throwable uiFailure) {
                    fail(stage, uiFailure);
                    cleanupGuest();
                }
                updateReport();
            });
        }, "twt-v31-trusted-demo-import").start();
    }

    private void event(String text) {
        if (events.length() < 14000) events.append(text).append('\n');
    }

    private void forward(String name) {
        if (destroyed) return;
        event("HOST " + name);
        if (guest != null && lifecycleMethod != null) dispatchLifecycle(name);
        else if (pendingLifecycle.size() < 128) pendingLifecycle.add(name);
        else {
            lifecycleVerified = false;
            fail("LIFECYCLE_QUEUE_LIMIT", new IllegalStateException(
                    "too many pending callbacks; leave and reopen this proxy before import"));
        }
        updateReport();
    }

    private void dispatchLifecycle(String name) {
        try {
            Object result = lifecycleMethod.invoke(guest, name);
            if (!(result instanceof String)) throw new IllegalStateException("missing lifecycle evidence");
            event("GUEST " + result);
            if ("onCreate".equals(name)) guestCreated = true;
            if ("onDestroy".equals(name)) guestDestroyed = true;
        } catch (Throwable failure) {
            lifecycleVerified = false;
            fail("LIFECYCLE_" + name, failure);
        }
    }

    private void captureGuest() {
        if (guest == null || clicksMethod == null || snapshotMethod == null) return;
        try {
            clicks = (Integer) clicksMethod.invoke(guest);
            guestSnapshot = String.valueOf(snapshotMethod.invoke(guest));
            if (clicks < 0) throw new IllegalStateException("invalid demo click count");
        } catch (Throwable failure) { fail("DEMO_STATE", failure); }
    }

    private void cleanupGuest() {
        if (cleanupAttempted) return;
        cleanupAttempted = true;
        captureGuest();
        if (guest != null && disposeMethod != null && disposedMethod != null) {
            try {
                disposeMethod.invoke(guest);
                disposed = Boolean.TRUE.equals(disposedMethod.invoke(guest));
                if (!disposed) throw new IllegalStateException("demo did not confirm dispose");
                captureGuest();
                event("GUEST disposed=" + disposed);
            } catch (Throwable failure) { fail("DISPOSE_DEMO", failure); }
        }
        try {
            if (guestFrame != null) guestFrame.removeAllViews();
            viewDetached = guestFrame != null && guestFrame.getChildCount() == 0;
            event("HOST guest View detached=" + viewDetached);
        } catch (RuntimeException failure) { fail("DETACH_DEMO_VIEW", failure); }
        if (resourceScope != null) {
            try {
                resourceScope.close();
                resourcesClosed = true;
                event("HOST guest resource scope closed=true");
            } catch (RuntimeException failure) { fail("CLOSE_RESOURCES", failure); }
            resourceScope = null;
        }
        if (payload != null) {
            try {
                payload.close();
                apkDeleted = payload.isApkDeleted();
                event("HOST private APK deleted=" + apkDeleted);
                if (!apkDeleted) throw new IllegalStateException("private demo APK still exists");
            } catch (RuntimeException failure) { fail("DELETE_PRIVATE_APK", failure); }
            payload = null;
        }
        guest = null;
        lifecycleMethod = null;
        clicksMethod = null;
        snapshotMethod = null;
        disposeMethod = null;
        disposedMethod = null;
        pendingLifecycle.clear();
    }

    private void fail(String failedStage, Throwable failure) {
        String reason = describe(failure);
        if (error.isEmpty()) error = "FAIL stage=" + failedStage + ": " + reason;
        event("FAIL stage=" + failedStage + ": " + reason);
    }

    private static String describe(Throwable failure) {
        StringBuilder text = new StringBuilder();
        Throwable cursor = failure;
        for (int depth = 0; cursor != null && depth < 4; depth++) {
            if (depth > 0) text.append(" <- ");
            text.append(cursor.getClass().getSimpleName());
            String message = cursor.getMessage();
            if (message != null) text.append(": ").append(message, 0, Math.min(180, message.length()));
            Throwable next = cursor instanceof InvocationTargetException
                    ? ((InvocationTargetException) cursor).getTargetException() : cursor.getCause();
            if (next == cursor) break;
            cursor = next;
        }
        return text.toString();
    }

    private String buildReport() {
        String result = "INCOMPLETE";
        if (!error.isEmpty()) result = "FAIL";
        else if (destroyed && !recreated && viewCreated && resourceVerified && clicks > 0
                && lifecycleVerified && guestCreated && guestDestroyed && disposed
                && viewDetached && resourcesClosed && apkDeleted) {
            result = "PASS";
        }
        return "天外天 / V3.1 外部受控 demo Host 代理 UI\n"
                + "session=" + (owner == null ? "NOT_STARTED" : owner) + '\n'
                + "startedAtWallMs=" + startedAtWallMs + '\n'
                + "Android API=" + Build.VERSION.SDK_INT + "; HOST PID=" + Process.myPid()
                + "; UID=" + Process.myUid() + '\n'
                + "stage=" + stage + "; importPending=" + loading + '\n'
                + "demo APK SHA-256=" + digest + '\n'
                + "resourcePackage=" + resourcePackage + '\n'
                + "guestClassLoader=" + loaderEvidence + '\n'
                + "guestViewCreated=" + viewCreated + "; resourcesVerified=" + resourceVerified + '\n'
                + "resourceEvidence=" + resourceEvidence + '\n'
                + "guestClickCount=" + clicks + "; lifecycleForwarded=" + lifecycleVerified + '\n'
                + "guestOnCreate=" + guestCreated + "; guestOnDestroy=" + guestDestroyed + '\n'
                + "recreatedFailClosed=" + recreated + '\n'
                + "guestDisposed=" + disposed + "; viewDetached=" + viewDetached
                + "; resourcesClosed=" + resourcesClosed + "; privateApkDeleted=" + apkDeleted + '\n'
                + (error.isEmpty() ? "" : error + '\n')
                + "HOST / GUEST timeline:\n" + events
                + "Guest snapshot:\n" + guestSnapshot + '\n'
                + "V3.1 外部受控 demo Host 代理 UI：" + result + '\n'
                + "边界：仅本版精确哈希的独立 demo；宿主 Activity/UID/权限，"
                + "不是系统直接启动未安装 Guest Activity，不是任意 APK 容器或微信定位验证。\n";
    }

    private void updateReport() {
        captureGuest();
        if (status != null) status.setText(!error.isEmpty() ? "本次运行失败，保留阶段与原因"
                : recreated ? "重建后已安全停止；返回 V0 后重新进入并选文件"
                : viewCreated ? "演示 View 已显示；点击计数按钮后完成并退出"
                : loading ? "核验配套 demo APK 中…" : "等待选择演示 APK");
        if (report != null) report.setText(buildReport());
        persist();
    }

    private void persist() {
        if (!attempted || owner == null) return;
        android.content.SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        if (owner.equals(prefs.getString(OWNER, ""))) {
            prefs.edit().putString(LAST_RESULT, buildReport()).apply();
        }
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putBoolean(ATTEMPTED, attempted);
        forward("onSaveInstanceState");
        super.onSaveInstanceState(state);
    }

    @Override protected void onStart() { super.onStart(); forward("onStart"); }
    @Override protected void onResume() { super.onResume(); forward("onResume"); }
    @Override protected void onRestart() { super.onRestart(); forward("onRestart"); }
    @Override protected void onPause() { forward("onPause"); super.onPause(); }
    @Override protected void onStop() { forward("onStop"); super.onStop(); }

    @Override protected void onDestroy() {
        if (confirmation != null) {
            confirmation.dismiss();
            confirmation = null;
        }
        recreated |= isChangingConfigurations();
        forward("onDestroy");
        generation++;
        destroyed = true;
        cleanupGuest();
        stage = recreated ? "RECREATED_FAIL_CLOSED" : loading ? "CLOSED_DURING_IMPORT" : "CLOSED";
        persist();
        super.onDestroy();
    }
}
