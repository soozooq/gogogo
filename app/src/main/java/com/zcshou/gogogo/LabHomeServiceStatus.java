package com.zcshou.gogogo;

/**
 * UI-only interpretation of ServiceGo.sRunning and an app-side request.
 * The process flag is never treated as GMS/system/third-party acceptance.
 */
public final class LabHomeServiceStatus {
    public enum Request { NONE, START, STOP }
    public enum Tone { NEUTRAL, INFO, WARNING }

    public static final class Summary {
        public final String text;
        public final Tone tone;

        private Summary(String text, Tone tone) {
            this.text = text;
            this.tone = tone;
        }
    }

    private LabHomeServiceStatus() {}

    public static Summary render(boolean processRunning, Request request,
                                 boolean observationWindowElapsed) {
        Request action = request == null ? Request.NONE : request;
        if (action == Request.START) {
            if (processRunning) {
                return new Summary(
                        "服务运行标记：RUNNING · 仍需独立验证消费者实际位置", Tone.INFO);
            }
            if (observationWindowElapsed) {
                return new Summary(
                        "已发送启动请求，但尚未观察到服务运行 · 请进入诊断中心排查",
                        Tone.WARNING);
            }
            return new Summary("正在核对启动请求 · 服务运行状态尚未确认", Tone.INFO);
        }

        if (action == Request.STOP) {
            if (!processRunning) {
                return new Summary(
                        "服务运行标记：STOPPED · Provider/GMS 清理结果仍需独立检查",
                        Tone.NEUTRAL);
            }
            if (observationWindowElapsed) {
                return new Summary(
                        "已发送停止请求，但进程仍显示 RUNNING · 请检查诊断",
                        Tone.WARNING);
            }
            return new Summary("正在核对停止请求 · 当前进程仍显示 RUNNING",
                    Tone.INFO);
        }

        if (processRunning) {
            return new Summary(
                    "服务运行标记：RUNNING · 不代表第三方应用已接受模拟位置",
                    Tone.INFO);
        }
        return new Summary("服务运行标记：STOPPED · 当前未观察到运行中的服务",
                Tone.NEUTRAL);
    }

    public static boolean requestConfirmed(boolean running, Request request) {
        return request == Request.START && running
                || request == Request.STOP && !running;
    }
}
