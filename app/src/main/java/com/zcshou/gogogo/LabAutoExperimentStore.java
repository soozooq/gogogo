package com.zcshou.gogogo;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Locale;

/**
 * Persists compact Lab 18 automatic black-box experiment results.
 */
public final class LabAutoExperimentStore {
    public static final String TYPE_WECHAT = "WECHAT";
    public static final String TYPE_CONTROL = "CONTROL";

    private static final String PREFS = "gogogo_lab18_auto_experiments";

    private LabAutoExperimentStore() {}

    public static void save(
            Context context,
            String type,
            LabCompatibilitySessionRecorder.Summary summary,
            long durationMs,
            boolean batteryOptimizationExempt) {
        if (context == null || summary == null || type == null) return;

        long worstGap = max(
                summary.gps == null ? 0L : summary.gps.maxGapMs,
                summary.network == null ? 0L : summary.network.maxGapMs,
                summary.gms == null ? 0L : summary.gms.maxGapMs,
                summary.heartbeat == null ? 0L : summary.heartbeat.maxGapMs,
                summary.backgroundHeartbeat == null
                        ? 0L
                        : summary.backgroundHeartbeat.maxGapMs);

        long worstP99 = max(
                summary.gps == null || summary.gps.gaps == null ? 0L : summary.gps.gaps.p99Ms,
                summary.network == null || summary.network.gaps == null ? 0L : summary.network.gaps.p99Ms,
                summary.gms == null || summary.gms.gaps == null ? 0L : summary.gms.gaps.p99Ms,
                summary.heartbeat == null || summary.heartbeat.gaps == null
                        ? 0L
                        : summary.heartbeat.gaps.p99Ms,
                summary.backgroundHeartbeat == null
                        || summary.backgroundHeartbeat.gaps == null
                        ? 0L
                        : summary.backgroundHeartbeat.gaps.p99Ms);

        String prefix = prefix(type);
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(prefix + "present", true)
                .putLong(prefix + "saved_at", System.currentTimeMillis())
                .putLong(prefix + "duration_ms", durationMs)
                .putLong(prefix + "worst_gap_ms", worstGap)
                .putLong(prefix + "worst_p99_ms", worstP99)
                .putString(prefix + "diagnosis", summary.freezeDiagnosis())
                .putString(prefix + "grade", summary.grade())
                .putBoolean(prefix + "battery_exempt", batteryOptimizationExempt)
                .putInt(prefix + "errors", summary.errorCount)
                .apply();
    }

    public static Result load(Context context, String type) {
        if (context == null || type == null) return Result.missing(type);
        SharedPreferences prefs =
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String prefix = prefix(type);
        if (!prefs.getBoolean(prefix + "present", false)) {
            return Result.missing(type);
        }

        return new Result(
                type,
                true,
                prefs.getLong(prefix + "saved_at", 0L),
                prefs.getLong(prefix + "duration_ms", 0L),
                prefs.getLong(prefix + "worst_gap_ms", 0L),
                prefs.getLong(prefix + "worst_p99_ms", 0L),
                prefs.getString(prefix + "diagnosis", "UNKNOWN"),
                prefs.getString(prefix + "grade", "UNKNOWN"),
                prefs.getBoolean(prefix + "battery_exempt", false),
                prefs.getInt(prefix + "errors", 0));
    }

    public static Comparison compare(Context context) {
        return compare(
                load(context, TYPE_WECHAT),
                load(context, TYPE_CONTROL));
    }

    public static Comparison compare(Result wechat, Result control) {
        if (wechat == null) wechat = Result.missing(TYPE_WECHAT);
        if (control == null) control = Result.missing(TYPE_CONTROL);

        if (!wechat.present || !control.present) {
            return new Comparison(
                    "NEED_BOTH",
                    "还差一组：微信和控制组都完成后才自动比较。",
                    wechat,
                    control);
        }

        if (wechat.worstGapMs <= 1500L && control.worstGapMs <= 1500L) {
            return new Comparison(
                    "BOTH_STABLE",
                    "两组都稳定：后台调度已经基本压平。",
                    wechat,
                    control);
        }

        if (wechat.worstGapMs > 3000L
                && control.worstGapMs > 3000L
                && ratio(wechat.worstGapMs, control.worstGapMs) < 2.0) {
            return new Comparison(
                    "OEM_BACKGROUND_GENERAL",
                    "微信组和控制组都出现明显长 gap，形态更像系统/OEM 普遍后台调度。",
                    wechat,
                    control);
        }

        if (wechat.worstGapMs > Math.max(3000L, control.worstGapMs * 2L)
                && wechat.worstP99Ms > Math.max(2000L, control.worstP99Ms * 2L)) {
            return new Comparison(
                    "WECHAT_ASSOCIATED_SLOWDOWN",
                    "微信组明显比控制组差；说明“切到微信”与后台调度恶化存在强关联，但不等于微信主动杀进程。",
                    wechat,
                    control);
        }

        if (control.worstGapMs > Math.max(3000L, wechat.worstGapMs * 2L)) {
            return new Comparison(
                    "CONTROL_WORSE",
                    "控制组反而更差，暂时不支持“微信场景特异”的判断。",
                    wechat,
                    control);
        }

        return new Comparison(
                "SIMILAR_MIXED",
                "两组有差异，但还没有达到足够强的特异性阈值。",
                wechat,
                control);
    }

    public static void clear(Context context) {
        if (context == null) return;
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .clear()
                .apply();
    }

    private static String prefix(String type) {
        return type.toLowerCase(Locale.US) + "_";
    }

    private static long max(long... values) {
        long out = 0L;
        if (values == null) return out;
        for (long value : values) out = Math.max(out, value);
        return out;
    }

    private static double ratio(long a, long b) {
        long hi = Math.max(a, b);
        long lo = Math.max(1L, Math.min(a, b));
        return (double) hi / (double) lo;
    }

    public static final class Result {
        public final String type;
        public final boolean present;
        public final long savedAtMs;
        public final long durationMs;
        public final long worstGapMs;
        public final long worstP99Ms;
        public final String diagnosis;
        public final String grade;
        public final boolean batteryOptimizationExempt;
        public final int errors;

        Result(
                String type,
                boolean present,
                long savedAtMs,
                long durationMs,
                long worstGapMs,
                long worstP99Ms,
                String diagnosis,
                String grade,
                boolean batteryOptimizationExempt,
                int errors) {
            this.type = type;
            this.present = present;
            this.savedAtMs = savedAtMs;
            this.durationMs = durationMs;
            this.worstGapMs = worstGapMs;
            this.worstP99Ms = worstP99Ms;
            this.diagnosis = diagnosis;
            this.grade = grade;
            this.batteryOptimizationExempt = batteryOptimizationExempt;
            this.errors = errors;
        }

        static Result missing(String type) {
            return new Result(
                    type,
                    false,
                    0L,
                    0L,
                    0L,
                    0L,
                    "N/A",
                    "N/A",
                    false,
                    0);
        }
    }

    public static final class Comparison {
        public final String verdict;
        public final String explanation;
        public final Result wechat;
        public final Result control;

        Comparison(
                String verdict,
                String explanation,
                Result wechat,
                Result control) {
            this.verdict = verdict;
            this.explanation = explanation;
            this.wechat = wechat;
            this.control = control;
        }
    }
}
