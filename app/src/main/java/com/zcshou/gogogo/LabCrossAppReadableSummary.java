package com.zcshou.gogogo;

import java.util.ArrayList;
import java.util.List;

/**
 * Human-readable, evidence-bound cross-app experiment summary.
 * Never assumes access to private SDKs or claims a mock-bypass mechanism.
 * Parses only our existing locally collected, privacy-redacted report format.
 */
public final class LabCrossAppReadableSummary {
    private LabCrossAppReadableSummary() {}

    public static String summarize(String report) {
        if (report == null || report.trim().isEmpty()) {
            return "暂无对照结果。\n先选择一个应用，打开查看位置，再回来记录观察。";
        }
        String[] blocks = report.split("========== CROSS_APP_ROUND at=");
        List<String> observations = new ArrayList<>();
        int finished = 0;
        int fullyMocked = 0;
        int ethernetCount = 0;
        boolean hasTencentMock = false;
        boolean hasWeChatReal = false;

        for (int i = 1; i < blocks.length; i++) {
            String block = blocks[i];
            if (!block.contains("\nEND_ROUND")) continue;
            finished++;
            String app = lineValue(block, "target=");
            String observed = lineValue(block, "visual_observation=");
            if (observed == null || "UNKNOWN".equals(observed)) continue;

            String nickname = app != null && app.contains("腾讯地图") ? "腾讯地图"
                    : app != null && app.contains("微信") ? "微信"
                    : app != null && app.contains("高德") ? "高德地图"
                    : app != null && app.contains("抖音") ? "抖音"
                    : app != null && app.contains("美团") ? "美团" : "其他应用";
            String description;
            if (observed.contains("均为 GoGoGo 模拟目标地区")) {
                description = "蓝点和附近地点都在模拟地区";
                if ("腾讯地图".equals(nickname)) hasTencentMock = true;
            } else if (observed.contains("均为国内实际地区")) {
                description = "蓝点和附近地点仍在国内";
                if ("微信".equals(nickname)) hasWeChatReal = true;
            } else if (observed.contains("蓝点国内、附近地点为模拟")) {
                description = "蓝点在国内，附近地点在模拟地区";
            } else if (observed.contains("蓝点为模拟目标地区、附近地点国内")) {
                description = "蓝点在模拟地区，附近地点在国内";
            } else if (observed.contains("加载失败") || observed.contains("网络不通畅")) {
                description = "有结果加载异常";
            } else {
                description = "存在其他情况，需看具体观察";
            }
            observations.add(nickname + "：" + description);
            if (allMocked(block)) fullyMocked++;
            if (block.contains("app_default_network=VPN+ETHERNET")) ethernetCount++;
        }

        if (finished == 0 || observations.isEmpty()) {
            return "已有实验数据，但还没有完整保存的人工观察。\n"
                    + "返回应用后选择真实看到的结果并保存即可。";
        }
        StringBuilder summary = new StringBuilder();
        summary.append("已完成 ").append(finished).append(" 轮对照\n");
        int from = Math.max(0, observations.size() - 4);
        for (int i = from; i < observations.size(); i++) {
            summary.append("• ").append(observations.get(i)).append("\n");
        }
        if (fullyMocked == finished) {
            summary.append("\n系统缓存：这些轮次的 GPS / NETWORK / GMS 前后")
                    .append("均为模拟目标附近（只代表本机公开接口）。\n");
        } else if (fullyMocked > 0) {
            summary.append("\n系统缓存：部分轮次的三条通道均为模拟目标；")
                    .append("其他轮次证据不足或不一致。\n");
        } else {
            summary.append("\n系统缓存：没有足够一致的三通道证据，需查看详细日志。\n");
        }
        if (ethernetCount == finished) {
            summary.append("网络：天外天默认通道为 VPN + 以太网。\n");
        }
        if (hasTencentMock && hasWeChatReal) {
            summary.append("\n结论：腾讯地图跟随模拟，微信没有跟随。")
                    .append("微信实际使用的定位来源仍未知；")
                    .append("不能据此断言缓存、独立 SDK 或模拟过滤就是原因。");
        } else {
            summary.append("\n提示：这是应用界面的人工观察，")
                    .append("不代表已经读到第三方应用的内部定位结果。");
        }
        return summary.toString();
    }

    private static String lineValue(String block, String key) {
        for (String line : block.split("\\r?\\n")) {
            if (line.startsWith(key)) return line.substring(key.length()).trim();
        }
        return null;
    }

    private static boolean allMocked(String block) {
        for (String phase : new String[]{"BEFORE", "AFTER"}) {
            for (String source : new String[]{"GPS", "NETWORK", "GMS_LAST"}) {
                boolean found = false;
                String prefix = phase + " " + source + "：";
                for (String line : block.split("\\r?\\n")) {
                    if (line.startsWith(prefix) && line.contains("mock=YES")
                            && line.contains("mock_target_distance=LT_1KM")) {
                        found = true;
                        break;
                    }
                }
                if (!found) return false;
            }
        }
        return true;
    }
}
