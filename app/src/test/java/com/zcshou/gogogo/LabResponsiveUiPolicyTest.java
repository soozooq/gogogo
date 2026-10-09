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

    @Test public void missingWidthFailsSafe() {
        assertTrue(LabResponsiveUiPolicy.stackHomeActions(0, 1f));
    }
}
