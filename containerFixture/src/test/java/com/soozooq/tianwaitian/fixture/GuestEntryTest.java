package com.soozooq.tianwaitian.fixture;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public final class GuestEntryTest {
    @Test public void recordAndReadControlledMarker() {
        assertEquals("alpha_42", GuestEntry.record("alpha_42"));
        assertEquals("alpha_42", GuestEntry.read());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectInvalidMarker() {
        GuestEntry.record("../escape");
    }
}
