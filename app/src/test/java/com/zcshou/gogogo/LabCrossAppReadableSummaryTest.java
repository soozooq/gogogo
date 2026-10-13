package com.zcshou.gogogo;

import org.junit.Test;
import static org.junit.Assert.*;

public class LabCrossAppReadableSummaryTest {
    private static String mockedRound(String app, String observed) {
        StringBuilder b = new StringBuilder();
        b.append("\n========== CROSS_APP_ROUND at=2026-10-13 21:51:00 ==========\n");
        b.append("target=").append(app).append("\n");
        b.append("NETWORK_RADIO_BEFORE\napp_default_network=VPN+ETHERNET\n");
        for (String phase : new String[]{"BEFORE", "AFTER"}) {
            b.append(phase).append(" GPS：fixAge=29ms mock=YES mock_target_distance=LT_1KM\n");
            b.append(phase).append(" NETWORK：fixAge=51ms mock=YES mock_target_distance=LT_1KM\n");
            b.append(phase).append(" GMS_LAST：fixAge=183ms mock=YES mock_target_distance=LT_1KM\n");
        }
        b.append("HUMAN_OBSERVATION\napp=").append(app).append("\n");
        b.append("visual_observation=").append(observed).append("\n");
        b.append("observation_source=USER_REPORTED_NOT_SDK_PROVEN\n");
        b.append("END_ROUND\n");
        return b.toString();
    }

    @Test public void rendersSimpleChineseSummaryForUserEvidence() {
        String tencent = mockedRound("腾讯地图 App",
                "蓝点和附近地点均为 GoGoGo 模拟目标地区");
        String wechat = mockedRound("微信发送位置",
                "蓝点和附近地点均为国内实际地区");
        String result = LabCrossAppReadableSummary.summarize(tencent + wechat);
        assertTrue(result.contains("已完成 2 轮"));
        assertTrue(result.contains("腾讯地图：蓝点和附近地点都在模拟地区"));
        assertTrue(result.contains("微信：蓝点和附近地点仍在国内"));
        assertTrue(result.contains("GPS / NETWORK / GMS"));
        assertTrue(result.contains("VPN + 以太网"));
        assertTrue(result.contains("微信实际使用的定位来源仍未知"));
        assertFalse(result.contains("2026-10-13"));
        assertFalse(result.contains("21:51"));
    }

    @Test public void doesNotClaimSuccessFromPendingOrIncompleteRound() {
        String truncated = mockedRound("微信发送位置",
                "蓝点和附近地点均为国内实际地区").replace("END_ROUND", "");
        String result = LabCrossAppReadableSummary.summarize(truncated);
        assertTrue(result.contains("还没有完整保存"));
        assertFalse(result.contains("微信实际使用的定位来源"));
    }

    @Test public void missingGmsAfterMakesResultConservative() {
        String tencent = mockedRound("腾讯地图 App",
                "蓝点和附近地点均为 GoGoGo 模拟目标地区").replace(
                        "AFTER GMS_LAST：fixAge=183ms mock=YES", "AFTER GMS_LAST：UNAVAILABLE");
        String result = LabCrossAppReadableSummary.summarize(tencent);
        assertTrue(result.contains("没有足够一致的三通道证据"));
        assertFalse(result.contains("前后均为模拟目标附近"));
    }

    @Test public void incompleteLogDoesNotEraseCompleteEarlierRound() {
        String complete = mockedRound("腾讯地图 App",
                "蓝点和附近地点均为 GoGoGo 模拟目标地区");
        String incomplete = "\n========== CROSS_APP_ROUND at=2026-10-13 22:01:00 ==========\n"
                + "target=微信发送位置\nPASSIVE_BEFORE";
        String result = LabCrossAppReadableSummary.summarize(complete + incomplete);
        assertTrue(result.contains("已完成 1 轮"));
        assertTrue(result.contains("腾讯地图："));
        assertFalse(result.contains("微信："));
    }

    @Test public void leavesCausationUnknownForDifferentApps() {
        String result = LabCrossAppReadableSummary.summarize(
                mockedRound("腾讯地图 App", "蓝点和附近地点均为 GoGoGo 模拟目标地区")
                + mockedRound("微信发送位置", "蓝点和附近地点均为国内实际地区"));
        assertTrue(result.contains("不能据此断言"));
        assertFalse(result.contains("微信使用了腾讯 SDK"));
        assertFalse(result.contains("微信识别了模拟"));
    }

    @Test public void emptyOrNullLogHasFriendlyInstruction() {
        assertTrue(LabCrossAppReadableSummary.summarize("").contains("暂无对照结果"));
        assertTrue(LabCrossAppReadableSummary.summarize(null).contains("暂无对照结果"));
    }
}
