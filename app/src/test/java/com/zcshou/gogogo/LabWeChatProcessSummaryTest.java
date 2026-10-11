package com.zcshou.gogogo;

import org.junit.Test;
import static org.junit.Assert.*;

public class LabWeChatProcessSummaryTest {
    private static final String PS = "USER PID NAME\\n"
            + "u0_a123 1000 com.tencent.mm\\n"
            + "u0_a123 1001 com.tencent.mm:tools\\n"
            + "u999_a123 2000 com.tencent.mm\\n"
            + "u0_a321 3000 com.tencent.mmpay\\n"
            + "u0_a321 3001 com.other.app\\n";

    @Test public void onlyIncludesActualWeChatProcessNames() {
        String report = LabWeChatProcessSummary.fromPs(PS, true);
        assertTrue(report.contains("3 条"));
        assertTrue(report.contains("u999_a123"));
        assertTrue(report.contains("com.tencent.mm:tools"));
        assertFalse(report.contains("com.tencent.mmpay"));
        assertFalse(report.contains("com.other.app"));
    }

    @Test public void errorsAreNotMistakenForNoProcesses() {
        assertTrue(LabWeChatProcessSummary.fromPs(PS, false).contains("不可用"));
        assertTrue(LabWeChatProcessSummary.fromPs(null, true).contains("不可用"));
    }

    @Test public void emptyResultIsNotForceStopProof() {
        String report = LabWeChatProcessSummary.fromPs("USER PID NAME\\n", true);
        assertTrue(report.contains("不代表已强停"));
    }
}
