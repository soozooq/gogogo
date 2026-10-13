package com.zcshou.gogogo;

import org.junit.Test;
import static org.junit.Assert.*;

public class LabDiagnosticWorkflowTest {
    @Test public void fixedStageTransitionsDoNotLoop() {
        assertEquals(LabDiagnosticWorkflow.PASSIVE,
                LabDiagnosticWorkflow.next(LabDiagnosticWorkflow.BASELINE));
        assertEquals(LabDiagnosticWorkflow.WECHAT_UI,
                LabDiagnosticWorkflow.next(LabDiagnosticWorkflow.PASSIVE));
        assertEquals(LabDiagnosticWorkflow.BACKGROUND_AB,
                LabDiagnosticWorkflow.next(LabDiagnosticWorkflow.WECHAT_UI));
        assertEquals(LabDiagnosticWorkflow.DONE,
                LabDiagnosticWorkflow.next(LabDiagnosticWorkflow.BACKGROUND_AB));
        assertEquals(LabDiagnosticWorkflow.NONE,
                LabDiagnosticWorkflow.next(LabDiagnosticWorkflow.DONE));
    }
    @Test public void cannotMistakeOldAbRunsForThisSession() {
        assertFalse(LabDiagnosticWorkflow.bothAbResultsFromThisRun(1000, 500, 2000));
        assertFalse(LabDiagnosticWorkflow.bothAbResultsFromThisRun(1000, 1500, 0));
        assertTrue(LabDiagnosticWorkflow.bothAbResultsFromThisRun(1000, 1200, 1700));
    }
    @Test public void sectionsAreBoundedAndRetainOrder() {
        String first = LabDiagnosticWorkflow.appendSection("run=42", "BASELINE", "step1");
        String second = LabDiagnosticWorkflow.appendSection(first, "PASSIVE", "step2");
        assertTrue(second.indexOf("BASELINE") < second.indexOf("PASSIVE"));
        String huge = new String(new char[200000]).replace('\0', 'x');
        assertTrue(LabDiagnosticWorkflow.appendSection(second, "HUGE", huge)
                .length() <= LabDiagnosticWorkflow.MAX_TOTAL_CHARS);
    }
    @Test public void unfinishedManualStagesAreActive() {
        assertTrue(LabDiagnosticWorkflow.isActive(LabDiagnosticWorkflow.WECHAT_UI));
        assertTrue(LabDiagnosticWorkflow.isActive(LabDiagnosticWorkflow.BACKGROUND_AB));
        assertFalse(LabDiagnosticWorkflow.isActive(LabDiagnosticWorkflow.DONE));
    }
}
