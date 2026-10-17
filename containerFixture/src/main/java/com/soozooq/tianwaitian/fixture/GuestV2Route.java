package com.soozooq.tianwaitian.fixture;

/**
 * Pure Java entrypoint defined exclusively in the trusted separate guest APK.
 * Host forwards a bounded, synthetic Intent action/payload to this contract.
 * Not a system Activity launch, PackageManager service or permission boundary.
 */
public final class GuestV2Route {
    public static final String ACTION = "com.soozooq.tianwaitian.fixture.action.V2_PING";
    public static final String COMPONENT =
            "com.soozooq.tianwaitian.fixture.GuestV2DeclaredActivity";

    private GuestV2Route() {}

    public static String handle(String action, String value) {
        if (!ACTION.equals(action)) throw new IllegalArgumentException("unsupported guest action");
        if (value == null || !value.matches("[A-Za-z0-9_-]{1,40}")) {
            throw new IllegalArgumentException("unsupported guest payload");
        }
        return "GUEST_V2_RESULT:" + new StringBuilder(value).reverse().toString();
    }
}
