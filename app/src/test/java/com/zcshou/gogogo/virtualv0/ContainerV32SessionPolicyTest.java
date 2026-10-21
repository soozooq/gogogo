package com.zcshou.gogogo.virtualv0;

import org.junit.Test;
import static org.junit.Assert.*;

public class ContainerV32SessionPolicyTest {
    @Test public void acceptsBoundedPrimitiveApplicationState() {
        assertTrue(ContainerV32SessionPolicy.isValid(1, 0, "", "home"));
        assertTrue(ContainerV32SessionPolicy.isValid(1, 100000, "字".repeat(500), "details"));
    }

    @Test public void rejectsTypeConfusionAndMissingValues() {
        assertFalse(ContainerV32SessionPolicy.isValid("1", 1, "note", "home"));
        assertFalse(ContainerV32SessionPolicy.isValid(1, 1L, "note", "home"));
        assertFalse(ContainerV32SessionPolicy.isValid(1, 1, null, "home"));
        assertFalse(ContainerV32SessionPolicy.isValid(1, 1, new Object(), "home"));
    }

    @Test public void rejectsUnknownSchemaAndOutOfRangeState() {
        assertFalse(ContainerV32SessionPolicy.isValid(2, 1, "", "home"));
        assertFalse(ContainerV32SessionPolicy.isValid(1, -1, "", "home"));
        assertFalse(ContainerV32SessionPolicy.isValid(1, 100001, "", "home"));
        assertFalse(ContainerV32SessionPolicy.isValid(1, 1, "x".repeat(501), "home"));
        assertFalse(ContainerV32SessionPolicy.isValid(1, 1, "", "../../code.apk"));
    }
}
