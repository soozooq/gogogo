package com.zcshou.gogogo;

import org.junit.Test;
import static org.junit.Assert.*;

public final class LabFeedbackReportTest {
    @Test public void historyIndexOmitsNestedExperimentsNotesAndOldInstructions() {
        String saved = "created_at=2026-10-13 18:45:58\nprivate_address=secret\n"
                + "GOGOGO_SUPPORT_LOG_V1\ncoord=123.456,45.678\n"
                + "下一步：请用 Consumer Matrix\n"
                + "HUMAN_OBSERVATION at=2026-10-13 21:53:34.626\n";
        String index = LabFeedbackReport.historyIndex("CROSS_APP", saved);
        assertTrue(index.contains("last_timestamp_text=2026-10-13 21:53:34.626"));
        assertTrue(index.contains("time_scope=STORED_TEXT_ONLY"));
        for (String old : new String[]{"secret", "123.456", "Consumer Matrix", "GOGOGO_SUPPORT_LOG"}) {
            assertFalse(index.contains(old));
        }
    }

    @Test public void emptyAndUnreadableRecordsAreDistinct() {
        assertEquals("ACTIVE: saved=NO\n", LabFeedbackReport.historyIndex("ACTIVE", "NO_RECORD\n"));
        assertEquals("ACTIVE: saved=NO\n", LabFeedbackReport.historyIndex("ACTIVE", null));
        assertEquals("ACTIVE: saved=UNKNOWN; read=UNAVAILABLE\n",
                LabFeedbackReport.historyIndex("ACTIVE", "READ_ERROR_PrivateDetail\n"));
        assertFalse(LabFeedbackReport.historyIndex("ACTIVE", "no timestamp here").contains("saved=NO"));
    }

    @Test public void missingTimestampsAndMixedOrderDoNotBecomeCurrentResults() {
        String index = LabFeedbackReport.historyIndex("WORKFLOW",
                "2026-10-23T10:00:00.000+0800\nembedded older report 2026-10-13 12:00:00");
        assertTrue(index.contains("last_timestamp_text=2026-10-13 12:00:00"));
        assertTrue(index.contains("STORED_TEXT_ONLY"));
        assertTrue(LabFeedbackReport.historyIndex("WORKFLOW", "undated record")
                .contains("last_timestamp_text=UNKNOWN"));
    }

    @Test public void currentEvidencePrecedesHistoryAndKeepsUnknownAppResult() {
        String report = LabFeedbackReport.render("version=sample\n", "mock_appop=YES",
                "GPS mock=YES\nno_active_fix_requested=true", "LAST_START_SEEN_NOT_PROOF_RUNNING", "ACTIVE: saved=NO");
        assertTrue(report.contains("other_app_result=UNKNOWN"));
        assertTrue(report.indexOf("GPS mock=YES") < report.indexOf("LAST_START_SEEN"));
        assertTrue(report.indexOf("LAST_START_SEEN") < report.indexOf("ACTIVE: saved=NO"));
        assertTrue(report.endsWith("END_GOGOGO_FEEDBACK_LOG_V2\n"));
    }

    @Test public void oversizedFeedbackRetainsCurrentEvidenceFooterAndValidUnicode() {
        String report = LabFeedbackReport.render("version=sample\n", "mock_appop=YES",
                "CURRENT_CACHE", "\uD83D\uDE00".repeat(20_000), "history");
        assertTrue(report.length() <= LabFeedbackReport.MAX_CHARACTERS);
        assertTrue(report.contains("CURRENT_CACHE"));
        assertTrue(report.contains("[FEEDBACK_TRUNCATED]"));
        assertTrue(report.endsWith("END_GOGOGO_FEEDBACK_LOG_V2\n"));
        for (int i = 0; i < report.length(); i++) {
            if (Character.isHighSurrogate(report.charAt(i))) {
                assertTrue(i + 1 < report.length() && Character.isLowSurrogate(report.charAt(++i)));
            } else assertFalse(Character.isLowSurrogate(report.charAt(i)));
        }
    }
}
