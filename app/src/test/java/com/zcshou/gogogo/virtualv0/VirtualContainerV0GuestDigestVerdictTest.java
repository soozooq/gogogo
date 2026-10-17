package com.zcshou.gogogo.virtualv0;

import org.junit.Test;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class VirtualContainerV0GuestDigestVerdictTest {
    private static final String A = "0123456789abcdef".repeat(4);
    private static final String B = "fedcba9876543210".repeat(4);

    @Test public void acceptsMatchingLowercaseSha256() {
        assertTrue(VirtualContainerV0GuestDigestVerdict.passed(A, A));
    }

    @Test public void rejectsDifferentFixtureApks() {
        assertFalse(VirtualContainerV0GuestDigestVerdict.passed(A, B));
    }

    @Test public void rejectsAbsentHashes() {
        assertFalse(VirtualContainerV0GuestDigestVerdict.passed(null, A));
        assertFalse(VirtualContainerV0GuestDigestVerdict.passed(A, null));
        assertFalse(VirtualContainerV0GuestDigestVerdict.passed("", ""));
    }

    @Test public void rejectsNonSha256Hashes() {
        assertFalse(VirtualContainerV0GuestDigestVerdict.passed("abcdef", "abcdef"));
        assertFalse(VirtualContainerV0GuestDigestVerdict.passed(A.toUpperCase(), A.toUpperCase()));
        assertFalse(VirtualContainerV0GuestDigestVerdict.passed(A + "0", A + "0"));
    }
}
