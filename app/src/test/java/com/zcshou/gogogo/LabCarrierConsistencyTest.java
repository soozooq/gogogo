package com.zcshou.gogogo;

import org.junit.Test;
import static org.junit.Assert.*;

public class LabCarrierConsistencyTest {
    @Test public void reproducesUserScreenshotMismatch() {
        assertEquals("CN", LabCarrierConsistency.countryFromMccMnc("46002"));
        assertEquals("CN", LabCarrierConsistency.countryFromMccMnc("46000"));
        assertEquals("INCONSISTENT_SIM_ISO_US_MCC_CN",
                LabCarrierConsistency.check("us", "46002"));
        assertEquals("CONSISTENT_US",
                LabCarrierConsistency.check("US", "310260"));
        assertEquals("CONSISTENT_CN",
                LabCarrierConsistency.check("CN", "46000"));
    }
    @Test public void unknownMustNotBecomeLocationClaim() {
        assertEquals("UNKNOWN", LabCarrierConsistency.countryFromMccMnc("xyz"));
        assertEquals("UNKNOWN_MCC_COUNTRY_OR_SIM_ISO",
                LabCarrierConsistency.check("US", ""));
        assertEquals("UNKNOWN_MCC_COUNTRY_OR_SIM_ISO",
                LabCarrierConsistency.check("US", "23410"));
    }
}
