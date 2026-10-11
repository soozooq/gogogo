package com.zcshou.gogogo;

/** Labels deliberately separate observation from inference. */
public final class LabExperimentStatusText {
    private LabExperimentStatusText() {}

    public static String serviceFlag(Boolean running) {
        if (running == null) return "无法读取主进程标记；请在 Consumer Matrix 看实际定位样本";
        return running
                ? "主进程 ServiceGo 标记 RUNNING（不等于所有定位接口已模拟）"
                : "主进程 ServiceGo 标记 STOPPED（不等于系统缓存已清理）";
    }

    public static String weChatStopped(Boolean stopped) {
        if (stopped == null) return "无法读取当前用户的微信 stopped 标记";
        return stopped
                ? "FLAG_STOPPED=YES（系统标记已停止，不代表可证明进程已退出）"
                : "FLAG_STOPPED=NO（不能据此断言微信仍在运行）";
    }
}
