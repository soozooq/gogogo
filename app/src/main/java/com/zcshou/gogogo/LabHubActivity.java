package com.zcshou.gogogo;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;

/**
 * Compact, read-only gateway to existing tools. Essential checks stay visible;
 * all research screens remain available behind expandable groups.
 * No tap here starts mock publishing or requests Shizuku authorization.
 */
public final class LabHubActivity extends AppCompatActivity {
    private static final String EXPANDED_STATE = "lab_hub_expanded";
    private static final int GROUP_COUNT = 4;
    private static final String PREFLIGHT_EXPANDED_KEY = "lab_hub_preflight_expanded";
    private final boolean[] expanded = new boolean[GROUP_COUNT];
    private TextView readinessTitle;
    private TextView readinessDetail;
    private LabTimedSupportCopy timedSupportCopy;
    private LabExperimentPreflight experimentPreflight;
    private boolean preflightExpanded;
    private View preflightPanel;
    private MaterialButton preflightToggle;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            boolean[] previous = savedInstanceState.getBooleanArray(EXPANDED_STATE);
            if (previous != null) {
                // Migrate the previous three saved groups when the fourth was added.
                System.arraycopy(previous, 0, expanded, 0,
                        Math.min(previous.length, GROUP_COUNT));
            }
            preflightExpanded = savedInstanceState.getBoolean(PREFLIGHT_EXPANDED_KEY, false);
        }
        buildUi();
    }

    @Override protected void onResume() {
        super.onResume();
        refreshReadiness();
        if (experimentPreflight != null && preflightExpanded) experimentPreflight.refresh();
    }

    @Override protected void onDestroy() {
        if (timedSupportCopy != null) timedSupportCopy.close();
        if (experimentPreflight != null) experimentPreflight.close();
        super.onDestroy();
    }

    @Override protected void onSaveInstanceState(Bundle outState) {
        outState.putBooleanArray(EXPANDED_STATE, expanded.clone());
        outState.putBoolean(PREFLIGHT_EXPANDED_KEY, preflightExpanded);
        super.onSaveInstanceState(outState);
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        GoGoUi.applyScreenBackground(scroll);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = GoGoUi.dp(this, 18);
        root.setPadding(pad, pad, pad, GoGoUi.dp(this, 28));
        scroll.addView(root);

        root.addView(GoGoUi.backButton(this, v -> finish()), GoGoUi.matchWrap());
        root.addView(GoGoUi.brandStrip(this), GoGoUi.matchWrap());
        root.addView(GoGoUi.heroTitle(this, "诊断中心"), GoGoUi.matchWrap());
        root.addView(GoGoUi.subtitle(this,
                "先查前置条件，再看真实位置消费者；深入排障和研究工具按需展开。"
                        + "打开这里不会启动模拟。"), GoGoUi.matchWrap());
        root.addView(GoGoUi.gap(this, 10));
        root.addView(GoGoUi.eyebrow(this, "只读总览  ·  快速检查  ·  深入排障"),
                GoGoUi.matchWrap());
        root.addView(GoGoUi.gap(this, 14));

        // One glance at the same read-only prerequisites used by Lab 31.
        com.google.android.material.card.MaterialCardView overview = GoGoUi.card(this);
        LinearLayout overviewBody = GoGoUi.cardContent(this);
        overview.addView(overviewBody);
        overviewBody.addView(GoGoUi.muted(this, "当前设备 · 只读概览"),
                GoGoUi.matchWrap());
        overviewBody.addView(GoGoUi.gap(this, 5));
        readinessTitle = GoGoUi.sectionTitle(this, "正在检查…");
        overviewBody.addView(readinessTitle, GoGoUi.matchWrap());
        readinessDetail = GoGoUi.muted(this, "不验证微信、GMS 缓存或 Provider 清理。");
        overviewBody.addView(readinessDetail, GoGoUi.matchWrap());
        overviewBody.addView(GoGoUi.gap(this, 8));
        overviewBody.addView(GoGoUi.secondaryButton(this, "刷新设备状态",
                v -> refreshReadiness()), GoGoUi.matchWrap());
        root.addView(overview, GoGoUi.matchWrap());

        root.addView(GoGoUi.gap(this, 12));
        root.addView(GoGoUi.sectionTitle(this, "先做这两项"),
                GoGoUi.matchWrap());
        root.addView(GoGoUi.muted(this,
                "只读检查不会启动新的模拟；主动定位观察需要你另外选择。"),
                GoGoUi.matchWrap());
        root.addView(GoGoUi.gap(this, 8));
        root.addView(GoGoUi.navigationTile(this, "基础快检与综合报告",
                "检查授权和运行标记，保存基准或对比异常清理历史",
                v -> open(LabQuickCheckActivity.class)), GoGoUi.matchWrap());
        root.addView(GoGoUi.gap(this, 8));
        root.addView(GoGoUi.navigationTile(this, "被动快照 · 不请求新定位",
                "先看 GPS / NETWORK / GMS 的已有缓存；不主动获取位置",
                v -> {
                    Intent intent = new Intent(this, ConsumerLocationProbeActivity.class);
                    intent.putExtra(ConsumerLocationProbeActivity.EXTRA_PASSIVE_MODE, true);
                    startActivity(intent);
                }), GoGoUi.matchWrap());
        root.addView(GoGoUi.gap(this, 16));
        root.addView(GoGoUi.sectionTitle(this, "需要时再深入"),
                GoGoUi.matchWrap());
        root.addView(GoGoUi.navigationTile(this, "跨来源主动对照 · Consumer Matrix",
                "此工具会主动请求位置回调；不启动模拟，但可能刷新缓存",
                v -> open(ConsumerLocationProbeActivity.class)), GoGoUi.matchWrap());

        root.addView(GoGoUi.gap(this, 10));
        root.addView(GoGoUi.primaryButton(this, "📋 复制最近诊断日志（发给 GPT）",
                v -> LabSupportLog.copy(this, "", "")), GoGoUi.matchWrap());
        timedSupportCopy = new LabTimedSupportCopy(this, () -> new String[] {"", ""});
        root.addView(GoGoUi.gap(this, 10));
        root.addView(timedSupportCopy.view(), GoGoUi.matchWrap());
        root.addView(GoGoUi.gap(this, 20));
        root.addView(GoGoUi.sectionTitle(this, "高级研究工具"),
                GoGoUi.matchWrap());
        root.addView(GoGoUi.muted(this,
                "专项对照、微信实验和兼容研究都还在；默认收起减少干扰。"),
                GoGoUi.matchWrap());
        addGroup(root, 3, "专项实验与对照", new Entry[]{
                new Entry("自动化诊断任务面板",
                        "定时被动缓存观察与诊断报告", LabAutoDiagnosticActivity.class),
                new Entry("跨应用定位来源对照",
                        "第三方应用人工观察与快照汇总", LabCrossAppLocationActivity.class)
        });
        addPreflightSection(root);

        addGroup(root, 0, "恢复与兼容", new Entry[]{
                new Entry("异常退出与恢复取证", "历史 Provider 与 GMS 清理记录",
                        LabRecoveryTriageActivity.class),
                new Entry("实验仪表盘", "系统、权限、Shizuku 与 Provider 详细记录",
                        LabDiagnosticsActivity.class),
                new Entry("腾讯定位探针", "腾讯 GEO / POI 缓存独立测试；需有效 Key",
                        TencentLocationProbeActivity.class),
                new Entry("定位兼容性", "不同通道的定位行为检查",
                        LocationCompatibilityActivity.class),
                new Entry("持续运行观察", "后台服务和消费者的运行记录",
                        CompatibilitySessionActivity.class)
        });
        addGroup(root, 1, "运动与路线", new Entry[]{
                new Entry("场景与回放", "参数、模拟场景与回放实验",
                        ScenarioLabActivity.class),
                new Entry("Motion Audit", "运动质量与历史轨迹分析",
                        MotionAuditActivity.class),
                new Entry("Heading Intelligence", "方向与姿态传感器实验",
                        HeadingLabActivity.class)
        });
        addGroup(root, 2, "系统研究", new Entry[]{
                new Entry("Resource Broker", "资源与系统策略观察",
                        PolicyLabActivity.class),
                new Entry("Sandbox / Work Profile", "沙箱及工作资料测试",
                        SandboxLabActivity.class),
                new Entry("Cross Profile Observatory", "跨资料空间实验",
                        CrossProfileObservatoryActivity.class),
                new Entry("Isolated Capsule", "隔离进程资源实验",
                        IsolatedCapsuleLabActivity.class)
        });

        root.addView(GoGoUi.gap(this, 18));
        root.addView(GoGoUi.muted(this,
                "这是工具导航，不会申请权限、启动或停止模拟。"
                        + "真实第三方位置兼容性必须用设备实测确认。"),
                GoGoUi.matchWrap());
        setContentView(scroll);
    }

    /**
     * The WeChat/Shizuku experiment panel contains specialist controls and
     * potentially slow read-only inspection. Keep it collapsed by default.
     * Opening this section never issues a force-stop or starts mock publishing.
     */
    private void addPreflightSection(LinearLayout root) {
        root.addView(GoGoUi.gap(this, 10));
        preflightToggle = GoGoUi.secondaryButton(this, "", v -> {});
        experimentPreflight = new LabExperimentPreflight(this);
        preflightPanel = experimentPreflight.view();
        setPreflightExpanded(preflightExpanded);
        preflightToggle.setOnClickListener(v -> {
            preflightExpanded = !preflightExpanded;
            setPreflightExpanded(preflightExpanded);
            if (preflightExpanded) experimentPreflight.refresh();
        });
        root.addView(preflightToggle, GoGoUi.matchWrap());
        root.addView(preflightPanel, GoGoUi.matchWrap());
    }

    private void setPreflightExpanded(boolean show) {
        if (preflightPanel == null || preflightToggle == null) return;
        preflightPanel.setVisibility(show ? View.VISIBLE : View.GONE);
        preflightToggle.setText(show ? "▴ 收起微信实验前置检查"
                : "▾ 展开微信实验前置检查");
        preflightToggle.setContentDescription(show
                ? "微信实验前置检查已展开，点击收起"
                : "微信实验前置检查已收起，点击展开");
    }

    private void refreshReadiness() {
        if (readinessTitle == null || readinessDetail == null) return;
        LabHubReadiness.Summary summary =
                LabHubReadiness.summarize(LabQuickCheckReader.read(this));
        readinessTitle.setText(summary.headline);
        readinessDetail.setText(summary.detail);
        GoGoUi.setStatusTone(readinessTitle,
                summary.attention == LabHubReadiness.Attention.NEED_ACTION
                        ? GoGoUi.StatusTone.WARNING
                        : summary.attention == LabHubReadiness.Attention.UNKNOWN
                        ? GoGoUi.StatusTone.INFO : GoGoUi.StatusTone.NEUTRAL);
    }

    private void addGroup(LinearLayout root, int index, String title, Entry[] entries) {
        root.addView(GoGoUi.gap(this, 10));
        LinearLayout contents = new LinearLayout(this);
        contents.setOrientation(LinearLayout.VERTICAL);
        MaterialButton toggle = GoGoUi.secondaryButton(this, "", v -> {});
        setGroupExpanded(toggle, contents, title, entries.length, expanded[index]);
        toggle.setOnClickListener(v -> {
            expanded[index] = !expanded[index];
            setGroupExpanded(toggle, contents, title, entries.length, expanded[index]);
        });
        root.addView(toggle, GoGoUi.matchWrap());

        contents.addView(GoGoUi.gap(this, 8));
        for (Entry entry : entries) {
            contents.addView(GoGoUi.navigationTile(this, entry.title, entry.description,
                    v -> open(entry.activity)), GoGoUi.matchWrap());
            contents.addView(GoGoUi.gap(this, 8));
        }
        root.addView(contents, GoGoUi.matchWrap());
    }

    private void setGroupExpanded(MaterialButton button, LinearLayout contents,
                                  String title, int count, boolean show) {
        contents.setVisibility(show ? View.VISIBLE : View.GONE);
        button.setText((show ? "▴ 收起 " : "▾ 展开 ") + title + " · " + count + " 项");
        button.setContentDescription(title + "，" + count + " 项，"
                + (show ? "已展开，点击收起" : "已收起，点击展开"));
    }

    private void open(Class<? extends Activity> activity) {
        startActivity(new Intent(this, activity));
    }

    private static final class Entry {
        final String title;
        final String description;
        final Class<? extends Activity> activity;

        Entry(String title, String description, Class<? extends Activity> activity) {
            this.title = title;
            this.description = description;
            this.activity = activity;
        }
    }
}
