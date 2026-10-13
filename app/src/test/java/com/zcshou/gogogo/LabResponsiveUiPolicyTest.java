package com.zcshou.gogogo;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class LabResponsiveUiPolicyTest {
    @Test public void narrowAndBoundaryWidthsStack() {
        assertTrue(LabResponsiveUiPolicy.stackHomeActions(320, 1f));
        assertTrue(LabResponsiveUiPolicy.stackHomeActions(360, 1f));
        assertFalse(LabResponsiveUiPolicy.stackHomeActions(361, 1f));
    }

    @Test public void largeFontAlwaysStacks() {
        assertTrue(LabResponsiveUiPolicy.stackHomeActions(420, 1.30f));
        assertTrue(LabResponsiveUiPolicy.stackHomeActions(600, 1.60f));
        assertFalse(LabResponsiveUiPolicy.stackHomeActions(420, 1.29f));
    }

    @Test public void constellationLauncherKeepsTwoButtonsOnOrdinaryPhones() {
        assertFalse(LabResponsiveUiPolicy.stackTianwaitianLauncherActions(320, 1.0f));
        assertFalse(LabResponsiveUiPolicy.stackTianwaitianLauncherActions(360, 1.0f));
        assertFalse(LabResponsiveUiPolicy.stackTianwaitianLauncherActions(393, 1.2f));
        assertTrue(LabResponsiveUiPolicy.stackTianwaitianLauncherActions(319, 1.0f));
        assertTrue(LabResponsiveUiPolicy.stackTianwaitianLauncherActions(400, 1.35f));
        assertTrue(LabResponsiveUiPolicy.stackTianwaitianLauncherActions(0, 1.0f));
    }

    @Test public void expandedDetailsDoNotFillShortPhones() {
        assertTrue(LabResponsiveUiPolicy.tianwaitianHomeDetailsMaxHeightDp(320) <= 110);
        assertTrue(LabResponsiveUiPolicy.tianwaitianHomeDetailsMaxHeightDp(480) <= 240);
        assertTrue(LabResponsiveUiPolicy.tianwaitianHomeDetailsMaxHeightDp(800) <= 300);
        assertTrue(LabResponsiveUiPolicy.tianwaitianHomeDetailsMaxHeightDp(1800) <= 300);
        assertTrue(LabResponsiveUiPolicy.tianwaitianHomeDetailsMaxHeightDp(0) >= 100);
    }

    @Test public void homeCardAlwaysLeavesRoomForMapControls() {
        for (int width : new int[] {320, 360, 390, 480, 720, 1200}) {
            int card = LabResponsiveUiPolicy.tianwaitianHomeCardWidthDp(width);
            assertTrue(card <= 356);
            assertTrue(card + 124 <= width);
        }
    }

    @Test public void shortPlacePickerAvoidsOverlongHeaders() {
        assertTrue(LabResponsiveUiPolicy.tianwaitianCompactPlacePicker(320));
        assertTrue(LabResponsiveUiPolicy.tianwaitianCompactPlacePicker(599));
        assertFalse(LabResponsiveUiPolicy.tianwaitianCompactPlacePicker(600));
        assertFalse(LabResponsiveUiPolicy.tianwaitianCompactPlacePicker(900));
    }

    @Test public void missingWidthFailsSafe() {
        assertTrue(LabResponsiveUiPolicy.stackHomeActions(0, 1f));
    }
}
