package com.zcshou.gogogo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Lab 19 background-survival health scorer.
 *
 * Converts the low-level Lab 17/18 diagnostics into a compact product-facing health
 * score. It intentionally scores only GoGoGo's own background execution and standard
 * Android/GMS location continuity.
 */
public final class LabSurvivalHealthEngine {
    private LabSurvivalHealthEngine() {}

    public static Report evaluate(
            boolean batteryOptimizationExempt,
            boolean backgroundLocationGranted,
            LabCompatibilitySessionRecorder.Summary summary,
            LabAutoExperimentStore.Comparison comparison) {
        int score = 100;
        List<String> findings = new ArrayList<>();
        List<String> actions = new ArrayList<>();

        if (!batteryOptimizationExempt) {
            score -= 30;
            findings.add("电池优化未豁免：存在 OEM 后台冻结高风险");
            actions.add("允许后台高耗电 / 关闭电池优化");
        }

        if (!backgroundLocationGranted) {
            score -= 4;
            findings.add("后台定位权限未授予：长期后台定位能力可能受系统版本限制");
            actions.add("需要长期后台定位时再授予后台定位权限");
        }

        if (summary == null) {
            LabAutoExperimentStore.Result best = bestStoredResult(comparison);
            if (best == null || !best.present) {
                score -= 12;
                findings.add("暂无近期 Survival 会话");
                actions.add("运行一次一键自动实验建立基线");
                return finish(score, findings, actions, comparison);
            }

            if (best.errors > 0) {
                score -= 10;
                findings.add("最近自动实验存在 API error");
            }
            if (best.worstGapMs > 10000L) {
                score -= 35;
                findings.add("最近自动实验出现 >10s 严重冻结");
            } else if (best.worstGapMs > 3000L) {
                score -= 16;
                findings.add("最近自动实验出现 >3s 后台抖动");
            } else if (best.worstGapMs > 1500L) {
                score -= 5;
                findings.add("最近自动实验存在轻微尾部抖动");
            } else {
                findings.add("最近自动实验未发现长 gap");
            }

            if (best.worstP99Ms > 1500L) {
                score -= 8;
                findings.add("最近实验 p99 偏高：" + best.worstP99Ms + " ms");
            } else if (best.worstP99Ms > 900L) {
                score -= 2;
                findings.add("最近实验仅有轻微 p99 抖动：" + best.worstP99Ms + " ms");
            }

            return finish(score, findings, actions, comparison);
        }

        if (summary.errorCount > 0) {
            score -= Math.min(20, 8 + summary.errorCount * 3);
            findings.add("Recorder 出现 API error ×" + summary.errorCount);
        }

        int liveStreams = 0;
        if (summary.gps != null && summary.gps.count > 0) liveStreams++;
        if (summary.network != null && summary.network.count > 0) liveStreams++;
        if (summary.gms != null && summary.gms.count > 0) liveStreams++;

        if (liveStreams < 3) {
            score -= (3 - liveStreams) * 10;
            findings.add("实时位置链仅 " + liveStreams + "/3 可见");
            actions.add("检查 GPS / NETWORK / GMS Fused 可用性");
        }

        long worstLocationMax = max(
                maxGap(summary.gps),
                maxGap(summary.network),
                maxGap(summary.gms));
        long worstLocationP99 = max(
                p99(summary.gps),
                p99(summary.network),
                p99(summary.gms));

        long mainMax = summary.heartbeat == null ? 0L : summary.heartbeat.maxGapMs;
        long bgMax = summary.backgroundHeartbeat == null
                ? 0L
                : summary.backgroundHeartbeat.maxGapMs;
        long mainP99 = summary.heartbeat == null || summary.heartbeat.gaps == null
                ? 0L
                : summary.heartbeat.gaps.p99Ms;
        long bgP99 = summary.backgroundHeartbeat == null
                || summary.backgroundHeartbeat.gaps == null
                ? 0L
                : summary.backgroundHeartbeat.gaps.p99Ms;

        int severe10s = over10000(summary.gps)
                + over10000(summary.network)
                + over10000(summary.gms)
                + over10000(summary.heartbeat)
                + over10000(summary.backgroundHeartbeat);
        int severe3s = over3000(summary.gps)
                + over3000(summary.network)
                + over3000(summary.gms)
                + over3000(summary.heartbeat)
                + over3000(summary.backgroundHeartbeat);
        int minor15s = over1500(summary.gps)
                + over1500(summary.network)
                + over1500(summary.gms)
                + over1500(summary.heartbeat)
                + over1500(summary.backgroundHeartbeat);

        if (severe10s > 0) {
            score -= Math.min(45, 30 + severe10s * 3);
            findings.add("检测到 >10s 严重冻结 ×" + severe10s);
            actions.add("优先检查厂商后台耗电/冻结策略");
        } else if (severe3s > 0) {
            score -= Math.min(25, 12 + severe3s * 3);
            findings.add("检测到 >3s 明显后台抖动 ×" + severe3s);
        } else if (minor15s > 0) {
            score -= Math.min(10, 3 + minor15s * 2);
            findings.add("存在少量 >1.5s 轻微调度抖动 ×" + minor15s);
        }

        if (worstLocationP99 > 1500L) {
            score -= 8;
            findings.add("位置流 p99 偏高：" + worstLocationP99 + " ms");
        } else if (worstLocationP99 > 900L) {
            score -= 3;
            findings.add("位置流 p99 有轻微尾延迟：" + worstLocationP99 + " ms");
        }

        if (mainP99 > 1500L) {
            score -= 7;
            findings.add("主线程 heartbeat p99 偏高：" + mainP99 + " ms");
        } else if (mainP99 > 900L) {
            score -= 2;
            findings.add("主线程有轻微抖动：" + mainP99 + " ms");
        }

        if (bgP99 > 1500L) {
            score -= 9;
            findings.add("后台 heartbeat p99 偏高：" + bgP99 + " ms");
        } else if (bgP99 > 900L) {
            score -= 2;
            findings.add("后台线程有轻微调度抖动：" + bgP99 + " ms");
        }

        if (worstLocationMax <= 1500L
                && mainMax <= 1700L
                && bgMax <= 1500L
                && severe3s == 0
                && summary.errorCount == 0) {
            findings.add("三条位置流与双 heartbeat 整体稳定");
        }

        if (summary.maxLastSeparationMeters > 10.0) {
            score -= 10;
            findings.add(String.format(
                    Locale.US,
                    "跨 API 最后坐标分离 %.1f m",
                    summary.maxLastSeparationMeters));
        }

        String diagnosis = summary.freezeDiagnosis();
        if ("PROCESS_OR_SCHEDULER_FREEZE".equals(diagnosis)) {
            score -= 18;
            findings.add("形态：整进程/调度冻结");
        } else if ("MAIN_THREAD_STALL".equals(diagnosis)) {
            score -= 7;
            findings.add("形态：主线程 stall");
        } else if ("LOCATION_CALLBACK_THROTTLE".equals(diagnosis)) {
            score -= 8;
            findings.add("形态：位置回调节流");
        }

        return finish(score, findings, actions, comparison);
    }

