package com.zcshou.gogogo;

import org.junit.Test;
import static org.junit.Assert.*;

public class LabVpnGeoPolicyTest {
    @Test public void rejectsUnknownInvalidAndZeroZero() {
        assertFalse(LabVpnGeoPolicy.valid(Double.NaN, 20, "US"));
        assertFalse(LabVpnGeoPolicy.valid(91, 20, "US"));
        assertFalse(LabVpnGeoPolicy.valid(20, 181, "US"));
        assertFalse(LabVpnGeoPolicy.valid(0, 0, "US"));
        assertFalse(LabVpnGeoPolicy.valid(34.05, -118.2, ""));
        assertTrue(LabVpnGeoPolicy.valid(34.05, -118.2, "United States"));
        assertTrue(LabVpnGeoPolicy.valid(-33.9, 151.2, "Australia"));
    }

    @Test public void neverAppliesWithoutOptInRealVpnOrFixedMode() {
        assertFalse(LabVpnGeoPolicy.canApply(false, true, true, true));
        assertFalse(LabVpnGeoPolicy.canApply(true, false, true, true));
        assertFalse(LabVpnGeoPolicy.canApply(true, true, false, true));
        assertFalse(LabVpnGeoPolicy.canApply(true, true, true, false));
        assertTrue(LabVpnGeoPolicy.canApply(true, true, true, true));
    }
}
