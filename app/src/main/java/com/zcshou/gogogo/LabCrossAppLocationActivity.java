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
        buildUi();
        render("尚未开始 · 选择应用并启动一次对照实验");
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

        root.addView(GoGoUi.eyebrow(this, "LOCATION  /  EVIDENCE STUDY"),
                GoGoUi.matchWrap());
        root.addView(GoGoUi.heroTitle(this, "跨应用定位来源对照"), GoGoUi.matchWrap());
        root.addView(GoGoUi.subtitle(this,
                "高德、抖音、微信、腾讯地图、美团 · 时间戳 + 被动缓存 + 人工观察"),
                GoGoUi.matchWrap());
        root.addView(GoGoUi.gap(this, 12));
        com.google.android.material.card.MaterialCardView card = GoGoUi.card(this);
        LinearLayout body = GoGoUi.cardContent(this);
        card.addView(body);
        body.addView(GoGoUi.muted(this,
                "执行顺序：启动基准 → 打开目标应用并观察蓝点及附近地点 →"
                        + " 返回自动取 AFTER 快照 → 保存人工结论。"
                        + "不会触发新的定位请求、强停第三方应用或修改模拟坐标。"),
                GoGoUi.matchWrap());
        body.addView(GoGoUi.gap(this, 8));
        status = GoGoUi.status(this, "等待启动");
        body.addView(status, GoGoUi.matchWrap());

        body.addView(GoGoUi.gap(this, 8));
        body.addView(GoGoUi.secondaryButton(this, "① 选择要对照的应用",
                v -> new AlertDialog.Builder(this)
                        .setTitle("本轮观察哪一个应用？")
                        .setSingleChoiceItems(LabCrossAppEvidence.APPLICATIONS, appIndex,
                                (d, index) -> {
                                    appIndex = index;
                                    observationIndex = -1;
                                    meituanVpnIndex = -1;
                                    d.dismiss();
                                    render("已选择：" + LabCrossAppEvidence.APPLICATIONS[appIndex]);
                                })
                        .setNegativeButton("取消", (d, w) -> {}).show()), GoGoUi.matchWrap());
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
        root.addView(card, GoGoUi.matchWrap());

        root.addView(GoGoUi.gap(this, 12));
        com.google.android.material.card.MaterialCardView reportCard = GoGoUi.card(this);
        LinearLayout inner = GoGoUi.cardContent(this);
        reportCard.addView(inner);
        inner.addView(GoGoUi.sectionTitle(this, "多应用总日志 · 脱敏"),
                GoGoUi.matchWrap());
        reportView = GoGoUi.muted(this, "");
        reportView.setTextIsSelectable(true);
        inner.addView(reportView, GoGoUi.matchWrap());
        inner.addView(GoGoUi.secondaryButton(this, "复制全部对照记录",
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
                Toast.makeText(this, "系统无法直接打开该应用，请手动切换，再返回 GoGoGo",
                        Toast.LENGTH_LONG).show();
            }
        } catch (RuntimeException unavailable) {
            append("launcher_error=" + unavailable.getClass().getSimpleName()
                    + "; user_must_open_app_manually");
            Toast.makeText(this, "请手动进入目标应用并返回 GoGoGo",
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
        if(status!=null)status.setText(message);
        if(reportView!=null)reportView.setText(total.length()>0
                ?total.toString():"还没有诊断记录。");
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
