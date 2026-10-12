package com.zcshou.gogogo;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LabLocationEvidenceTimelineTest {
    @Test public void requestAndCallbackHaveRelativeMonotonicTimes() {
        LabLocationEvidenceTimeline events = new LabLocationEvidenceTimeline();
        events.add(LabLocationEvidenceTimeline.Source.GMS_LAST,
                LabLocationEvidenceTimeline.Stage.REQUEST, 5000L);
        events.add(LabLocationEvidenceTimeline.Source.GMS_LAST,
                LabLocationEvidenceTimeline.Stage.CALLBACK_FIX, 5100L, 120L,
                LabLocationEvidenceTimeline.AgeBasis.ANDROID_MONOTONIC,
                LabLocationEvidenceTimeline.MockState.YES);
        String rendered = events.renderRecent(10);
        assertTrue(rendered.contains("+0ms  GMS_LAST  REQUEST"));
        assertTrue(rendered.contains("+100ms  GMS_LAST  CALLBACK_FIX"));
        assertTrue(rendered.contains("fixAge=120ms  basis=ANDROID_MONOTONIC  isMock=YES"));
    }

    @Test public void wallClockEstimatesAreLabelledAsSuch() {
        LabLocationEvidenceTimeline events = new LabLocationEvidenceTimeline();
        events.add(LabLocationEvidenceTimeline.Source.TENCENT,
                LabLocationEvidenceTimeline.Stage.CALLBACK_FIX, 900L, 42L,
                LabLocationEvidenceTimeline.AgeBasis.TENCENT_WALL_ESTIMATE,
                LabLocationEvidenceTimeline.MockState.UNKNOWN);
        assertTrue(events.renderRecent(3).contains("basis=TENCENT_WALL_ESTIMATE"));
        assertFalse(events.renderRecent(3).contains("isMock=YES"));
    }

    @Test public void unknownTimesDoNotBecomeZero() {
        LabLocationEvidenceTimeline events = new LabLocationEvidenceTimeline();
        events.add(LabLocationEvidenceTimeline.Source.GPS,
                LabLocationEvidenceTimeline.Stage.CALLBACK_FIX, -1L, -1L,
                LabLocationEvidenceTimeline.AgeBasis.UNKNOWN,
                LabLocationEvidenceTimeline.MockState.UNKNOWN);
        assertTrue(events.renderRecent(1).contains("+UNKNOWNms"));
        assertTrue(events.renderRecent(1).contains("fixAge=UNKNOWN"));
    }

    @Test public void doesNotExposeCoordinatesOrArbitrarySdkMessages() {
        LabLocationEvidenceTimeline events = new LabLocationEvidenceTimeline();
        events.add(LabLocationEvidenceTimeline.Source.TENCENT,
                LabLocationEvidenceTimeline.Stage.CALLBACK_FAILED, 123L);
        String out = events.renderRecent(5);
        assertFalse(out.contains("latitude"));
        assertFalse(out.contains("longitude"));
        assertFalse(out.contains("SSID"));
        assertFalse(out.contains("SDK_KEY"));
        assertTrue(out.contains("CALLBACK_FAILED"));
    }

    @Test public void boundedAndRecentRowsOnly() {
        LabLocationEvidenceTimeline events = new LabLocationEvidenceTimeline();
        for (int i = 0; i < 100; i++) {
            events.add(LabLocationEvidenceTimeline.Source.NETWORK,
                    LabLocationEvidenceTimeline.Stage.REQUEST, i * 10L);
        }
        assertEquals(48, events.size());
        String one = events.renderRecent(1);
        assertTrue(one.contains("+990ms"));
        assertFalse(one.contains("+980ms"));
    }

    @Test public void resetMakesANewSession() {
        LabLocationEvidenceTimeline events = new LabLocationEvidenceTimeline();
        events.add(LabLocationEvidenceTimeline.Source.GPS,
                LabLocationEvidenceTimeline.Stage.REQUEST, 300L);
        events.clear();
        events.add(LabLocationEvidenceTimeline.Source.GPS,
                LabLocationEvidenceTimeline.Stage.REQUEST, 800L);
        assertEquals(1, events.size());
        assertTrue(events.renderRecent(1).contains("+0ms"));
    }

    @Test public void wallAgeIsAnEstimateAndFutureIsUnknown() {
        assertEquals(500L,
                LabLocationEvidenceTimeline.estimateWallAgeMs(1500L, 1000L));
        assertEquals(-1L,
                LabLocationEvidenceTimeline.estimateWallAgeMs(1000L, 1500L));
        assertEquals(-1L,
                LabLocationEvidenceTimeline.estimateWallAgeMs(1500L, 0L));
    }

    @Test public void callbackFailureNeverRendersAsFix() {
        LabLocationEvidenceTimeline events = new LabLocationEvidenceTimeline();
        events.add(LabLocationEvidenceTimeline.Source.GMS_CURRENT,
                LabLocationEvidenceTimeline.Stage.CALLBACK_FAILED, 450L);
        assertFalse(events.renderRecent(3).contains("fixAge="));
    }
}
