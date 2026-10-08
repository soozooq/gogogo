package com.zcshou.gogogo;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class LabAutoExperimentStoreTest {

    @Test
    public void bothStableProducesStableVerdict() {
        LabAutoExperimentStore.Result wechat =
                result(LabAutoExperimentStore.TYPE_WECHAT, 900L, 700L);
        LabAutoExperimentStore.Result control =
                result(LabAutoExperimentStore.TYPE_CONTROL, 800L, 650L);

        assertEquals(
                "BOTH_STABLE",
                LabAutoExperimentStore.compare(wechat, control).verdict);
    }

    @Test
    public void bothBadAndSimilarLooksLikeOemBackground() {
        LabAutoExperimentStore.Result wechat =
                result(LabAutoExperimentStore.TYPE_WECHAT, 5200L, 4300L);
        LabAutoExperimentStore.Result control =
                result(LabAutoExperimentStore.TYPE_CONTROL, 4700L, 3900L);

        assertEquals(
                "OEM_BACKGROUND_GENERAL",
                LabAutoExperimentStore.compare(wechat, control).verdict);
    }

    @Test
    public void wechatMuchWorseIsAssociatedSlowdown() {
        LabAutoExperimentStore.Result wechat =
                result(LabAutoExperimentStore.TYPE_WECHAT, 9000L, 7000L);
        LabAutoExperimentStore.Result control =
                result(LabAutoExperimentStore.TYPE_CONTROL, 1200L, 900L);

        assertEquals(
                "WECHAT_ASSOCIATED_SLOWDOWN",
                LabAutoExperimentStore.compare(wechat, control).verdict);
    }

    @Test
    public void missingOneSideRequestsBoth() {
        LabAutoExperimentStore.Result wechat =
                result(LabAutoExperimentStore.TYPE_WECHAT, 900L, 700L);

        assertEquals(
                "NEED_BOTH",
                LabAutoExperimentStore.compare(wechat, null).verdict);
    }

    private static LabAutoExperimentStore.Result result(
            String type,
            long worstGap,
            long p99) {
        return new LabAutoExperimentStore.Result(
                type,
                true,
                1L,
                60000L,
                worstGap,
                p99,
                "TEST",
                "TEST",
                true,
                0);
    }
}
