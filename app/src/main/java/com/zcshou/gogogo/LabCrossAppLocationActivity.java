package com.zcshou.gogogo;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.SystemClock;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.location.LocationCompat;

import com.google.android.gms.location.LocationServices;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Evidence-only staged study across external location consumers.
 * Never requests active location, modifies mock, stops external apps, or reads
 * another app's private GPS/SDK results. GPS/NETWORK/GMS cache reads only.
 */
public final class LabCrossAppLocationActivity extends AppCompatActivity {
    private static final String PREFS = "cross_app_location_evidence";
    private static final String KEY_LOG = "total_log";
    private static final String KEY_SESSION_AT = "last_session_at";
    private static final int MAX_REPORT = 56000;
    private static final String[] PACKAGES = {
            "com.ss.android.ugc.aweme", "com.autonavi.minimap",
            "com.tencent.mm", "com.tencent.map",
            "com.sankuai.meituan"
    };

    private final StringBuilder total = new StringBuilder();
    private TextView status;
    private TextView reportView;
    private TextView summaryView;
    private LinearLayout advancedPanel;
    private com.google.android.material.button.MaterialButton advancedToggle;
    private com.google.android.material.button.MaterialButton rawLogToggle;
    private com.google.android.material.button.MaterialButton appChoiceButton;
    private boolean advancedExpanded;
    private boolean rawLogExpanded;
    private EditText observationNote;
    private int appIndex = 0;
    private int observationIndex = -1; // Never preselect a user-observed outcome.
    private int meituanVpnIndex = -1; // Multiple network states require human verification.
    private int generation;
    private boolean started;
    private boolean awaitingReturn;
    private boolean leftOurActivity;
    private boolean returnedSnapshot;
    private long sessionStartElapsed;
    private long sessionStartWall;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        total.append(prefs.getString(KEY_LOG, ""));
        if (state != null) {
            advancedExpanded = state.getBoolean("twt.crossapp.advanced", false);
            rawLogExpanded = state.getBoolean("twt.crossapp.raw", false);
        }
        buildUi();
        render("尚未开始 · 选择应用并启动一次对照实验");
    }

    @Override protected void onSaveInstanceState(Bundle outState) {
        outState.putBoolean("twt.crossapp.advanced", advancedExpanded);
        outState.putBoolean("twt.crossapp.raw", rawLogExpanded);
        super.onSaveInstanceState(outState);
    }

    @Override protected void onStop() {
        if (awaitingReturn) leftOurActivity = true;
        persist();
        super.onStop();
    }

    @Override protected void onResume() {
        super.onResume();
        if (awaitingReturn && leftOurActivity && !returnedSnapshot) {
            returnedSnapshot = true;
            awaitingReturn = false;
            append("RETURN_FROM_EXTERNAL_SCREEN at=" + clock() + " elapsed_s="
                    + sinceStartSeconds() + "\nsource=return_to_GoGoGo"
                    + "\nuser_must_confirm_actual_external_app_opened");
            passive("AFTER");
            render("已从外部界面返回并自动拍摄 AFTER 快照，填写观察后保存");
        }
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        GoGoUi.applyScreenBackground(scroll);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int padding = GoGoUi.dp(this, 18);
        root.setPadding(padding, GoGoUi.dp(this, 22), padding, GoGoUi.dp(this, 26));
        scroll.addView(root);

        root.addView(GoGoUi.brandStrip(this), GoGoUi.matchWrap());
        root.addView(GoGoUi.heroTitle(this, "应用定位对比"), GoGoUi.matchWrap());
        root.addView(GoGoUi.subtitle(this,
                "先选应用，再打开观察，回来点一下就能保存结果。"),
                GoGoUi.matchWrap());
        root.addView(GoGoUi.gap(this, 12));

        // Ordinary users have just three steps; expert controls are retained.
        com.google.android.material.card.MaterialCardView quickCard = GoGoUi.card(this);
        LinearLayout quick = GoGoUi.cardContent(this);
        quickCard.addView(quick);
        quick.addView(GoGoUi.sectionTitle(this, "三步完成一次对比"),
                GoGoUi.matchWrap());
        quick.addView(GoGoUi.muted(this,
                "不会修改模拟位置，也不会主动获取新定位。"), GoGoUi.matchWrap());
        quick.addView(GoGoUi.gap(this, 8));
        status = GoGoUi.status(this, "等待开始");
        quick.addView(status, GoGoUi.matchWrap());
        quick.addView(GoGoUi.gap(this, 8));
        appChoiceButton = GoGoUi.secondaryButton(this,
                "① 选择应用 · " + LabCrossAppEvidence.APPLICATIONS[appIndex],
                v -> showAppChoice());
        quick.addView(appChoiceButton, GoGoUi.matchWrap());
        quick.addView(GoGoUi.gap(this, 6));
        quick.addView(GoGoUi.primaryButton(this, "② 开始观察 · 自动记录前后变化",
                v -> startQuickRound()), GoGoUi.matchWrap());
        quick.addView(GoGoUi.gap(this, 6));
        quick.addView(GoGoUi.secondaryButton(this,
                "③ 记录我看到的位置并保存", v -> saveQuickObservation()),
                GoGoUi.matchWrap());
        root.addView(quickCard, GoGoUi.matchWrap());
        root.addView(GoGoUi.gap(this, 12));

        advancedToggle = GoGoUi.secondaryButton(this, "",
                v -> {
                    advancedExpanded = !advancedExpanded;
                    updateDisclosures();
                });
        root.addView(advancedToggle, GoGoUi.matchWrap());
        advancedPanel = new LinearLayout(this);
        advancedPanel.setOrientation(LinearLayout.VERTICAL);
        root.addView(advancedPanel, GoGoUi.matchWrap());


        com.google.android.material.card.MaterialCardView card = GoGoUi.card(this);
        LinearLayout body = GoGoUi.cardContent(this);
        card.addView(body);
        body.addView(GoGoUi.muted(this,
                "执行顺序：启动基准 → 打开目标应用并观察蓝点及附近地点 →"
                        + " 返回自动取 AFTER 快照 → 保存人工结论。"
                        + "不会触发新的定位请求、强停第三方应用或修改模拟坐标。"),
                GoGoUi.matchWrap());
        body.addView(GoGoUi.gap(this, 8));
        body.addView(GoGoUi.secondaryButton(this, "① 选择要对照的应用",
                v -> showAppChoice()), GoGoUi.matchWrap());
        body.addView(GoGoUi.secondaryButton(this, "② 开始本轮并读取 BEFORE 被动快照",
                v -> beginRound()), GoGoUi.matchWrap());
        body.addView(GoGoUi.secondaryButton(this, "③ 打开目标应用 · 观察蓝点 / POI / 地址功能",
                v -> openExternalApp()), GoGoUi.matchWrap());
        body.addView(GoGoUi.secondaryButton(this, "④ 返回后补拍 / 确认 AFTER",
                v -> {
                    if (!started) {
                        Toast.makeText(this, "请先开始本轮实验", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (!returnedSnapshot) {
                        returnedSnapshot = true;
                        awaitingReturn = false;
                        append("MANUAL_AFTER_SHOT at=" + clock()
                                + " elapsed_s=" + sinceStartSeconds()
                                + "\nuser_confirmation_needed=true");
                        passive("AFTER");
                    }
                    render("AFTER 快照已记录；下一步保存人工观察");
                }), GoGoUi.matchWrap());
        body.addView(GoGoUi.gap(this, 9));
        body.addView(GoGoUi.secondaryButton(this, "⑤ 选择界面观察结果（地图/地址）",
                v -> new AlertDialog.Builder(this)
                        .setTitle(appIndex == LabCrossAppEvidence.MEITUAN_INDEX
                                ? "美团：蓝点、城市栏和地址服务" : "蓝点与附近地点")
                        .setSingleChoiceItems(LabCrossAppEvidence.observationOptions(appIndex),
                                observationIndex, (d, idx) -> {
                                    observationIndex = idx;
                                    d.dismiss();
                                    render("观察选项："
                                            + LabCrossAppEvidence.observationOptions(appIndex)[idx]);
                                })
                        .setNegativeButton("取消", (d,w)->{}).show()), GoGoUi.matchWrap());
        body.addView(GoGoUi.secondaryButton(this, "美团专项：VPN 开/关时地址结果（可跳过）",
                v -> {
                    if (appIndex != LabCrossAppEvidence.MEITUAN_INDEX) {
                        Toast.makeText(this, "这一项仅用于美团新增地址实验",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    new AlertDialog.Builder(this)
                            .setTitle("不同 VPN 状态是否真正做过对照？")
                            .setSingleChoiceItems(LabCrossAppEvidence.MEITUAN_VPN_OUTCOMES,
                                    meituanVpnIndex, (d, choice) -> {
                                        meituanVpnIndex = choice;
                                        d.dismiss();
                                        render("VPN 观察："
                                                + LabCrossAppEvidence.vpnComparison(choice));
                                    })
                            .setNegativeButton("取消", (d,w)->{})
                            .show();
                }), GoGoUi.matchWrap());
        observationNote = new EditText(this);
        observationNote.setHint("可选备注：仅填写模糊现象，不要填写真实地址或精确坐标");
        observationNote.setMaxLines(3);
        observationNote.setMinLines(2);
        GoGoUi.styleInput(observationNote);
        body.addView(observationNote, GoGoUi.matchWrap());
        body.addView(GoGoUi.primaryButton(this, "保存本轮观察并追加到总日志",
                v -> finishRound()), GoGoUi.matchWrap());
        advancedPanel.addView(card, GoGoUi.matchWrap());

        root.addView(GoGoUi.gap(this, 12));
        com.google.android.material.card.MaterialCardView reportCard = GoGoUi.card(this);
        LinearLayout inner = GoGoUi.cardContent(this);
        reportCard.addView(inner);
        inner.addView(GoGoUi.sectionTitle(this, "看得懂的实验结果"),
                GoGoUi.matchWrap());
        summaryView = GoGoUi.muted(this, "");
        summaryView.setTextSize(14);
        summaryView.setTextIsSelectable(true);
        inner.addView(summaryView, GoGoUi.matchWrap());
        inner.addView(GoGoUi.gap(this, 9));
        rawLogToggle = GoGoUi.secondaryButton(this, "",
                v -> {
                    rawLogExpanded = !rawLogExpanded;
                    updateDisclosures();
                });
        inner.addView(rawLogToggle, GoGoUi.matchWrap());
        reportView = GoGoUi.muted(this, "");
        reportView.setTextSize(12);
        reportView.setTextIsSelectable(true);
        inner.addView(reportView, GoGoUi.matchWrap());
        inner.addView(GoGoUi.secondaryButton(this, "复制完整记录 · 发给 GPT",
                v -> {
                    ClipboardManager clipboard = (ClipboardManager)
                            getSystemService(CLIPBOARD_SERVICE);
                    if (clipboard != null) clipboard.setPrimaryClip(
                            ClipData.newPlainText("GoGoGo cross-app evidence", total.toString()));
                    Toast.makeText(this, "已复制本地跨应用对照总日志",
                            Toast.LENGTH_SHORT).show();
                }), GoGoUi.matchWrap());
        inner.addView(GoGoUi.secondaryButton(this, "清空之前记录",
                v -> new AlertDialog.Builder(this)
                        .setTitle("确认清空所有历史对照记录？")
                        .setMessage("清空后无法恢复，建议先复制。")
                        .setNegativeButton("取消", (d,w)->{})
                        .setPositiveButton("清空", (d,w)-> {
                            generation++;
                            total.setLength(0);
                            started = false;
                            awaitingReturn = false;
                            returnedSnapshot = false;
                            persist();
                            render("已清空历史对照记录");
                        }).show()), GoGoUi.matchWrap());
        root.addView(reportCard, GoGoUi.matchWrap());
        root.addView(GoGoUi.gap(this, 10));
        root.addView(GoGoUi.muted(this,
                "边界：高德地图 App 被模拟并不能证明抖音使用相同高德定位数据。"
                        + " 本工具看不到任何第三方 App 内部的 SDK 类型、定位缓存和来源；"
                        + " 蓝点与 POI 由用户自主确认，结果只表明关联而非因果。"),
                GoGoUi.matchWrap());
        setContentView(scroll);
        updateDisclosures();
    }

    private void showAppChoice() {
        if (started) {
            Toast.makeText(this, "先记录当前一轮的观察结果，再换应用",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("这次要看哪个应用？")
                .setSingleChoiceItems(LabCrossAppEvidence.APPLICATIONS, appIndex,
                        (dialog, index) -> {
                            appIndex = index;
                            observationIndex = -1;
                            meituanVpnIndex = -1;
                            dialog.dismiss();
                            if (appChoiceButton != null) {
                                appChoiceButton.setText("① 选择应用 · "
                                        + LabCrossAppEvidence.APPLICATIONS[appIndex]);
                            }
                            render("已选择 " + LabCrossAppEvidence.APPLICATIONS[appIndex]
                                    + "，点击第二步开始");
                        }).setNegativeButton("取消", null).show();
    }

    private void startQuickRound() {
        if (started) {
            Toast.makeText(this, "上一轮尚未保存，请先点击第三步",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        beginRound();
        openExternalApp();
    }

    private void saveQuickObservation() {
        if (!started) {
            Toast.makeText(this, "请先点击第二步开始观察",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        if (!returnedSnapshot) {
            Toast.makeText(this, "先打开应用观察位置，再返回本页",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        if (appIndex == LabCrossAppEvidence.MEITUAN_INDEX) {
            showFullObservationDialog();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("刚才看到的位置是？")
                .setItems(new String[]{
                        "蓝点和附近地点都在模拟位置",
                        "蓝点和附近地点都在国内",
                        "其他 / 不确定（查看更多选项）"
                }, (dialog, choice) -> {
                    if (choice == 0 || choice == 1) {
                        observationIndex = choice == 0 ? 1 : 0;
                        finishRound();
                    } else {
                        showFullObservationDialog();
                    }
                }).setNegativeButton("取消", null).show();
    }

    private void showFullObservationDialog() {
        new AlertDialog.Builder(this)
                .setTitle("选择真实观察结果")
                .setItems(LabCrossAppEvidence.observationOptions(appIndex),
                        (dialog, choice) -> {
                            observationIndex = choice;
                            finishRound();
                        })
                .setNegativeButton("取消", null).show();
    }

    private void updateDisclosures() {
        if (advancedPanel != null && advancedToggle != null) {
            advancedPanel.setVisibility(advancedExpanded
                    ? android.view.View.VISIBLE : android.view.View.GONE);
            advancedToggle.setText(advancedExpanded
                    ? "▴ 收起专业实验步骤"
                    : "▾ 专业实验步骤与补拍（按需展开）");
        }
        if (reportView != null && rawLogToggle != null) {
            reportView.setVisibility(rawLogExpanded
                    ? android.view.View.VISIBLE : android.view.View.GONE);
            rawLogToggle.setText(rawLogExpanded
                    ? "▴ 收起英文技术日志" : "▾ 查看完整技术日志（高级）");
        }
    }

    private void beginRound() {
        if (started) {
            Toast.makeText(this, "当前轮未完成，先保存观察记录", Toast.LENGTH_LONG).show();
            return;
        }
        started = true;
        awaitingReturn = false;
        leftOurActivity = false;
        returnedSnapshot = false;
        observationIndex = -1;
        meituanVpnIndex = -1;
        sessionStartWall = System.currentTimeMillis();
        sessionStartElapsed = SystemClock.elapsedRealtime();
        generation++;
        append("\n========== CROSS_APP_ROUND at=" + clock() + " =========="
                + "\ntarget=" + LabCrossAppEvidence.APPLICATIONS[appIndex]
                + "\nmock_status_and_route=NOT_AUTO_VERIFIED"
                + "\noperator_note=KEEP_SAME_MOCK_VPN_WIFI_AND_CELLULAR_CONDITIONS");
        passive("BEFORE");
        render("BEFORE 被动快照已执行；现在打开目标应用，观察蓝点和附近地点");
    }

    private void openExternalApp() {
        if (!started) {
            Toast.makeText(this, "请先拍 BEFORE 基准快照", Toast.LENGTH_SHORT).show();
            return;
        }
        awaitingReturn = true;
        leftOurActivity = false;
        returnedSnapshot = false;
        append("EXTERNAL_APP_LAUNCH_REQUEST target="
                + LabCrossAppEvidence.APPLICATIONS[appIndex]
                + " at=" + clock() + "\nelapsed_s=" + sinceStartSeconds());
        try {
            Intent intent = getPackageManager().getLaunchIntentForPackage(PACKAGES[appIndex]);
            if (intent != null) startActivity(intent);
            else {
                append("launcher_unavailable=TRUE; user_must_open_app_manually");
                Toast.makeText(this, "系统无法直接打开该应用，请手动切换，再返回天外天",
                        Toast.LENGTH_LONG).show();
            }
        } catch (RuntimeException unavailable) {
            append("launcher_error=" + unavailable.getClass().getSimpleName()
                    + "; user_must_open_app_manually");
            Toast.makeText(this, "请手动进入目标应用并返回天外天",
                    Toast.LENGTH_LONG).show();
        }
        persist();
    }

    private void finishRound() {
        if (!started) {
            Toast.makeText(this, "请先开始本轮实验", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!returnedSnapshot) {
            Toast.makeText(this, "需要先打开应用并返回，或明确手动拍 AFTER",
                    Toast.LENGTH_LONG).show();
            return;
        }
        if (observationIndex < 0) {
            Toast.makeText(this, "请先选择你实际观察到的蓝点和 POI 结果",
                    Toast.LENGTH_LONG).show();
            return;
        }
        append("HUMAN_OBSERVATION at=" + clock() + " elapsed_s=" + sinceStartSeconds()
                + "\n" + LabCrossAppEvidence.describe(appIndex, observationIndex,
                leftOurActivity, observationNote.getText().toString())
                + (appIndex == LabCrossAppEvidence.MEITUAN_INDEX
                    ? "\nmeituan_vpn_outcome="
                            + LabCrossAppEvidence.vpnComparison(meituanVpnIndex)
                            + "\nmeituan_address_api_status=NOT_ACCESSIBLE_FROM_OUR_APP"
                            + "\nwarning=MAP_TILES_LOADING_NOT_PROOF_ADDRESS_API_WORKS"
                    : "")
                + "\nCAUTION=cannot_distinguish_cache_sdk_source_or_mock_rejection_without_more_evidence"
                + "\nEND_ROUND");
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putLong(KEY_SESSION_AT, System.currentTimeMillis()).apply();
        started = false;
        awaitingReturn = false;
        observationNote.setText("");
        persist();
        render("已加入总日志；可选择另一个应用继续下一轮");
    }

    public static String getReportSince(Context context, long sinceWallMs) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (sinceWallMs <= 0 || prefs.getLong(KEY_SESSION_AT, 0L) < sinceWallMs) return "";
        String record = prefs.getString(KEY_LOG, "");
        if (record == null || record.length() > MAX_REPORT) return "";
        return record;
    }

    private void passive(String phase) {
        append("PASSIVE_" + phase + " at=" + clock()
                + "\nelapsed_s=" + sinceStartSeconds()
                + "\nreads=getLastKnownLocation_GPS_NETWORK,GMS_getLastLocation_only");
        // An independent read-only transport/telephony snapshot is captured
        // even without location permission, so Ethernet-vs-cellular evidence
        // is not lost when GPS/GMS cannot be read.
        append("NETWORK_RADIO_" + phase + "\n"
                + LabCellularEthernetSnapshot.capture(this));
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED
                && checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            append("PASSIVE_" + phase + " SKIPPED_NO_LOCATION_PERMISSION");
            return;
        }
        final int token = generation;
        LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        for (String provider : new String[]{LocationManager.GPS_PROVIDER,
                LocationManager.NETWORK_PROVIDER}) {
            try {
                Location fix = lm == null ? null : lm.getLastKnownLocation(provider);
                append(phase + " " + fixLine(provider.toUpperCase(Locale.ROOT), fix));
            } catch (Exception unavailable) {
                append(phase + " " + provider + ":UNAVAILABLE "
                        + unavailable.getClass().getSimpleName());
            }
        }
        append(phase + " GMS_LAST=PENDING_ASYNC");
        try {
            LocationServices.getFusedLocationProviderClient(this).getLastLocation()
                    .addOnCompleteListener(task -> {
                        if (token != generation || isDestroyed()) return;
                        String line = !task.isSuccessful() ? "GMS_LAST:UNAVAILABLE "
                                + (task.getException() == null ? "UNKNOWN"
                                : task.getException().getClass().getSimpleName())
                                : fixLine("GMS_LAST", task.getResult());
                        append(phase + " " + line + " at=" + clock());
                    });
        } catch (Exception unavailable) {
            append(phase + " GMS_LAST:UNAVAILABLE "
                    + unavailable.getClass().getSimpleName());
        }
    }

    private String fixLine(String source, Location fix) {
        if (fix == null) return LabAutoDiagnosticPlan.cacheLine(source,false,false,-1,null);
        boolean mock = LocationCompat.isMock(fix);
        long nanos = SystemClock.elapsedRealtimeNanos() - fix.getElapsedRealtimeNanos();
        long age = nanos < 0 ? -1 : nanos/1000000L;
        String distanceBucket = mock ? LabSupportLog.distanceToRequestedTarget(this,
                fix.getLongitude(), fix.getLatitude()) : null;
        return LabAutoDiagnosticPlan.cacheLine(source,true,mock,age,distanceBucket);
    }

    private void append(String data) {
        if (data == null) return;
        String addition = data + "\n";
        if (total.length() + addition.length() > MAX_REPORT) {
            total.delete(0, Math.min(total.length(), MAX_REPORT/2));
            total.insert(0,"[OLDER_EVIDENCE_TRUNCATED]\n");
        }
        if (addition.length() < MAX_REPORT) total.append(addition);
        persist();
        if (reportView != null) reportView.setText(total.toString());
    }

    private void persist() {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putString(KEY_LOG,total.toString()).apply();
    }

    private void render(String message) {
        if (status != null) status.setText(message);
        if (summaryView != null) {
            summaryView.setText(LabCrossAppReadableSummary.summarize(total.toString()));
        }
        if (reportView != null) {
            reportView.setText(total.length() > 0
                    ? total.toString() : "还没有技术日志。");
        }
    }

    private long sinceStartSeconds() {
        return sessionStartElapsed <=0 ? -1 :
                Math.max(0,SystemClock.elapsedRealtime()-sessionStartElapsed)/1000L;
    }

    private static String clock() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.CHINA)
                .format(new Date());
    }

    @Override protected void onDestroy() {
        generation++;
        persist();
        super.onDestroy();
    }
}
