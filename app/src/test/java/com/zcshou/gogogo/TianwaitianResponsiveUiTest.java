package com.zcshou.gogogo;

import org.junit.Test;
import static org.junit.Assert.*;

/** Independent of Android UI so compact and landscape layout rules stay reproducible. */
public final class TianwaitianResponsiveUiTest {
    @Test public void cardReservesLegacyMapControlRail() {
        assertEquals(196, LabResponsiveUiPolicy.tianwaitianHomeCardWidthDp(320));
        assertEquals(236, LabResponsiveUiPolicy.tianwaitianHomeCardWidthDp(360));
        assertEquals(356, LabResponsiveUiPolicy.tianwaitianHomeCardWidthDp(800));
        assertEquals(120, LabResponsiveUiPolicy.tianwaitianHomeCardWidthDp(240));
    }

    @Test public void landscapeAndShortScreensCollapseButPortraitCanExpand() {
        assertFalse(LabResponsiveUiPolicy.tianwaitianHomeDetailsDefaultExpanded(359));
        assertFalse(LabResponsiveUiPolicy.tianwaitianHomeDetailsDefaultExpanded(599));
        assertTrue(LabResponsiveUiPolicy.tianwaitianHomeDetailsDefaultExpanded(600));
        assertTrue(LabResponsiveUiPolicy.tianwaitianHomeDetailsDefaultExpanded(780));
    }

    @Test public void largeTextOrNarrowCardsUseStackedActions() {
        assertTrue(LabResponsiveUiPolicy.stackTianwaitianHomeActions(196, 1.0f));
        assertFalse(LabResponsiveUiPolicy.stackTianwaitianHomeActions(236, 1.0f));
        assertTrue(LabResponsiveUiPolicy.stackTianwaitianHomeActions(356, 1.30f));
        assertFalse(LabResponsiveUiPolicy.stackTianwaitianHomeActions(356, 1.0f));
    }
}
