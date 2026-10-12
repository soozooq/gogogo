package com.zcshou.gogogo;

import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.zcshou.service.ServiceGo;

import java.util.Locale;

/**
 * Lab 15 standard Android location compatibility observatory.
 *
 * This screen does not inspect or alter another application's internals. It only reports
 * GoGoGo's own framework submission results and Google Play Services Task outcomes.
 * None of these counters proves another application received or used a location.
 */
public class LocationCompatibilityActivity extends AppCompatActivity {
    private final Handler handler = new Handler(Looper.getMainLooper());

    private TextView summaryView;
    private TextView providerView;
    private TextView performanceView;
    private TextView publishedView;
    private TextView guidanceView;

    private ServiceGo.ServiceGoBinder binder;
    private boolean bound;

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            if (service instanceof ServiceGo.ServiceGoBinder) {
                binder = (ServiceGo.ServiceGoBinder) service;
                bound = true;
                refresh();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            binder = null;
            bound = false;
            refresh();
        }
    };

    private final Runnable refreshTask = new Runnable() {
        @Override
        public void run() {
            refresh();
            handler.postDelayed(this, 500L);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        bindIfRunning();
    }

    private void buildUi() {
        int pad = GoGoUi.dp(this, 18);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        GoGoUi.applyScreenBackground(scroll);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, GoGoUi.dp(this, 16), pad, GoGoUi.dp(this, 24));
        scroll.addView(root);

        LinearLayout appBar = GoGoUi.row(this);
        appBar.addView(
                GoGoUi.textButton(this, "←", v -> finish()),
                new LinearLayout.LayoutParams(
                        GoGoUi.dp(this, 52),
                        ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout titleBlock = new LinearLayout(this);
        titleBlock.setOrientation(LinearLayout.VERTICAL);
        TextView title = GoGoUi.sectionTitle(this, "Location Compatibility");
        title.setTextSize(20);
        title.setPadding(0, 0, 0, 0);
        titleBlock.addView(title, GoGoUi.matchWrap());
        titleBlock.addView(
                GoGoUi.muted(this, "Lab 20 · Provider Reliability + Recovery"),
                GoGoUi.matchWrap());
        appBar.addView(titleBlock, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(appBar, GoGoUi.matchWrap());

        com.google.android.material.card.MaterialCardView summaryCard = GoGoUi.card(this);
        LinearLayout summaryContent = GoGoUi.cardContent(this);
        summaryCard.addView(summaryContent);
        summaryContent.addView(
                GoGoUi.sectionTitle(this, "标准位置链状态（非微信验收）"),
                GoGoUi.matchWrap());
        summaryView = GoGoUi.status(this, "等待 ServiceGo…");
        summaryView.setTextSize(16);
        summaryContent.addView(summaryView, GoGoUi.matchWrap());
        GoGoUi.addCard(root, summaryCard, 16);

        com.google.android.material.card.MaterialCardView providerCard = GoGoUi.card(this);
        LinearLayout providerContent = GoGoUi.cardContent(this);
        providerCard.addView(providerContent);
        providerContent.addView(
                GoGoUi.sectionTitle(this, "Provider 提交 / GMS Task 完成证据"),
                GoGoUi.matchWrap());
        providerView = GoGoUi.muted(this, "暂无数据");
        providerView.setTextIsSelectable(true);
        providerContent.addView(providerView, GoGoUi.matchWrap());
        providerContent.addView(GoGoUi.gap(this, 12), GoGoUi.matchWrap());
        providerContent.addView(GoGoUi.sectionTitle(this,
                "Lab 52 · 循环节奏与 GMS 异步积压"), GoGoUi.matchWrap());
        performanceView = GoGoUi.muted(this, "尚无本次 ServiceGo 性能观测");
        performanceView.setTextIsSelectable(true);
        providerContent.addView(performanceView, GoGoUi.matchWrap());
        GoGoUi.addCard(root, providerCard, 12);

        com.google.android.material.card.MaterialCardView publishedCard = GoGoUi.card(this);
        LinearLayout publishedContent = GoGoUi.cardContent(this);
        publishedCard.addView(publishedContent);
        publishedContent.addView(
                GoGoUi.sectionTitle(this, "当前 Published Sample"),
                GoGoUi.matchWrap());
        publishedView = GoGoUi.muted(this, "暂无数据");
        publishedView.setTextIsSelectable(true);
        publishedContent.addView(publishedView, GoGoUi.matchWrap());
        GoGoUi.addCard(root, publishedCard, 12);

        com.google.android.material.card.MaterialCardView guideCard = GoGoUi.card(this);
        LinearLayout guideContent = GoGoUi.cardContent(this);
        guideCard.addView(guideContent);
        guideContent.addView(
                GoGoUi.sectionTitle(this, "微信 / 小程序兼容说明"),
                GoGoUi.matchWrap());
        guidanceView = GoGoUi.muted(
                this,
                "本页只显示 GoGoGo 的标准定位接口提交与 GMS Task 完成情况。"
                        + "只有 Consumer Matrix 才能验证本应用测试消费者实际收到的 fix；"
                        + "微信是否使用相同来源、是否采用模拟位置，仍需单独实机验证。");
        guideContent.addView(guidanceView, GoGoUi.matchWrap());
        GoGoUi.addCard(root, guideCard, 12);

        setContentView(scroll);
    }

    private void refresh() {
        ServiceGo.ServiceGoBinder b = binder;
        if (b == null) {
            summaryView.setText(ServiceGo.sRunning
                    ? "ServiceGo 正在运行 · 正在重连"
                    : "ServiceGo 未运行");
            providerView.setText("请先开始模拟位置，再观察 provider 健康度。");
            performanceView.setText("ServiceGo 未连接；循环间隔与 Task 积压 UNKNOWN。");
            publishedView.setText("无 Published Sample");
            return;
        }

        long gpsAge = b.getGpsPublishAgeMs();
        long netAge = b.getNetworkPublishAgeMs();
        long fusedAge = b.getFusedProviderPublishAgeMs();
        long gmsAge = b.getGmsFusedDispatchAgeMs();
        long gmsAckAge = b.getGmsFusedTaskSuccessAgeMs();
        LabGmsPublishEvidence.Snapshot gms = b.getGmsPublishEvidenceSnapshot();

        boolean gpsFresh = fresh(gpsAge);
        boolean netFresh = fresh(netAge);
        boolean fusedFresh = fresh(fusedAge);
        // Recent FLP submission is not enough: require an observed successful Task.
        boolean gmsFresh = b.isGmsFusedMockEnabled() && fresh(gmsAckAge);

        int primaryHealthy = 0;
        if (gpsFresh) primaryHealthy++;
        if (netFresh) primaryHealthy++;
        if (gmsFresh || fusedFresh) primaryHealthy++;

        int orphaned = b.getProviderOrphanedCount();

        String status;
        if (orphaned > 0) {
            status = "ORPHANED · " + orphaned + " 条 provider 等待回收";
        } else if (!b.isPolicyPublishing()) {
            status = "PAUSED · Policy 当前停止发布位置";
        } else if (primaryHealthy >= 3) {
            status = "ACTIVE · 3 条标准发布链近期有响应（非消费者验证）";
        } else if (primaryHealthy == 2) {
            status = "PARTIAL · 两条发布链近期有响应（非消费者验证）";
        } else {
            status = "DEGRADED · 发布链响应不足或未经确认";
        }

        summaryView.setText(status);

        providerView.setText(String.format(Locale.US,
                "GPS provider: %s · submit age %s · returned %d · failed %d\\n"
                        + "NETWORK provider: %s · submit age %s · returned %d · failed %d\\n"
                        + "Framework FUSED: %s · submit age %s · returned %d · failed %d\\n"
                        + "GMS FLP mock: %s · last Task OK age %s\\n"
                        + "  Task returned %d · OK %d · failed %d · pending %d\\n"
                        + "  callback unknown %d · sync exception %d · last submit age %s\\n\\n"
                        + "Provider lifecycle: %s\\n"
                        + "Route recovery: %s\\n\\n"
                        + "注：33 ms 是计划 tick 间隔，不是 Task 成功率。"
                        + "即使 GMS Task OK，也不代表微信/第三方消费者使用了这条位置。",
                enabled(b.isGpsProviderEnabled()), age(gpsAge),
                b.getGpsPublishCount(), b.getGpsPublishFailureCount(),
                enabled(b.isNetworkProviderEnabled()), age(netAge),
                b.getNetworkPublishCount(), b.getNetworkPublishFailureCount(),
                enabled(b.isFusedProviderEnabled()), age(fusedAge),
                b.getFusedProviderPublishCount(), b.getFusedProviderPublishFailureCount(),
                b.isGmsFusedMockEnabled() ? "ENABLED" : "UNAVAILABLE",
                age(gmsAckAge),
                gms.submitted, gms.taskSucceeded, gms.taskFailed, gms.pending,
                gms.callbackUnavailable, gms.synchronousFailure, age(gmsAge),
                b.getProviderReliabilitySummary(),
                b.getRouteRecoverySummary()));
        performanceView.setText(b.getPublishPerformanceSnapshot().render());

        publishedView.setText(String.format(Locale.US,
                "publish=%s\n"
                        + "lng=%.7f\n"
                        + "lat=%.7f\n"
                        + "alt=%.1f m\n"
                        + "accuracy=%.1f m\n"
                        + "speed=%.2f m/s\n"
                        + "bearing=%.1f°\n"
                        + "policy=%s",
                b.isPolicyPublishing() ? "YES" : "NO",
                b.getPublishedLongitude(),
                b.getPublishedLatitude(),
                b.getPublishedAltitude(),
                b.getPublishedAccuracyMeters(),
                b.getPublishedSpeedMps(),
                b.getPublishedBearingDegrees(),
                b.getPolicySummary()));

        if (orphaned > 0) {
            guidanceView.setText(
                    "发现无法立即回收的 test provider。Lab 20 会在 mock AppOps 可用后重试清理；"
                            + "如果开发者选项里取消过“模拟位置信息应用”，请重新选择 GoGoGo 后再观察。");
        } else if (primaryHealthy >= 3) {
            guidanceView.setText(
                    "GoGoGo 的标准位置提交近期有响应，且未报告孤儿 Provider。"
                            + "这不能证明 Consumer Matrix 或微信收到了新鲜位置。"
                            + "下一步应检查自有消费者的 fixAge/isMock，并独立对照微信页面。");
        } else if (!gmsFresh && !fusedFresh) {
            guidanceView.setText(
                    "尚无近期 FUSED Task 成功或 Framework FUSED 提交。"
                            + "请区分 GMS Task pending/failed 和 Framework Provider 提交状态；"
                            + "不要把提交计数作为微信成功的证明。");
        } else {
            guidanceView.setText(
                    "有标准 provider 的发布 age 偏大或不可用。"
                            + "继续保持本页打开几秒，确认问题是持续状态还是刚启动时的瞬态。");
        }
    }

    private static boolean fresh(long ageMs) {
        return ageMs >= 0L && ageMs <= 500L;
    }

    private static String enabled(boolean enabled) {
        return enabled ? "ENABLED" : "UNAVAILABLE";
    }

    private static String age(long ageMs) {
        return ageMs < 0L ? "N/A" : ageMs + " ms";
    }

    private void bindIfRunning() {
        if (bound || !ServiceGo.sRunning) return;
        try {
            bindService(new Intent(this, ServiceGo.class), connection, 0);
        } catch (Throwable ignored) {
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        bindIfRunning();
        handler.removeCallbacks(refreshTask);
        handler.post(refreshTask);
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(refreshTask);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(refreshTask);
        if (bound) {
            try {
                unbindService(connection);
            } catch (Throwable ignored) {
            }
        }
        bound = false;
        binder = null;
        super.onDestroy();
    }
}
