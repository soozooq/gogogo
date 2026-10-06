package com.zcshou.gogogo;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class LabAttitudeHeadingEngineTest {

    @Test
    public void flatPhoneUsesScreenTopAxis() {
        LabAttitudeHeadingEngine engine = new LabAttitudeHeadingEngine();

        float[] identity = {
                1f, 0f, 0f,
                0f, 1f, 0f,
                0f, 0f, 1f
        };

        LabAttitudeHeadingEngine.Frame frame =
                engine.update(identity, 0f, 0f, 0f);

        assertTrue(frame.available);
        assertEquals("FLAT", frame.posture);
        assertEquals("SCREEN_TOP", frame.axis);
        assertEquals(0f, frame.headingDeg, 0.001f);
    }

    @Test
    public void uprightPhoneFallsBackToScreenForwardAxis() {
        LabAttitudeHeadingEngine engine = new LabAttitudeHeadingEngine();

        // Device +Y points Up. Device -Z points North.
        float[] upright = {
                1f, 0f, 0f,
                0f, 0f, -1f,
                0f, 1f, 0f
        };

        LabAttitudeHeadingEngine.Frame frame =
                engine.update(upright, 90f, 0f, 0f);

        assertTrue(frame.available);
        assertEquals("UPRIGHT", frame.posture);
        assertTrue(frame.axis.startsWith("SCREEN_FORWARD"));
        assertEquals(0f, frame.headingDeg, 0.001f);
    }

    @Test
    public void fallbackChoosesNormalSignClosestToNorthReference() {
        LabAttitudeHeadingEngine engine = new LabAttitudeHeadingEngine();

        // Device -Z points South here, +Z points North.
        float[] uprightFlipped = {
                -1f, 0f, 0f,
                0f, 0f, 1f,
                0f, 1f, 0f
        };

        LabAttitudeHeadingEngine.Frame frame =
                engine.update(uprightFlipped, 90f, 0f, 5f);

        assertTrue(frame.available);
        assertEquals("SCREEN_FORWARD_FLIPPED", frame.axis);
        assertTrue(frame.headingDeg < 20f || frame.headingDeg > 340f);
    }

    @Test
    public void resetClearsContinuityState() {
        LabAttitudeHeadingEngine engine = new LabAttitudeHeadingEngine();

        float[] identity = {
                1f, 0f, 0f,
                0f, 1f, 0f,
                0f, 0f, 1f
        };

        engine.update(identity, 0f, 0f, 0f);
        engine.reset();
        LabAttitudeHeadingEngine.Frame frame =
                engine.update(identity, 0f, 0f, 180f);

        assertEquals(0f, frame.headingDeg, 0.001f);
    }
}
