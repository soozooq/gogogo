package com.zcshou.gogogo;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LabHeadingIntelligenceEngineTest {

    @Test
    public void locksAbsoluteNorthWhenMagneticFieldIsHealthy() {
        LabHeadingIntelligenceEngine engine = new LabHeadingIntelligenceEngine();
        long t = 1_000_000_000L;

        engine.onMagneticField(30f, 20f, 35f,
                LabHeadingIntelligenceEngine.ACCURACY_HIGH, t);
        engine.onGameHeading(100f, t + 1);
        LabHeadingIntelligenceEngine.Frame frame = engine.onAbsoluteHeading(
                120f,
                LabHeadingIntelligenceEngine.ACCURACY_HIGH,
                t + 2);

        assertEquals("MAG_LOCKED", frame.state);
        assertTrue(frame.available);
        assertFalse(frame.magneticDisturbed);
        assertEquals(120f, frame.fusedHeadingDeg, 0.5f);
        assertTrue(frame.confidence > 0.8);
    }

    @Test
    public void switchesToInertialHoldDuringMagneticDisturbance() {
        LabHeadingIntelligenceEngine engine = new LabHeadingIntelligenceEngine();
        long t = 1_000_000_000L;

        engine.onMagneticField(30f, 20f, 35f,
                LabHeadingIntelligenceEngine.ACCURACY_HIGH, t);
        engine.onGameHeading(100f, t + 1);
        engine.onAbsoluteHeading(120f,
                LabHeadingIntelligenceEngine.ACCURACY_HIGH, t + 2);

        // Large magnetic magnitude jump marks the magnetic reference as disturbed.
        engine.onMagneticField(200f, 0f, 0f,
                LabHeadingIntelligenceEngine.ACCURACY_LOW, t + 3);
        LabHeadingIntelligenceEngine.Frame frame =
                engine.onGameHeading(110f, t + 4);

        assertEquals("INERTIAL_HOLD", frame.state);
        assertTrue(frame.magneticDisturbed);
        assertEquals(130f, frame.fusedHeadingDeg, 1.0f);
    }

    @Test
    public void confidenceDecaysDuringLongInertialHold() {
        LabHeadingIntelligenceEngine engine = new LabHeadingIntelligenceEngine();
        long t = 1_000_000_000L;

        engine.onMagneticField(30f, 20f, 35f,
                LabHeadingIntelligenceEngine.ACCURACY_HIGH, t);
        engine.onGameHeading(0f, t + 1);
        engine.onAbsoluteHeading(10f,
                LabHeadingIntelligenceEngine.ACCURACY_HIGH, t + 2);
        engine.onMagneticField(200f, 0f, 0f,
                LabHeadingIntelligenceEngine.ACCURACY_LOW, t + 3);

        double early = engine.snapshot(t + 5_000_000_000L).confidence;
        double late = engine.snapshot(t + 40_000_000_000L).confidence;

        assertTrue(early > late);
        assertTrue(late >= 0.35);
    }

    @Test
    public void relativeOnlyWorksWithoutMagneticReference() {
        LabHeadingIntelligenceEngine engine = new LabHeadingIntelligenceEngine();
        LabHeadingIntelligenceEngine.Frame frame =
                engine.onGameHeading(42f, 100L);

        assertEquals("RELATIVE_ONLY", frame.state);
        assertEquals(42f, frame.fusedHeadingDeg, 0.001f);
        assertTrue(frame.available);
    }
}
