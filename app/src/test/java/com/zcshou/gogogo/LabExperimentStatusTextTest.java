package com.zcshou.gogogo;

import org.junit.Test;
import static org.junit.Assert.*;

public final class LabExperimentStatusTextTest {
    @Test public void serviceRuntimeFlagIsNotDeclaredFullInjectionSuccess() {
        assertTrue(LabExperimentStatusText.serviceFlag(true).contains("不等于"));
        assertTrue(LabExperimentStatusText.serviceFlag(false).contains("STOPPED"));
        assertTrue(LabExperimentStatusText.serviceFlag(null).contains("无法读取"));
    }

    @Test public void wechatStoppedIsNeverClaimedToBeProcessProof() {
        assertTrue(LabExperimentStatusText.weChatStopped(true).contains("不代表"));
        assertTrue(LabExperimentStatusText.weChatStopped(false).contains("不能据此"));
        assertTrue(LabExperimentStatusText.weChatStopped(null).contains("无法读取"));
    }
}
