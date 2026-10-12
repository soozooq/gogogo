package com.zcshou.gogogo;

import org.junit.Test;
import static org.junit.Assert.*;

public class LabPrivacyAuditRulesTest {
    @Test public void vpnAbsenceDoesNotClaimRealCountry() {
        assertTrue(LabPrivacyAuditRules.vpnState(true, false).contains("WARNING"));
        assertTrue(LabPrivacyAuditRules.vpnState(false, false).contains("UNKNOWN"));
        assertTrue(LabPrivacyAuditRules.vpnState(true, true).contains("OBSERVED"));
    }
    @Test public void unreachableIpv6IsNotProofOfNoLeak() {
        assertEquals("NOT_TESTED", LabPrivacyAuditRules.familyState(false, false));
        assertEquals("NOT_REACHABLE_OR_BLOCKED_OR_FAILED",
                LabPrivacyAuditRules.familyState(true, false));
    }
    @Test public void timezoneDifferenceNotProofOfLocationLeak() {
        String different = LabPrivacyAuditRules.comparison(
                "Asia/Shanghai", "America/Los_Angeles", true);
        assertTrue(different.contains("DIFFERENT"));
        assertTrue(different.contains("不等于真实国家泄露"));
        assertTrue(LabPrivacyAuditRules.comparison(
                "Asia/Shanghai", "", false).contains("UNKNOWN"));
    }
    @Test public void otherAppsNeverClaimedVerified() {
        assertTrue(LabPrivacyAuditRules.conclusion(true, true).contains("其他应用"));
        assertTrue(LabPrivacyAuditRules.conclusion(true, false).contains("尚未验证"));
    }
}
