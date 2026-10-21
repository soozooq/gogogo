package com.zcshou.gogogo;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
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
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.zcshou.gogogo.virtualv0.ContainerV31GuestResources;
import com.zcshou.gogogo.virtualv0.ContainerV31TrustedDemo;
import com.zcshou.gogogo.virtualv0.ContainerV32SessionPolicy;

import org.json.JSONArray;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;

/** Private HOST runtime for the exact-hash, explicitly imported developer demo. */
public final class VirtualContainerV31ProxyActivity extends AppCompatActivity {
    public static final String PREFS = "twt_v31_proxy";
    public static final String LAST_RESULT = "last_result";
    public static final String RECENT_RESULTS = "recent_results";
    private static final String OWNER = "report_owner";
    private static final String ACTIVE = "twt_runtime_active";
    private static final String GUEST_STATE = "twt_runtime_guest_state";
    private static final String STATE_DIGEST = "saved_apk_sha256";
    private static final String SESSION_ID = "twt_runtime_session";
    private static final int PICK_DEMO = 3101;

    private final StringBuilder events = new StringBuilder();
    // These are actual observed HOST callbacks, replayed only when a new trusted
    // entry is constructed. No synthetic Android Activity callback is invented.
    private final ArrayList<String> pendingLifecycle = new ArrayList<>();
    private FrameLayout guestFrame;
    private TextView status;
    private TextView report;
    private LinearLayout advancedPanel;
    private MaterialButton primaryButton;
    private MaterialButton endButton;
    private AlertDialog confirmation;
    private ContainerV31TrustedDemo payload;
    private ContainerV31GuestResources resourceScope;
    private Object guest;
    private Method lifecycleMethod;
    private Method clicksMethod;
    private Method interactionMethod;
    private Method snapshotMethod;
    private Method saveStateMethod;
    private Method backMethod;
    private Method disposeMethod;
    private Method disposedMethod;
    private boolean attempted;
    private boolean loading;
    private boolean destroyed;
    private boolean finalized;
    private boolean copyOnClose;
    private boolean viewCreated;
    private boolean resourceVerified;
    private boolean lifecycleVerified = true;
    private boolean replayHistoryComplete = true;
    private boolean importedAvailable;
    private boolean guestCreated;
    private boolean guestDestroyed;
    private boolean disposed;
    private boolean viewDetached;
    private boolean resourcesClosed;
    private boolean apkDeleted;
    private boolean cleanupAttempted;
    private boolean recoveryRequested;
    private boolean stateRestored;
    private boolean stateSaved;
    private boolean advancedExpanded;
    private boolean recorded;
    private int generation;
    private int clicks;
    private int baselineClicks;
    private int newUserActions;
    private String owner;
    private String session;
    private String resumedFromSession = "NONE";
    private long startedAtWallMs;
    private String digest = "NOT_LOADED";
    private String resourcePackage = "NOT_LOADED";
    private String loaderEvidence = "NOT_LOADED";
    private String resourceEvidence = "NOT_LOADED";
    private String guestSnapshot = "NOT_LOADED";
    private String error = "";
    private String stage = "READY";

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        advancedExpanded = state != null && Boolean.TRUE.equals(state.get("advanced_expanded"));
        buildUi();
        forward("onCreate");
        if (state != null && Boolean.TRUE.equals(state.get(ACTIVE))) {
            recoveryRequested = true;
            // Saved instance state is a request to resume, never an execution
            // authorization. The fixed private import and current build pin must
            // still be present, and openImported revalidates all APK bytes.
            try {
                Bundle previous = checkedState(state.getBundle(GUEST_STATE));
                if (!ContainerV31TrustedDemo.getExpectedSha256().equals(state.get(STATE_DIGEST))
                        || !ContainerV31TrustedDemo.hasImportedDemo(this)) {
                    throw new SecurityException("saved session has no matching current import authorization");
                }
                Object previousSession = state.get(SESSION_ID);
                if (previousSession instanceof String
                        && ((String) previousSession).matches("[0-9a-f-]{36}")) {
                    resumedFromSession = (String) previousSession;
                }
                openDemo(null, previous, true);
            } catch (RuntimeException failure) {
                attempted = true;
                claimReport();
                fail("RESTORE_REQUIRES_EXPLICIT_OPEN", failure);
                stage = "RESTORE_DENIED";
                updateReport();
            }
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
        GoGoUi.addLabHeader(root, "TIANWAITIAN / APPS", "应用运行",
                "导入一次，再次打开会继续上次的内容", v -> finish());
        status = GoGoUi.muted(this, "准备打开应用");
        root.addView(status, GoGoUi.matchWrap());
        primaryButton = GoGoUi.primaryButton(this, "导入并打开演示应用", v -> {
            if (loading || guest != null) return;
            if (ContainerV31TrustedDemo.hasImportedDemo(this)) {
                openDemo(null, readStoredState(), false);
            } else chooseDemo();
        });
        root.addView(primaryButton, GoGoUi.matchWrap());
        guestFrame = new FrameLayout(this);
        root.addView(guestFrame, GoGoUi.matchWrap());
        endButton = GoGoUi.primaryButton(this, "结束并复制记录", v -> {
            copyOnClose = true;
            finish();
        });
        root.addView(endButton, GoGoUi.matchWrap());
        root.addView(GoGoUi.muted(this,
                "当前可运行本版配套演示文件。导入文件不会安装到手机，其他 APK 保持只读。"),
                GoGoUi.matchWrap());
        MaterialButton advancedToggle = GoGoUi.secondaryButton(this, "高级选项与记录", v -> {
            advancedExpanded = !advancedExpanded;
            advancedPanel.setVisibility(advancedExpanded ? View.VISIBLE : View.GONE);
            updateReport();
        });
        root.addView(advancedToggle, GoGoUi.matchWrap());
        advancedPanel = new LinearLayout(this);
        advancedPanel.setOrientation(LinearLayout.VERTICAL);
        advancedPanel.setVisibility(advancedExpanded ? View.VISIBLE : View.GONE);
        root.addView(advancedPanel, GoGoUi.matchWrap());
        advancedPanel.addView(GoGoUi.secondaryButton(this, "重新导入配套文件", v -> {
            if (loading || guest != null) {
                Toast.makeText(this, "先结束当前应用，再重新导入", Toast.LENGTH_SHORT).show();
            } else chooseDemo();
        }), GoGoUi.matchWrap());
        advancedPanel.addView(GoGoUi.secondaryButton(this, "重新创建当前界面", v -> {
            if (guest != null && !loading) recreate();
        }), GoGoUi.matchWrap());
        advancedPanel.addView(GoGoUi.secondaryButton(this, "打开系统设置", v -> {
            if (guest == null) return;
            try { startActivity(new Intent(Settings.ACTION_SETTINGS)); }
            catch (RuntimeException failure) { fail("OPEN_SETTINGS", failure); updateReport(); }
        }), GoGoUi.matchWrap());
        advancedPanel.addView(GoGoUi.secondaryButton(this, "移除已导入文件与保存内容", v -> forgetDemo()),
                GoGoUi.matchWrap());
        report = GoGoUi.reportPanel(this);
        advancedPanel.addView(report, GoGoUi.matchWrap());
        setContentView(scroll);
        updateReport();
    }

