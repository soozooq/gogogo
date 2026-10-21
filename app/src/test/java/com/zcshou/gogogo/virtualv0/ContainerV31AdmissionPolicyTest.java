package com.zcshou.gogogo.virtualv0;

import org.junit.Test;
import static org.junit.Assert.*;

public class ContainerV31AdmissionPolicyTest {
    private static final String PIN = "0123456789abcdef".repeat(4);

    @Test public void exactBuildDigestRequired() {
        assertTrue(ContainerV31AdmissionPolicy.matchesDigest(PIN, PIN));
        assertFalse(ContainerV31AdmissionPolicy.matchesDigest(PIN, "f" + PIN.substring(1)));
        assertFalse(ContainerV31AdmissionPolicy.matchesDigest(PIN, PIN.toUpperCase()));
        assertFalse(ContainerV31AdmissionPolicy.matchesDigest(null, PIN));
        assertFalse(ContainerV31AdmissionPolicy.matchesDigest(PIN, ""));
        assertFalse(ContainerV31AdmissionPolicy.matchesDigest("DUMMY", "DUMMY"));
    }

    @Test public void packageNameAloneCannotAdmitAnApk() {
        assertTrue(identity(ContainerV31AdmissionPolicy.PACKAGE, 1,
                ContainerV31AdmissionPolicy.ACTIVITY, 1, 0, 0));
        assertFalse(identity("com.tencent.mm", 1, ContainerV31AdmissionPolicy.ACTIVITY, 1, 0, 0));
        assertFalse(identity(ContainerV31AdmissionPolicy.PACKAGE, 2,
                ContainerV31AdmissionPolicy.ACTIVITY, 1, 0, 0));
        assertFalse(identity(ContainerV31AdmissionPolicy.PACKAGE, 1, "other.Entry", 1, 0, 0));
    }

    @Test public void extraPermissionsAndComponentsAreRejected() {
        assertFalse(identity(ContainerV31AdmissionPolicy.PACKAGE, 1,
                ContainerV31AdmissionPolicy.ACTIVITY, 2, 0, 0));
        assertFalse(identity(ContainerV31AdmissionPolicy.PACKAGE, 1,
                ContainerV31AdmissionPolicy.ACTIVITY, 1, 1, 0));
        assertFalse(identity(ContainerV31AdmissionPolicy.PACKAGE, 1,
                ContainerV31AdmissionPolicy.ACTIVITY, 1, 0, 1));
    }

    @Test public void importSizeHasBothBounds() {
        assertFalse(ContainerV31AdmissionPolicy.validLength(-1));
        assertFalse(ContainerV31AdmissionPolicy.validLength(0));
        assertFalse(ContainerV31AdmissionPolicy.validLength(1023));
        assertTrue(ContainerV31AdmissionPolicy.validLength(1024));
        assertTrue(ContainerV31AdmissionPolicy.validLength(ContainerV31AdmissionPolicy.MAX_BYTES));
        assertFalse(ContainerV31AdmissionPolicy.validLength(ContainerV31AdmissionPolicy.MAX_BYTES + 1));
    }

    private static boolean identity(String name, long version, String entry,
            int activities, int permissions, int components) {
        return ContainerV31AdmissionPolicy.matchesIdentity(name, version, entry,
                activities, permissions, components);
    }
}
