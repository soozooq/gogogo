package com.soozooq.tianwaitian.fixture;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public final class GuestV2RouteTest {
    @Test public void trustedIntentRoundtrip() {
        assertEquals("GUEST_V2_RESULT:321-olleH",
                GuestV2Route.handle(GuestV2Route.ACTION, "Hello-123"));
    }
    @Test(expected=IllegalArgumentException.class)
    public void unknownActionRejected() {
        GuestV2Route.handle("android.intent.action.VIEW", "Hello-123");
    }
    @Test(expected=IllegalArgumentException.class)
    public void arbitraryUriRejected() {
        GuestV2Route.handle(GuestV2Route.ACTION, "https://example.invalid");
    }
    @Test(expected=IllegalArgumentException.class)
    public void oversizedPayloadRejected() {
        GuestV2Route.handle(GuestV2Route.ACTION, new String(new char[41]).replace('\0', 'A'));
    }
    @Test(expected=IllegalArgumentException.class)
    public void emptyPayloadRejected() {
        GuestV2Route.handle(GuestV2Route.ACTION, "");
    }
}