    private void chooseDemo() {
        if (loading || guest != null || destroyed) return;
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
                || data.getData() == null || loading || guest != null || destroyed) return;
        final Uri selected = data.getData();
        confirmation = new AlertDialog.Builder(this)
                .setTitle("导入并打开配套演示应用？")
                .setMessage("核验后保留这份配套文件，之后可直接打开并继续上次内容。其他 APK 不会运行。")
                .setNegativeButton("取消", null)
                .setPositiveButton("导入并打开", (dialog, which) -> openDemo(selected, null, false))
                .show();
    }

    private void claimReport() {
        owner = UUID.randomUUID().toString();
        session = UUID.randomUUID().toString();
        startedAtWallMs = System.currentTimeMillis();
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(OWNER, owner).apply();
    }

    private void openDemo(Uri selected, Bundle previous, boolean automaticRecovery) {
        if (loading || guest != null || destroyed) return;
        if (attempted && !error.isEmpty()) rememberCompletedReport();
        resetAttempt();
        claimReport();
        attempted = true;
        recoveryRequested = automaticRecovery;
        if (!automaticRecovery) resumedFromSession = "NONE";
        if (!lifecycleVerified || !replayHistoryComplete) {
            fail("LIFECYCLE_QUEUE_REJECTED", new IllegalStateException("reopen this page before retrying"));
            updateReport();
            return;
        }
        final Bundle restored;
        try { restored = previous == null ? null : checkedState(previous); }
        catch (RuntimeException failure) { fail("INVALID_SAVED_STATE", failure); updateReport(); return; }
        baselineClicks = restored == null ? 0 : restored.getInt("clicks");
        loading = true;
        stage = selected == null ? "REVALIDATING_IMPORTED_DEMO" : "VERIFYING_EXPLICIT_DOCUMENT";
        final int request = ++generation;
        final Context app = getApplicationContext();
        updateReport();
        new Thread(() -> {
            ContainerV31TrustedDemo candidate = null;
            Throwable failure = null;
            try {
                candidate = selected == null ? ContainerV31TrustedDemo.openImported(app)
                        : ContainerV31TrustedDemo.open(app, selected);
            } catch (Throwable caught) { failure = caught; }
            final ContainerV31TrustedDemo ready = candidate;
            final Throwable caught = failure;
            runOnUiThread(() -> {
                if (destroyed || isFinishing() || isDestroyed() || generation != request) {
                    if (ready != null) {
                        try { ready.close(); apkDeleted = ready.isApkDeleted(); }
                        catch (RuntimeException cleanup) { fail("LATE_IMPORT_CLEANUP", cleanup); }
                    }
                    loading = false;
                    persist();
                    if (finalized) {
                        recorded = false;
                        rememberCompletedReport();
                    }
                    return;
                }
                loading = false;
                if (caught != null) { fail("TRUSTED_IMPORT", caught); updateReport(); return; }
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
                    stage = "RESTORE_DEMO_SESSION";
                    guest = entry.getConstructor(Context.class, Resources.class, int.class)
                            .newInstance(this, resources, 0);
                    lifecycleMethod = entry.getMethod("onHostLifecycle", String.class);
                    clicksMethod = entry.getMethod("getClickCount");
                    interactionMethod = entry.getMethod("getInteractionCount");
                    snapshotMethod = entry.getMethod("snapshot");
                    saveStateMethod = entry.getMethod("saveState");
                    backMethod = entry.getMethod("handleBack");
                    disposeMethod = entry.getMethod("dispose");
                    disposedMethod = entry.getMethod("isDisposed");
                    if (restored != null) {
                        entry.getMethod("restoreState", Bundle.class).invoke(guest, new Bundle(restored));
                        stateRestored = sameState(restored, checkedState((Bundle) saveStateMethod.invoke(guest)));
                        if (!stateRestored) throw new SecurityException("restored demo state mismatch");
                    }
                    for (String name : pendingLifecycle) dispatchLifecycle(name);
                    stage = "CREATE_DEMO_VIEW";
                    Object result = entry.getMethod("createView").invoke(guest);
                    if (!(result instanceof View)) throw new SecurityException("demo returned no View");
                    View view = (View) result;
                    if (view.getParent() != null) throw new SecurityException("demo View already attached");
                    resourceVerified = Boolean.TRUE.equals(entry.getMethod("resourcesVerified").invoke(guest));
                    resourceEvidence = String.valueOf(entry.getMethod("getResourceEvidence").invoke(guest));
                    if (!resourceVerified) throw new SecurityException("demo resources not verified");
                    guestFrame.addView(view);
                    viewCreated = view.getParent() == guestFrame;
                    if (!viewCreated) throw new IllegalStateException("demo View attachment failed");
                    captureGuest();
                    if (clicks != baselineClicks) throw new SecurityException("initial/restored click count mismatch");
                    if (newUserActions != 0) throw new SecurityException("saved state fabricated new user actions");
                    stage = "DEMO_VIEW_RUNNING";
                    event("GUEST View created; stateRestored=" + stateRestored
                            + "; baselineClicks=" + baselineClicks + "; importRevalidated=true");
                    saveRuntimeState();
                } catch (Throwable uiFailure) { fail(stage, uiFailure); cleanupGuest(); }
                updateReport();
            });
        }, "twt-runtime-open-demo").start();
    }

    private void resetAttempt() {
        cleanupAttempted = false;
        finalized = false;
        recorded = false;
        stateRestored = false;
        stateSaved = false;
        viewCreated = resourceVerified = guestCreated = guestDestroyed = false;
        disposed = viewDetached = resourcesClosed = apkDeleted = false;
        error = "";
        digest = resourcePackage = loaderEvidence = resourceEvidence = guestSnapshot = "NOT_LOADED";
        clicks = baselineClicks = newUserActions = 0;
        events.setLength(0);
    }

    private Bundle readStoredState() {
        Map<String, ?> values = getSharedPreferences(PREFS, MODE_PRIVATE).getAll();
        if (!ContainerV31TrustedDemo.getExpectedSha256().equals(values.get(STATE_DIGEST))) return null;
        if (!ContainerV32SessionPolicy.isValid(values.get("state_schema"), values.get("state_clicks"),
                values.get("state_note"), values.get("state_page"))) return null;
        Bundle result = new Bundle();
        result.putInt("schema", (Integer) values.get("state_schema"));
        result.putInt("clicks", (Integer) values.get("state_clicks"));
        result.putString("note", (String) values.get("state_note"));
        result.putString("page", (String) values.get("state_page"));
        return result;
    }

    private static Bundle checkedState(Bundle value) {
        if (value == null || value.size() != 4 || !ContainerV32SessionPolicy.isValid(
                value.get("schema"), value.get("clicks"), value.get("note"), value.get("page"))) {
            throw new IllegalArgumentException("invalid demo primitive state schema");
        }
        Bundle result = new Bundle();
        result.putInt("schema", (Integer) value.get("schema"));
        result.putInt("clicks", (Integer) value.get("clicks"));
        result.putString("note", (String) value.get("note"));
        result.putString("page", (String) value.get("page"));
        return result;
    }

    private static boolean sameState(Bundle left, Bundle right) {
        return left.getInt("schema") == right.getInt("schema")
                && left.getInt("clicks") == right.getInt("clicks")
                && left.getString("note", "").equals(right.getString("note", ""))
                && left.getString("page", "").equals(right.getString("page", ""));
    }

    private Bundle saveRuntimeState() {
        if (guest == null || saveStateMethod == null || !viewCreated) return null;
        try {
            Bundle saved = checkedState((Bundle) saveStateMethod.invoke(guest));
            stateSaved = getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                    .putString(STATE_DIGEST, digest).putInt("state_schema", saved.getInt("schema"))
                    .putInt("state_clicks", saved.getInt("clicks"))
                    .putString("state_note", saved.getString("note"))
                    .putString("state_page", saved.getString("page")).commit();
            if (!stateSaved) throw new IllegalStateException("could not persist demo primitive state");
            return saved;
        } catch (Throwable failure) { fail("SAVE_DEMO_STATE", failure); return null; }
    }

    private void forgetDemo() {
        if (loading || guest != null) {
            Toast.makeText(this, "先结束当前应用，再移除导入文件", Toast.LENGTH_SHORT).show();
            return;
        }
        confirmation = new AlertDialog.Builder(this).setTitle("移除已导入应用和保存内容？")
                .setMessage("下次打开需要重新选择配套文件。已生成的运行记录会保留。")
                .setNegativeButton("取消", null).setPositiveButton("移除", (dialog, which) -> {
                    try {
                        getSharedPreferences(PREFS, MODE_PRIVATE).edit().remove(STATE_DIGEST)
                                .remove("state_schema").remove("state_clicks")
                                .remove("state_note").remove("state_page").apply();
                        ContainerV31TrustedDemo.forgetImportedDemo(this);
                        updateReport();
                    } catch (RuntimeException failure) { fail("REMOVE_IMPORTED_DEMO", failure); updateReport(); }
                }).show();
    }

    private void event(String text) {
        if (events.length() < 14000) events.append(text).append('\n');
    }

    private void forward(String name) {
        if (destroyed) return;
        event("HOST " + name);
        if (pendingLifecycle.size() < 128) pendingLifecycle.add(name);
        else if (replayHistoryComplete) {
            // The active guest still receives each actual callback. Only another
            // entry in this Host instance needs the complete earlier history.
            replayHistoryComplete = false;
            event("HOST replay history full; active guest callbacks continue directly");
        }
        if (guest != null && lifecycleMethod != null) dispatchLifecycle(name);
        updateReport();
    }

    private void dispatchLifecycle(String name) {
        try {
            Object result = lifecycleMethod.invoke(guest, name);
            if (!(result instanceof String)) throw new IllegalStateException("missing lifecycle evidence");
            event("GUEST " + result);
            if ("onCreate".equals(name)) guestCreated = true;
            if ("onDestroy".equals(name)) guestDestroyed = true;
        } catch (Throwable failure) { lifecycleVerified = false; fail("LIFECYCLE_" + name, failure); }
    }

    private void captureGuest() {
        if (guest == null || clicksMethod == null || snapshotMethod == null) return;
        try {
            clicks = (Integer) clicksMethod.invoke(guest);
            if (interactionMethod != null) newUserActions = (Integer) interactionMethod.invoke(guest);
            guestSnapshot = String.valueOf(snapshotMethod.invoke(guest));
            if (clicks < 0 || clicks > 100000 || newUserActions < 0) {
                throw new IllegalStateException("invalid demo interaction counts");
            }
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
            try { resourceScope.close(); resourcesClosed = true; }
            catch (RuntimeException failure) { fail("CLOSE_RESOURCES", failure); }
            resourceScope = null;
        }
        if (payload != null) {
            try {
                payload.close();
                apkDeleted = payload.isApkDeleted();
                if (!apkDeleted) throw new IllegalStateException("private runtime APK still exists");
            } catch (RuntimeException failure) { fail("DELETE_PRIVATE_APK", failure); }
            payload = null;
        }
        guest = null;
        lifecycleMethod = clicksMethod = interactionMethod = snapshotMethod = saveStateMethod = null;
        backMethod = disposeMethod = disposedMethod = null;
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
        else if (finalized && viewCreated && resourceVerified && newUserActions > 0
                && lifecycleVerified && guestCreated && guestDestroyed && disposed
                && viewDetached && resourcesClosed && apkDeleted && stateSaved) result = "PASS";
        return "天外天 / 受控应用运行记录\n"
                + "session=" + (session == null ? "NOT_STARTED" : session) + '\n'
                + "resumedFromSession=" + resumedFromSession + '\n'
                + "startedAtWallMs=" + startedAtWallMs + '\n'
                + "Android API=" + Build.VERSION.SDK_INT + "; HOST PID=" + Process.myPid()
                + "; UID=" + Process.myUid() + '\n'
                + "stage=" + stage + "; importPending=" + loading + '\n'
                + "demo APK SHA-256=" + digest + '\n'
                + "resourcePackage=" + resourcePackage + '\n'
                + "guestClassLoader=" + loaderEvidence + '\n'
                + "guestViewCreated=" + viewCreated + "; resourcesVerified=" + resourceVerified + '\n'
                + "resourceEvidence=" + resourceEvidence + '\n'
                + "baselineClicks=" + baselineClicks + "; guestClickCount=" + clicks
                + "; newClicks=" + Math.max(0, clicks - baselineClicks)
                + "; newUserActions=" + newUserActions + '\n'
                + "recoveryRequested=" + recoveryRequested + "; stateRestored=" + stateRestored
                + "; stateSaved=" + stateSaved + '\n'
                + "lifecycleForwarded=" + lifecycleVerified
                + "; replayHistoryComplete=" + replayHistoryComplete + "; guestOnCreate=" + guestCreated
                + "; guestOnDestroy=" + guestDestroyed + '\n'
                + "guestDisposed=" + disposed + "; viewDetached=" + viewDetached
                + "; resourcesClosed=" + resourcesClosed + "; privateApkDeleted=" + apkDeleted + '\n'
                + (error.isEmpty() ? "" : error + '\n')
                + "HOST / GUEST timeline:\n" + events + "Guest snapshot:\n" + guestSnapshot + '\n'
                + "V3.1 外部受控 demo Host 代理 UI：" + result + '\n'
                + "边界：仅本版精确哈希配对 demo；可信导入源保留以便再次打开，运行副本退出删除。"
                + "共享宿主 Activity/UID/权限，不是任意 APK 容器或微信定位验证。\n";
    }

    private void updateReport() {
        captureGuest();
        if (!loading && !destroyed) importedAvailable = ContainerV31TrustedDemo.hasImportedDemo(this);
        boolean imported = importedAvailable;
        if (status != null) status.setText(!error.isEmpty() ? "本次打开未完成，可重试或展开记录"
                : loading ? "正在检查并打开应用…"
                : viewCreated && guest != null ? stateRestored ? "已恢复上次内容" : "应用已打开"
                : imported ? "已导入，可直接打开并继续上次内容" : "首次使用请选择配套演示文件");
        if (primaryButton != null) {
            primaryButton.setVisibility(guest == null ? View.VISIBLE : View.GONE);
            primaryButton.setEnabled(!loading);
            primaryButton.setText(imported ? "打开已导入应用" : "导入并打开演示应用");
        }
        if (endButton != null) {
            endButton.setVisibility(attempted ? View.VISIBLE : View.GONE);
            // A complete-copy action waits for import verification/cleanup to
            // settle. System Back can still leave an explicitly incomplete run.
            endButton.setEnabled(!loading);
        }
        if (report != null) report.setText(buildReport());
        persist();
    }

    private void persist() {
        if (!attempted || owner == null) return;
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        if (owner.equals(prefs.getString(OWNER, ""))) {
            prefs.edit().putString(LAST_RESULT, buildReport()).apply();
        }
    }

    private void rememberCompletedReport() {
        if (recorded || !attempted || owner == null) return;
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        if (!owner.equals(prefs.getString(OWNER, ""))) return;
        String value = buildReport();
        JSONArray previous;
        try { previous = new JSONArray(prefs.getString(RECENT_RESULTS, "[]")); }
        catch (RuntimeException | org.json.JSONException invalid) { previous = new JSONArray(); }
        JSONArray next = new JSONArray();
        ArrayList<String> kept = new ArrayList<>();
        for (int i = 0; i < previous.length(); i++) {
            String old = previous.optString(i, "");
            if (!old.isEmpty() && old.length() <= 40000
                    && !old.contains("session=" + session + "\n")) kept.add(old);
        }
        for (int i = Math.max(0, kept.size() - 4); i < kept.size(); i++) next.put(kept.get(i));
        next.put(value);
        prefs.edit().putString(RECENT_RESULTS, next.toString()).apply();
        recorded = true;
    }

    public static String collectRecentResults(Context context) {
        StringBuilder text = new StringBuilder();
        try {
            JSONArray values = new JSONArray(context.getSharedPreferences(PREFS, MODE_PRIVATE)
                    .getString(RECENT_RESULTS, "[]"));
            for (int i = Math.max(0, values.length() - 5); i < values.length(); i++) {
                String value = values.optString(i, "");
                if (!value.isEmpty() && value.length() <= 40000) {
                    text.append("\n===== 已结束运行 ").append(i + 1).append(" =====\n").append(value);
                }
            }
        } catch (RuntimeException | org.json.JSONException invalid) {
            text.append("历史运行记录无法读取；本次最新记录另行保留。\n");
        }
        return text.toString();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        Bundle current = saveRuntimeState();
        state.putBoolean(ACTIVE, guest != null && current != null && error.isEmpty());
        if (current != null) state.putBundle(GUEST_STATE, current);
        state.putString(STATE_DIGEST, digest);
        state.putString(SESSION_ID, session);
        state.putBoolean("advanced_expanded", advancedExpanded);
        forward("onSaveInstanceState");
        super.onSaveInstanceState(state);
    }

    @Override public void onBackPressed() {
        if (guest != null && backMethod != null) {
            try {
                if (Boolean.TRUE.equals(backMethod.invoke(guest))) {
                    saveRuntimeState();
                    updateReport();
                    return;
                }
            } catch (Throwable failure) { fail("GUEST_BACK", failure); }
        }
        super.onBackPressed();
    }

    @Override protected void onStart() { super.onStart(); forward("onStart"); }
    @Override protected void onResume() { super.onResume(); forward("onResume"); }
    @Override protected void onRestart() { super.onRestart(); forward("onRestart"); }
    @Override protected void onPause() { saveRuntimeState(); forward("onPause"); super.onPause(); }
    @Override protected void onStop() { forward("onStop"); super.onStop(); }

    @Override protected void onDestroy() {
        if (confirmation != null) { confirmation.dismiss(); confirmation = null; }
        saveRuntimeState();
        forward("onDestroy");
        generation++;
        destroyed = true;
        cleanupGuest();
        finalized = isFinishing() && !isChangingConfigurations();
        stage = isChangingConfigurations() ? "RECREATING_WITH_SAVED_STATE"
                : loading ? "CLOSED_DURING_IMPORT" : "CLOSED";
        persist();
        if (copyOnClose && finalized) {
            try {
                ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                if (clipboard == null) throw new IllegalStateException("clipboard unavailable");
                clipboard.setPrimaryClip(ClipData.newPlainText("Tianwaitian runtime",
                        VirtualContainerV0Activity.collectAllReports(this)));
                Toast.makeText(this, "完整记录已复制，下次可一起发送", Toast.LENGTH_SHORT).show();
            } catch (RuntimeException failure) {
                fail("COPY_COMPLETE_REPORT", failure);
                persist();
            }
        }
        if (finalized || isChangingConfigurations()) rememberCompletedReport();
        super.onDestroy();
    }
}
