package com.zcshou.gogogo;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
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
    private final MaterialButton[] groupToggles = new MaterialButton[GROUP_COUNT];
    private final LinearLayout[] groupBodies = new LinearLayout[GROUP_COUNT];
    private final String[] groupTitles = new String[GROUP_COUNT];
    private final int[] groupCounts = new int[GROUP_COUNT];
    private MaterialButton collapseAdvancedButton;
    private TextView readinessTitle;
    private TextView readinessDetail;
    private LabTimedSupportCopy timedSupportCopy;
    private LabExperimentPreflight experimentPreflight;
    private boolean preflightExpanded;
    private View preflightPanel;
    private MaterialButton preflightToggle;
    private boolean advancedExpanded;
    private LinearLayout advancedPanel;
    private MaterialButton advancedToggle;
    private MaterialButton copyLogButton;
    private TextView copyStatus;
    private java.util.concurrent.ExecutorService exportWorker;
    private boolean exporting;
    private boolean resumed;
    private int exportGeneration;
    private String readyReport;
    private long readyReportElapsed;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            advancedExpanded = savedInstanceState.getBoolean("hub_advanced_expanded", false);
            boolean[] previous = savedInstanceState.getBooleanArray(EXPANDED_STATE);
            if (previous != null) {
                // Migrate the previous three saved groups when the fourth was added.
                System.arraycopy(previous, 0, expanded, 0,
                        Math.min(previous.length, GROUP_COUNT));
            }
            preflightExpanded = savedInstanceState.getBoolean(PREFLIGHT_EXPANDED_KEY, false);
            // Previous builds allowed every tool group open at once. Normalize
            // restored state for the new single-open accordion.
            boolean oneOpen = preflightExpanded;
            for (int i = 0; i < GROUP_COUNT; i++) {
                if (!expanded[i]) continue;
                if (oneOpen) expanded[i] = false;
                else oneOpen = true;
            }
        }
        if (!advancedExpanded) {
            java.util.Arrays.fill(expanded, false);
            preflightExpanded = false;
        }
        buildUi();
    }

    @Override protected void onResume() {
        super.onResume();
        resumed = true;
        if (readyReport != null && SystemClock.elapsedRealtime() - readyReportElapsed > 60_000L) {
            readyReport = null;
            copyLogButton.setText("一键复制反馈");
            copyStatus.setText("点击会重新汇总当前状态，不用先运行实验。");
        }
        TianwaitianBottomNav.syncSelection(this, TianwaitianBottomNav.Tab.DIAGNOSTICS);
        refreshReadiness();
        if (experimentPreflight != null && advancedExpanded && preflightExpanded) experimentPreflight.refresh();
    }

    @Override protected void onPause() {
        resumed = false;
        super.onPause();
    }

    @Override protected void onDestroy() {
        exportGeneration++;
        if (exportWorker != null) exportWorker.shutdownNow();
        if (timedSupportCopy != null) timedSupportCopy.close();
        if (experimentPreflight != null) experimentPreflight.close();
        super.onDestroy();
    }

    @Override protected void onSaveInstanceState(Bundle outState) {
        outState.putBooleanArray(EXPANDED_STATE, expanded.clone());
        outState.putBoolean(PREFLIGHT_EXPANDED_KEY, preflightExpanded);
        outState.putBoolean("hub_advanced_expanded", advancedExpanded);
        super.onSaveInstanceState(outState);
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        GoGoUi.applyScreenBackground(scroll);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = GoGoUi.dp(this, 18);
        root.setPadding(pad, GoGoUi.dp(this, 12), pad, GoGoUi.dp(this, 24));
        scroll.addView(root);
        root.addView(GoGoUi.brandStrip(this), GoGoUi.matchWrap());
        root.addView(GoGoUi.heroTitle(this, "诊断与反馈"), GoGoUi.matchWrap());
        root.addView(GoGoUi.subtitle(this,
                "照常选点和使用应用，遇到问题后回来复制一份日志即可。"), GoGoUi.matchWrap());
        root.addView(GoGoUi.gap(this, 14));

        com.google.android.material.card.MaterialCardView feedback = GoGoUi.card(this);
        LinearLayout feedbackBody = GoGoUi.cardContent(this);
        feedback.addView(feedbackBody);
        feedbackBody.addView(GoGoUi.sectionTitle(this, "只需复制一次"), GoGoUi.matchWrap());
        feedbackBody.addView(GoGoUi.muted(this,
                "自动整理当前状态，旧实验只保留存档索引。无需先做快检、选实验或填写表格。"), GoGoUi.matchWrap());
        feedbackBody.addView(GoGoUi.gap(this, 12));
        copyLogButton = GoGoUi.primaryButton(this, "一键复制反馈", v -> copyFeedbackLog());
        feedbackBody.addView(copyLogButton, GoGoUi.matchWrap());
        feedbackBody.addView(GoGoUi.gap(this, 8));
        copyStatus = GoGoUi.muted(this, "复制后直接粘贴到对话。没有旧实验记录也可以使用。");
        feedbackBody.addView(copyStatus, GoGoUi.matchWrap());
        root.addView(feedback, GoGoUi.matchWrap());
        root.addView(GoGoUi.gap(this, 12));

        com.google.android.material.card.MaterialCardView overview = GoGoUi.card(this);
        LinearLayout overviewBody = GoGoUi.cardContent(this);
        overview.addView(overviewBody);
        overviewBody.addView(GoGoUi.muted(this, "当前状态 · 自动读取"), GoGoUi.matchWrap());
        readinessTitle = GoGoUi.sectionTitle(this, "正在读取状态…");
        overviewBody.addView(readinessTitle, GoGoUi.matchWrap());
        readinessDetail = GoGoUi.muted(this, "状态无法读取时也可以直接复制日志。");
        overviewBody.addView(readinessDetail, GoGoUi.matchWrap());
        root.addView(overview, GoGoUi.matchWrap());
        root.addView(GoGoUi.gap(this, 16));

        advancedToggle = GoGoUi.secondaryButton(this, "", v -> setAdvancedExpanded(!advancedExpanded));
        root.addView(advancedToggle, GoGoUi.matchWrap());
        advancedPanel = new LinearLayout(this);
        advancedPanel.setOrientation(LinearLayout.VERTICAL);
        root.addView(advancedPanel, GoGoUi.matchWrap());
        setAdvancedExpanded(advancedExpanded);
        setContentView(scroll);
        TianwaitianBottomNav.attach(this, TianwaitianBottomNav.Tab.DIAGNOSTICS);
    }

    private void setAdvancedExpanded(boolean show) {
        advancedExpanded = show;
        if (show && advancedPanel.getChildCount() == 0) buildAdvancedTools(advancedPanel);
        if (!show) collapseAllAdvanced();
        advancedPanel.setVisibility(show ? View.VISIBLE : View.GONE);
        advancedToggle.setText(show ? "收起高级工具" : "高级工具（通常不用）");
        advancedToggle.setContentDescription(show ? "高级工具已展开，点击收起" : "高级工具已收起，点击展开");
    }

    private void buildAdvancedTools(LinearLayout root) {
        root.addView(GoGoUi.muted(this, "以下是保留的手动研究工具，日常反馈不用逐项操作。"),
                GoGoUi.matchWrap());
        root.addView(GoGoUi.gap(this, 12));
        root.addView(GoGoUi.navigationTile(this, "应用运行",
                "自有 Demo 与兼容研究；日常定位反馈无需导入",
                v -> open(VirtualContainerV0Activity.class)), GoGoUi.matchWrap());

        root.addView(GoGoUi.gap(this, 12));
        root.addView(GoGoUi.sectionTitle(this, "手动检查（可选）"),
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
        timedSupportCopy = new LabTimedSupportCopy(this, () -> new String[] {"", ""});
        root.addView(GoGoUi.gap(this, 10));
        root.addView(timedSupportCopy.view(), GoGoUi.matchWrap());
        root.addView(GoGoUi.gap(this, 20));
        root.addView(GoGoUi.sectionTitle(this, "高级研究工具"),
                GoGoUi.matchWrap());
        root.addView(GoGoUi.muted(this,
                "专项对照、微信实验和兼容研究都保留在这里；一次只展开一个分类。"),
                GoGoUi.matchWrap());
        collapseAdvancedButton = GoGoUi.secondaryButton(this,
                "收起全部高级工具", v -> collapseAllAdvanced());
        root.addView(collapseAdvancedButton, GoGoUi.matchWrap());
        updateCollapseAdvancedButton();
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

    }

    private void copyFeedbackLog() {
        if (exporting) return;
        if (readyReport != null) {
            copyReadyReport();
            return;
        }
        exporting = true;
        final int token = ++exportGeneration;
        copyLogButton.setEnabled(false);
        copyLogButton.setText("正在整理日志…");
        copyStatus.setText("正在自动汇总，请稍候；不需要打开其他实验页面。");
        if (exportWorker == null) {
            exportWorker = java.util.concurrent.Executors.newSingleThreadExecutor(task -> {
                Thread worker = new Thread(task, "TianwaitianSupportExport");
                worker.setDaemon(true);
                return worker;
            });
        }
        final android.content.Context app = getApplicationContext();
        try {
            exportWorker.execute(() -> {
                String report = null;
                String failure = null;
                try { report = LabSupportLog.generateForFeedback(app); }
                catch (RuntimeException error) { failure = error.getClass().getSimpleName(); }
                final String result = report;
                final String error = failure;
                runOnUiThread(() -> {
                    if (token != exportGeneration || isFinishing() || isDestroyed()) return;
                    exporting = false;
                    copyLogButton.setEnabled(true);
                    if (result == null) {
                        copyLogButton.setText("一键复制反馈");
                        copyStatus.setText("整理未完成，请再点一次（" + error + "）");
                        return;
                    }
                    readyReport = result;
                    readyReportElapsed = SystemClock.elapsedRealtime();
                    if (resumed && hasWindowFocus()) copyReadyReport();
                    else {
                        copyLogButton.setText("复制已整理的日志");
                        copyStatus.setText("日志已整理。回到本页后点一下复制即可。");
                    }
                });
            });
        } catch (RuntimeException rejected) {
            exporting = false;
            copyLogButton.setEnabled(true);
            copyLogButton.setText("一键复制反馈");
            copyStatus.setText("暂时无法整理，请再点一次。");
        }
    }

    private void copyReadyReport() {
        if (!resumed || !hasWindowFocus()) return;
        if (LabSupportLog.copyPrepared(this, readyReport)) {
            readyReport = null;
            copyLogButton.setText("一键复制反馈");
            copyStatus.setText("已复制。直接粘贴到对话即可，不用再复制其他实验日志。");
        } else {
            copyLogButton.setText("重试复制已整理的日志");
            copyStatus.setText("日志已整理，点一下重试复制即可。");
        }
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
            boolean opening = !preflightExpanded;
            if (opening) collapseAllAdvanced();
            preflightExpanded = opening;
            setPreflightExpanded(preflightExpanded);
            updateCollapseAdvancedButton();
            if (opening) experimentPreflight.refresh();
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
        groupToggles[index] = toggle;
        groupBodies[index] = contents;
        groupTitles[index] = title;
        groupCounts[index] = entries.length;
        setGroupExpanded(toggle, contents, title, entries.length, expanded[index]);
        toggle.setOnClickListener(v -> {
            boolean opening = !expanded[index];
            if (opening) collapseAllAdvanced();
            expanded[index] = opening;
            setGroupExpanded(toggle, contents, title, entries.length, expanded[index]);
            updateCollapseAdvancedButton();
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

    private void collapseAllAdvanced() {
        for (int i = 0; i < GROUP_COUNT; i++) {
            expanded[i] = false;
            if (groupToggles[i] != null && groupBodies[i] != null) {
                setGroupExpanded(groupToggles[i], groupBodies[i],
                        groupTitles[i], groupCounts[i], false);
            }
        }
        preflightExpanded = false;
        setPreflightExpanded(false);
        updateCollapseAdvancedButton();
    }

    private void updateCollapseAdvancedButton() {
        if (collapseAdvancedButton == null) return;
        boolean anyOpen = preflightExpanded;
        for (boolean open : expanded) anyOpen |= open;
        collapseAdvancedButton.setVisibility(anyOpen ? View.VISIBLE : View.GONE);
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
