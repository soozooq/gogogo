package com.zcshou.gogogo;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

/**
 * One stable, non-destructive entrance to existing diagnostic and research
 * screens. Never starts/stops a mock service or requests privileged access.
 */
public final class LabHubActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
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

        root.addView(GoGoUi.backButton(this, v -> finish()),
                GoGoUi.matchWrap());
        root.addView(GoGoUi.eyebrow(this, "TOOLS  /  LAB CENTER"),
                GoGoUi.matchWrap());
        root.addView(GoGoUi.heroTitle(this, "实验与诊断"),
                GoGoUi.matchWrap());
        root.addView(GoGoUi.subtitle(this,
                "定位验证、运动分析和高级实验统一收在这里。选择工具不会自动启动模拟。"),
                GoGoUi.matchWrap());

        root.addView(GoGoUi.gap(this, 12));
        root.addView(GoGoUi.navigationTile(this,
                "一键基础快检",
                "先看权限、模拟位置 AppOps 和服务标记；可复制隐私精简摘要",
                v -> open(LabQuickCheckActivity.class)), GoGoUi.matchWrap());

        addGroup(root, "定位与兼容",
                "先检查标准位置消费者，再决定是否使用更深入的探针。",
                new Entry[]{
                        new Entry("实验仪表盘", "权限、网络、生命周期与 Provider 取证",
                                LabDiagnosticsActivity.class),
                        new Entry("Consumer Matrix", "Android / GMS 多通道位置对照",
                                ConsumerLocationProbeActivity.class),
                        new Entry("腾讯定位探针", "GEO / POI / 缓存独立测试；需要有效 Key",
                                TencentLocationProbeActivity.class),
                        new Entry("定位兼容性", "不同定位通道和行为检查",
                                LocationCompatibilityActivity.class),
                        new Entry("持续运行观察", "后台服务与独立消费者的运行记录",
                                CompatibilitySessionActivity.class)
                });
        addGroup(root, "运动与路线",
                "这些是实验分析页面；模拟路线的常用操作仍留在地图页。",
                new Entry[]{
                        new Entry("场景与回放", "实验场景、回放参数",
                                ScenarioLabActivity.class),
                        new Entry("Motion Audit", "运动质量与历史轨迹分析",
                                MotionAuditActivity.class),
                        new Entry("Heading Intelligence", "方向与姿态传感器实验",
                                HeadingLabActivity.class)
                });
        addGroup(root, "系统研究",
                "仅当需要排查复杂设备差异时使用。",
                new Entry[]{
                        new Entry("Resource Broker", "资源与系统策略观察",
                                PolicyLabActivity.class),
                        new Entry("Sandbox / Work Profile", "沙箱与工作资料隔离测试",
                                SandboxLabActivity.class),
                        new Entry("Cross Profile Observatory", "跨资料空间实验",
                                CrossProfileObservatoryActivity.class),
                        new Entry("Isolated Capsule", "隔离进程资源实验",
                                IsolatedCapsuleLabActivity.class)
                });
        TextView note = GoGoUi.muted(this,
                "注：Shizuku 授权仍需在实验仪表盘中主动操作；这里不会自动请求权限。");
        note.setPadding(0, GoGoUi.dp(this, 16), 0, 0);
        root.addView(note, GoGoUi.matchWrap());
        setContentView(scroll);
    }

    private void addGroup(LinearLayout root, String title, String description, Entry[] entries) {
        root.addView(GoGoUi.gap(this, 18));
        root.addView(GoGoUi.sectionTitle(this, title), GoGoUi.matchWrap());
        root.addView(GoGoUi.muted(this, description), GoGoUi.matchWrap());
        root.addView(GoGoUi.gap(this, 8));
        for (Entry entry : entries) {
            root.addView(GoGoUi.navigationTile(this, entry.title, entry.description,
                    v -> open(entry.activity)), GoGoUi.matchWrap());
            root.addView(GoGoUi.gap(this, 8));
        }
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
