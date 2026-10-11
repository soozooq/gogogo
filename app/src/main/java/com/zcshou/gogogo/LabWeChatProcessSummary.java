package com.zcshou.gogogo;

/**
 * Only renders rows which explicitly name WeChat processes. A ps snapshot
 * cannot attribute shared package names to primary/clone user profiles safely.
 */
public final class LabWeChatProcessSummary {
    private LabWeChatProcessSummary() {}

    public static String fromPs(String text, boolean success) {
        if (!success || text == null) {
            return "系统进程快照不可用（命令失败或超时），请勿据此判断微信已结束";
        }
        String[] lines = text.split("\\r?\\n");
        StringBuilder output = new StringBuilder();
        int matches = 0;
        for (String line : lines) {
            String[] columns = line.trim().split("\\s+");
            if (columns.length < 3) continue;
            String processName = columns[columns.length - 1];
            if (!"com.tencent.mm".equals(processName)
                    && !processName.startsWith("com.tencent.mm:")) continue;
            if (matches < 30) {
                output.append(columns[0]).append("  ")
                        .append(columns[1]).append("  ")
                        .append(processName).append("\\n");
            }
            matches++;
        }
        if (matches == 0) {
            return "当前快照未发现微信相关进程；不代表已强停或分身一定不存在";
        }
        return "当前可见微信相关进程：" + matches + " 条（含子进程）\\n"
                + "用户标识  PID  进程名\\n" + output
                + (matches > 30 ? "…其余记录省略\\n" : "")
                + "本结果为单次只读快照，无法单凭进程名区分主微信与 vivo 分身；"
                + "不能证明后续后台存活或微信定位来源。";
    }
}
