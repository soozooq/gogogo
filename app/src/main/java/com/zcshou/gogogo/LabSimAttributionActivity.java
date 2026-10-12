package com.zcshou.gogogo;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.InputFilter;
import android.text.InputType;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import cn.lalaki.phone.location.PhoneLocation;
import cn.lalaki.phone.location.PhoneLocation.PhoneInfo;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Privacy-first SIM/network operator and China mobile segment-registration lookup.
 * No phone number auto-read; no requests for SMS, call logs, contacts or location;
 * no raw ICCID, IMSI, MSISDN or phone number storage/logging.
 */
public final class LabSimAttributionActivity extends AppCompatActivity {
    private TextView simReport;
    private TextView segmentResult;
    private EditText segmentInput;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private int generation;

    private final ActivityResultLauncher<String> permission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(),
                    granted -> {
                        refreshSimReport();
                        if (!granted) Toast.makeText(this,
                                "未授权：仍可查看基础运营商信息、手动查号段",
                                Toast.LENGTH_LONG).show();
                    });

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        buildUi();
        refreshSimReport();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        GoGoUi.applyScreenBackground(scroll);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = GoGoUi.dp(this, 18);
        root.setPadding(pad, GoGoUi.dp(this, 22), pad, GoGoUi.dp(this, 32));
        scroll.addView(root);

        root.addView(GoGoUi.eyebrow(this, "TELEPHONY  /  SIM"), GoGoUi.matchWrap());
        root.addView(GoGoUi.gap(this, 8));
        root.addView(GoGoUi.heroTitle(this, "手机卡归属地"), GoGoUi.matchWrap());
        root.addView(GoGoUi.subtitle(this,
                "自动读取 SIM/网络所属国家和运营商 · 中国号码段开户省市离线查询"),
                GoGoUi.matchWrap());
        root.addView(GoGoUi.gap(this, 16));

        com.google.android.material.card.MaterialCardView simCard = GoGoUi.card(this);
        LinearLayout simContent = GoGoUi.cardContent(this);
        simCard.addView(simContent);
        simContent.addView(GoGoUi.sectionTitle(this, "本机 SIM 卡信息"),
                GoGoUi.matchWrap());
        simContent.addView(GoGoUi.muted(this,
                "区分 SIM 发卡国家、运营商和当前接入网络。"
                        + "VPN 不会改变 SIM 的发卡地区；基站漫游也不代表号码归属地改变。"),
                GoGoUi.matchWrap());
        simReport = GoGoUi.muted(this, "等待本机读取…");
        simReport.setTextIsSelectable(true);
        simContent.addView(GoGoUi.gap(this, 8));
        simContent.addView(simReport, GoGoUi.matchWrap());
        simContent.addView(GoGoUi.gap(this, 10));
        simContent.addView(GoGoUi.secondaryButton(this, "刷新 SIM / 运营商信息",
                v -> refreshSimReport()), GoGoUi.matchWrap());
        simContent.addView(GoGoUi.secondaryButton(this, "授权查看双卡 / eSIM 详情",
                v -> {
                    if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE)
                            == PackageManager.PERMISSION_GRANTED) {
                        refreshSimReport();
                    } else {
                        permission.launch(Manifest.permission.READ_PHONE_STATE);
                    }
                }), GoGoUi.matchWrap());
        root.addView(simCard, GoGoUi.matchWrap());

        root.addView(GoGoUi.gap(this, 16));
        com.google.android.material.card.MaterialCardView segmentCard = GoGoUi.card(this);
        LinearLayout segmentContent = GoGoUi.cardContent(this);
        segmentCard.addView(segmentContent);
        segmentContent.addView(GoGoUi.sectionTitle(this, "中国手机号开户归属地"),
                GoGoUi.matchWrap());
        segmentContent.addView(GoGoUi.muted(this,
                "仅输入手机号前 7 位，例如 1380013。查询本地打包的公开号段数据库，"
                        + "不必填写完整 11 位号码，不读取电话本，也不将号码上传到网站。"
                        + "旧号段及携号转网可能导致结果过时。"),
                GoGoUi.matchWrap());
        segmentInput = new EditText(this);
        segmentInput.setHint("手机号前 7 位");
        segmentInput.setSingleLine(true);
        segmentInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        segmentInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(7)});
        GoGoUi.styleInput(segmentInput);
        segmentContent.addView(GoGoUi.gap(this, 8));
        segmentContent.addView(segmentInput, GoGoUi.matchWrap());
        segmentContent.addView(GoGoUi.gap(this, 8));
        segmentContent.addView(GoGoUi.primaryButton(this, "离线查询省份与城市",
                v -> querySegment()), GoGoUi.matchWrap());
        segmentResult = GoGoUi.muted(this, "尚未查询 · 数据不会写入日志");
        segmentResult.setTextIsSelectable(true);
        segmentContent.addView(GoGoUi.gap(this, 10));
        segmentContent.addView(segmentResult, GoGoUi.matchWrap());
        root.addView(segmentCard, GoGoUi.matchWrap());

        root.addView(GoGoUi.gap(this, 16));
        TextView footer = GoGoUi.muted(this,
                "重要：SIM 国家 / 号段开户城市 / VPN 出口城市 / 手机 GPS 位置是不同信息源。"
                        + "本功能不会改变系统定位、VPN、微信或时区。");
        footer.setGravity(Gravity.CENTER);
        root.addView(footer, GoGoUi.matchWrap());
        setContentView(scroll);
    }

    private void refreshSimReport() {
        if (simReport == null) return;
        StringBuilder report = new StringBuilder();
        TelephonyManager tm = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        if (tm == null) {
            simReport.setText("设备未提供电话服务；可能是 Wi-Fi 平板或无蜂窝模块。");
            return;
        }

        report.append("SIM 发卡国家：")
                .append(LabSimAttribution.country(value(() -> tm.getSimCountryIso())))
                .append("\n");
        report.append("SIM 运营商：").append(safe(value(() -> tm.getSimOperatorName())))
                .append("\n");
        report.append("SIM MCC/MNC：").append(safe(value(() -> tm.getSimOperator())))
                .append("\n");
        report.append("当前蜂窝网络国家：")
                .append(LabSimAttribution.country(value(() -> tm.getNetworkCountryIso())))
                .append("\n");
        report.append("当前蜂窝网络运营商：")
                .append(safe(value(() -> tm.getNetworkOperatorName())))
                .append("\n");
        report.append("当前网络 MCC/MNC：")
                .append(safe(value(() -> tm.getNetworkOperator())))
                .append("\n");
        try {
            report.append("蜂窝漫游：").append(tm.isNetworkRoaming() ? "是" : "否")
                    .append("\n");
        } catch (SecurityException unavailable) {
            report.append("蜂窝漫游：不可读取\n");
        }

        if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE)
                != PackageManager.PERMISSION_GRANTED) {
            report.append("\n双卡 / eSIM 详情：未授权（可选择授权，非必需）");
            simReport.setText(report.toString());
            return;
        }

        try {
            SubscriptionManager subscriptions = (SubscriptionManager) getSystemService(
                    Context.TELEPHONY_SUBSCRIPTION_SERVICE);
            List<SubscriptionInfo> active = subscriptions == null
                    ? null : subscriptions.getActiveSubscriptionInfoList();
            if (active == null || active.isEmpty()) {
                report.append("\n未发现可读取的活跃卡槽；可能无 SIM、eSIM 未启用或系统限制。");
            } else {
                report.append("\n活跃 SIM / eSIM 数量：").append(active.size()).append("\n");
                for (SubscriptionInfo info : active) {
                    int slot = info.getSimSlotIndex();
                    report.append("\n卡槽 ").append(slot < 0 ? "未知" : (slot + 1))
                            .append("：")
                            .append(safe(info.getCarrierName() == null
                                    ? "" : info.getCarrierName().toString()))
                            .append("\n  发卡国家：")
                            .append(LabSimAttribution.country(info.getCountryIso()))
                            .append("\n  MCC/MNC：")
                            .append(info.getMccString() == null ? "未知" : info.getMccString())
                            .append("/")
                            .append(info.getMncString() == null ? "未知" : info.getMncString())
                            .append("\n");
                }
            }
        } catch (SecurityException denied) {
            report.append("\n卡槽详情：系统拒绝读取权限");
        } catch (RuntimeException unavailable) {
            report.append("\n卡槽详情：系统返回不可用");
        }
        simReport.setText(report.toString());
    }

    private interface Query { String get(); }
    private static String value(Query query) {
        try { return query.get(); } catch (RuntimeException unavailable) { return ""; }
    }
    private static String safe(String input) {
        if (input == null || input.trim().isEmpty()) return "未知";
        String trimmed = input.trim();
        return trimmed.length() > 85 ? trimmed.substring(0, 85) : trimmed;
    }

    private void querySegment() {
        final String segment = segmentInput.getText().toString().trim();
        if (!LabSimAttribution.validSegment(segment)) {
            segmentResult.setText("请输入中国大陆手机号有效的前 7 位（以 1 开头，第二位为 3-9）。");
            return;
        }
        final int token = ++generation;
        segmentResult.setText("本机离线数据库正在查询…");
        worker.execute(() -> {
            String output;
            try {
                PhoneLocation.setTempDirectory(getCacheDir());
                PhoneInfo info = PhoneLocation.queryPhoneInfo(segment);
                if (info == null) {
                    output = "数据库未收录该号码段，不能推断开户城市。";
                } else {
                    output = LabSimAttribution.displaySegmentResult(
                            info.province(), info.city(), info.carrier(),
                            String.valueOf(info.version()));
                }
            } catch (Throwable problem) {
                output = "本地数据库暂不可用：" + problem.getClass().getSimpleName()
                        + "。没有联网查询，也没有上传号码。";
            }
            final String result = output;
            runOnUiThread(() -> {
                if (token != generation || isFinishing() || isDestroyed()) return;
                segmentResult.setText(result);
            });
        });
    }

    @Override protected void onDestroy() {
        generation++;
        worker.shutdownNow();
        if (segmentInput != null) segmentInput.setText("");
        super.onDestroy();
    }
}
