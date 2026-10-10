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
    private static final int GROUP_COUNT = 3;
    private final boolean[] expanded = new boolean[GROUP_COUNT];
    private TextView readinessTitle;
    private TextView readinessDetail;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            boolean[] previous = savedInstanceState.getBooleanArray(EXPANDED_STATE);
            if (previous != null && previous.length == GROUP_COUNT) {
                System.arraycopy(previous, 0, expanded, 0, GROUP_COUNT);
            }
        }
        buildUi();
    }

    @Override protected void onResume() {
        super.onResume();
        refreshReadiness();
    }

    @Override protected void onSaveInstanceState(Bundle outState) {
        outState.putBooleanArray(EXPANDED_STATE, expanded.clone());
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
        root.addView(GoGoUi.eyebrow(this, "TOOLS  /  DIAGNOSTICS"), GoGoUi.matchWrap());
        root.addView(GoGoUi.heroTitle(this, "实验与诊断"), GoGoUi.matchWrap());
        root.addView(GoGoUi.subtitle(this,
                "先查前置条件，再看真实位置消费者；深入排障和研究工具按需展开。"
                        + "打开这里不会启动模拟。"), GoGoUi.matchWrap());
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
        root.addView(overview, GoGoUi.matchWrap());

        root.addView(GoGoUi.gap(this, 18));
        root.addView(GoGoUi.sectionTitle(this, "先用这两个工具"),
                GoGoUi.matchWrap());
        root.addView(GoGoUi.navigationTile(this, "基础快检与综合报告",
                "检查授权和运行标记，保存基准或对比异常清理历史",
                v -> open(LabQuickCheckActivity.class)), GoGoUi.matchWrap());
        root.addView(GoGoUi.gap(this, 8));
        root.addView(GoGoUi.navigationTile(this, "Consumer Matrix",
                "对照 Android / GMS 的定位回调与新鲜度；不自动启动模拟",
                v -> open(ConsumerLocationProbeActivity.class)), GoGoUi.matchWrap());
        root.addView(GoGoUi.gap(this, 8));
        root.addView(GoGoUi.navigationTile(this, "被动快照（无定位请求）",
                "只读 GPS / NETWORK / GMS 缓存，不触发新鲜定位或持续订阅",
                v -> {
                    Intent intent = new Intent(this, ConsumerLocationProbeActivity.class);
                    intent.putExtra(ConsumerLocationProbeActivity.EXTRA_PASSIVE_MODE, true);
                    startActivity(intent);
                }), GoGoUi.matchWrap());

        root.addView(GoGoUi.gap(this, 20));
        root.addView(GoGoUi.sectionTitle(this, "按需展开其他工具"),
                GoGoUi.matchWrap());
        root.addView(GoGoUi.muted(this,
                "把日常检查留在顶部；原有高级实验保留在下面，点击分类展开。"),
                GoGoUi.matchWrap());

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