    private static LabAutoExperimentStore.Result bestStoredResult(
            LabAutoExperimentStore.Comparison comparison) {
        if (comparison == null) return null;
        LabAutoExperimentStore.Result a = comparison.wechat;
        LabAutoExperimentStore.Result b = comparison.control;

        if (a != null && a.present && b != null && b.present) {
            return a.worstGapMs >= b.worstGapMs ? a : b;
        }
        if (a != null && a.present) return a;
        if (b != null && b.present) return b;
        return null;
    }

    private static Report finish(
            int rawScore,
            List<String> findings,
            List<String> actions,
            LabAutoExperimentStore.Comparison comparison) {
        int score = Math.max(0, Math.min(100, rawScore));

        String level;
        if (score >= 95) {
            level = "EXCELLENT";
        } else if (score >= 88) {
            level = "STABLE_WITH_MINOR_JITTER";
        } else if (score >= 72) {
            level = "NEEDS_ATTENTION";
        } else if (score >= 50) {
            level = "HIGH_RISK";
        } else {
            level = "SEVERE";
        }

        if (comparison != null
                && comparison.verdict != null
                && !"NEED_BOTH".equals(comparison.verdict)) {
            findings.add("场景对比：" + comparison.verdict);
        }

        if (findings.isEmpty()) {
            findings.add("未发现明显后台生存问题");
        }
        if (actions.isEmpty()) {
            actions.add("无需额外处理");
        }

        return new Report(
                score,
                level,
                Collections.unmodifiableList(findings),
                Collections.unmodifiableList(actions));
    }

    private static long max(long... values) {
        long out = 0L;
        if (values == null) return out;
        for (long value : values) out = Math.max(out, value);
        return out;
    }

    private static long maxGap(LabCompatibilitySessionRecorder.StreamSummary s) {
        return s == null ? 0L : s.maxGapMs;
    }

    private static long p99(LabCompatibilitySessionRecorder.StreamSummary s) {
        return s == null || s.gaps == null ? 0L : s.gaps.p99Ms;
    }

    private static int over1500(LabCompatibilitySessionRecorder.StreamSummary s) {
        return s == null || s.gaps == null ? 0 : s.gaps.over1500;
    }

    private static int over3000(LabCompatibilitySessionRecorder.StreamSummary s) {
        return s == null || s.gaps == null ? 0 : s.gaps.over3000;
    }

    private static int over10000(LabCompatibilitySessionRecorder.StreamSummary s) {
        return s == null || s.gaps == null ? 0 : s.gaps.over10000;
    }

    private static int over1500(LabCompatibilitySessionRecorder.HeartbeatSummary s) {
        return s == null || s.gaps == null ? 0 : s.gaps.over1500;
    }

    private static int over3000(LabCompatibilitySessionRecorder.HeartbeatSummary s) {
        return s == null || s.gaps == null ? 0 : s.gaps.over3000;
    }

    private static int over10000(LabCompatibilitySessionRecorder.HeartbeatSummary s) {
        return s == null || s.gaps == null ? 0 : s.gaps.over10000;
    }

    public static final class Report {
        public final int score;
        public final String level;
        public final List<String> findings;
        public final List<String> actions;

        Report(
                int score,
                String level,
                List<String> findings,
                List<String> actions) {
            this.score = score;
            this.level = level;
            this.findings = findings;
            this.actions = actions;
        }

        public String headline() {
            switch (level) {
                case "EXCELLENT":
                    return "后台健康优秀";
                case "STABLE_WITH_MINOR_JITTER":
                    return "后台稳定 · 仅轻微抖动";
                case "NEEDS_ATTENTION":
                    return "后台可用 · 建议优化";
                case "HIGH_RISK":
                    return "后台高风险";
                default:
                    return "后台严重不稳定";
            }
        }
    }
}
