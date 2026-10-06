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
 * whether GoGoGo is successfully publishing through the standard GPS, network, framework
 * fused and Google Play Services fused paths that location clients may use.
 */
public class LocationCompatibilityActivity extends AppCompatActivity {
    private final Handler handler = new Handler(Looper.getMainLooper());

    private TextView summaryView;
    private TextView providerView;
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
                GoGoUi.muted(this, "Lab 15 · 标准位置链健康度"),
                GoGoUi.matchWrap());
        appBar.addView(titleBlock, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(appBar, GoGoUi.matchWrap());

        com.google.android.material.card.MaterialCardView summaryCard = GoGoUi.card(this);
        LinearLayout summaryContent = GoGoUi.cardContent(this);
        summaryCard.addView(summaryContent);
        summaryContent.addView(
                GoGoUi.sectionTitle(this, "兼容状态"),
                GoGoUi.matchWrap());
        summaryView = GoGoUi.status(this, "等待 ServiceGo…");
        summaryView.setTextSize(16);
        summaryContent.addView(summaryView, GoGoUi.matchWrap());
        GoGoUi.addCard(root, summaryCard, 16);

        com.google.android.material.card.MaterialCardView providerCard = GoGoUi.card(this);
        LinearLayout providerContent = GoGoUi.cardContent(this);
        providerCard.addView(providerContent);
        providerContent.addView(
                GoGoUi.sectionTitle(this, "Provider 发布链"),
                GoGoUi.matchWrap());
        providerView = GoGoUi.muted(this, "暂无数据");
        providerView.setTextIsSelectable(true);
        providerContent.addView(providerView, GoGoUi.matchWrap());
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
                "这里仅验证 Android 标准位置接口是否持续发布。"
                        + "如果微信使用 GPS、NETWORK、framework fused 或 GMS fused，"
                        + "这些链路越完整，兼容覆盖就越好；如果客户端还有额外平台限制，"
                        + "本页不会尝试绕过，只会把标准链状态显示出来。");
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
            publishedView.setText("无 Published Sample");
            return;
        }

        long gpsAge = b.getGpsPublishAgeMs();
        long netAge = b.getNetworkPublishAgeMs();
        long fusedAge = b.getFusedProviderPublishAgeMs();
        long gmsAge = b.getGmsFusedDispatchAgeMs();

        boolean gpsFresh = fresh(gpsAge);
        boolean netFresh = fresh(netAge);
        boolean fusedFresh = fresh(fusedAge);
        boolean gmsFresh = b.isGmsFusedMockEnabled() && fresh(gmsAge);

        int primaryHealthy = 0;
        if (gpsFresh) primaryHealthy++;
        if (netFresh) primaryHealthy++;
        if (gmsFresh || fusedFresh) primaryHealthy++;

        String status;
        if (!b.isPolicyPublishing()) {
            status = "PAUSED · Policy 当前停止发布位置";
        } else if (primaryHealthy >= 3) {
            status = "GOOD · GPS / NETWORK / FUSED 覆盖完整";
        } else if (primaryHealthy == 2) {
            status = "PARTIAL · 两条主要位置链活跃";
        } else {
            status = "DEGRADED · 标准位置链覆盖不足";
        }

        summaryView.setText(status);

        providerView.setText(String.format(Locale.US,
                "GPS provider: %s · age %s · ok %d · fail %d\n"
                        + "NETWORK provider: %s · age %s · ok %d · fail %d\n"
                        + "Framework FUSED: %s · age %s · ok %d · fail %d\n"
                        + "GMS Fused mock: %s · age %s · dispatch %d\n\n"
                        + "参考：GoGoGo tick ≈ 33 ms。age 长时间明显大于 500 ms 才值得重点排查。",
                enabled(b.isGpsProviderEnabled()), age(gpsAge),
                b.getGpsPublishCount(), b.getGpsPublishFailureCount(),
                enabled(b.isNetworkProviderEnabled()), age(netAge),
                b.getNetworkPublishCount(), b.getNetworkPublishFailureCount(),
                enabled(b.isFusedProviderEnabled()), age(fusedAge),
                b.getFusedProviderPublishCount(), b.getFusedProviderPublishFailureCount(),
                b.isGmsFusedMockEnabled() ? "ENABLED" : "UNAVAILABLE",
                age(gmsAge),
                b.getGmsFusedDispatchCount()));

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

        if (primaryHealthy >= 3) {
            guidanceView.setText(
                    "标准 Android 位置发布链目前完整。"
                            + "这代表 GoGoGo 已经把 GPS / NETWORK / FUSED 兼容面尽量铺满；"
                            + "如果微信仍然拿不到，问题就不再是某一条 provider 没有持续发布。");
        } else if (!gmsFresh && !fusedFresh) {
            guidanceView.setText(
                    "当前 FUSED 路径不完整。部分客户端会优先使用融合定位；"
                            + "先观察 GMS 是否可用，以及 framework FUSED 是否能建立 test provider。");
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
