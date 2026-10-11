package com.zcshou.gogogo;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * User-triggered timed clipboard copy. No location calls, background service,
 * automatic mock start/stop, permissions or network requests.
 *
 * The deadline uses elapsedRealtime, not a decrementing counter. Android may
 * delay main-thread delivery in the background; activity destruction cancels.
 */
public final class LabTimedSupportCopy {
    public interface TargetSource {
        String[] currentTargets();
    }

    private final Activity activity;
    private final TargetSource targetSource;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final LinearLayout view;
    private final TextView status;
    private final Runnable tick = this::advance;
    private long deadlineElapsed = -1L;
    private String longitude = "";
    private String latitude = "";

    public LabTimedSupportCopy(Activity activity, TargetSource targetSource) {
        this.activity = activity;
        this.targetSource = targetSource;
        view = new LinearLayout(activity);
        view.setOrientation(LinearLayout.VERTICAL);
        view.addView(GoGoUi.muted(activity, "定时复制 · 到点自动将最近诊断记录放入剪贴板"),
                GoGoUi.matchWrap());
        view.addView(GoGoUi.gap(activity, 8));

        LinearLayout presets = GoGoUi.row(activity);
        addPreset(presets, 10);
        GoGoUi.addHorizontalGap(activity, presets, 6);
        addPreset(presets, 30);
        GoGoUi.addHorizontalGap(activity, presets, 6);
        addPreset(presets, 60);
        view.addView(presets, GoGoUi.matchWrap());
        view.addView(GoGoUi.gap(activity, 6));
        view.addView(GoGoUi.secondaryButton(activity, "取消倒计时", v -> cancel()),
                GoGoUi.matchWrap());
        status = GoGoUi.muted(activity,
                "选择秒数开始；关闭本页面会取消。切到后台或锁屏可能延迟。");
        view.addView(status, GoGoUi.matchWrap());
    }

    private void addPreset(LinearLayout row, int seconds) {
        row.addView(GoGoUi.secondaryButton(activity, seconds + " 秒",
                v -> start(seconds)), GoGoUi.weighted());
    }

    public LinearLayout view() {
        return view;
    }

    private void start(int seconds) {
        handler.removeCallbacks(tick);
        String[] target = targetSource == null ? null : targetSource.currentTargets();
        longitude = target != null && target.length > 0 && target[0] != null ? target[0] : "";
        latitude = target != null && target.length > 1 && target[1] != null ? target[1] : "";
        deadlineElapsed = SystemClock.elapsedRealtime() + seconds * 1000L;
        advance();
    }

    private void advance() {
        if (deadlineElapsed < 0L) return;
        long remainingMs = deadlineElapsed - SystemClock.elapsedRealtime();
        if (remainingMs > 0L) {
            status.setText("倒计时 " + LabSupportCountdownMath.remainingSeconds(
                    deadlineElapsed, SystemClock.elapsedRealtime())
                    + " 秒 · 完成后自动复制（可取消）");
            handler.postDelayed(tick, Math.min(1000L, remainingMs));
            return;
        }
        deadlineElapsed = -1L;
        handler.removeCallbacks(tick);
        boolean copied = LabSupportLog.copyWithResult(activity, longitude, latitude);
        status.setText(copied ? "倒计时结束 · 已复制最新日志，直接粘贴即可"
                : "倒计时结束 · 复制失败，请点手动复制重试");
    }

    private void cancel() {
        handler.removeCallbacks(tick);
        if (deadlineElapsed >= 0L) {
            deadlineElapsed = -1L;
            status.setText("已取消 · 没有更改剪贴板");
        } else {
            status.setText("当前没有运行中的倒计时");
        }
    }

    /** Called by the hosting Activity in onDestroy; no delayed clipboard write. */
    public void close() {
        handler.removeCallbacks(tick);
        deadlineElapsed = -1L;
    }
}
