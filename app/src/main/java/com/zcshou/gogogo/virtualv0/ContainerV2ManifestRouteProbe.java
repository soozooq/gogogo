package com.zcshou.gogogo.virtualv0;

import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Inspect one bundled, self-authored APK's Manifest and execute its separately
 * compiled request handler via DexClassLoader. No uninstalled Activity starts.
 */
public final class ContainerV2ManifestRouteProbe {
    public static final String GUEST_COMPONENT =
            "com.soozooq.tianwaitian.fixture.GuestV2DeclaredActivity";
    public static final String GUEST_ROUTE =
            "com.soozooq.tianwaitian.fixture.GuestV2Route";
    public static final String ACTION =
            "com.soozooq.tianwaitian.fixture.action.V2_PING";

    public static final class Result {
        public final boolean passed;
        public final String sha256;
        public final String response;
        public final String detail;

        public Result(boolean passed, String sha256, String response, String detail) {
            this.passed = passed;
            this.sha256 = sha256;
            this.response = response;
            this.detail = detail;
        }
    }

    private ContainerV2ManifestRouteProbe() {}

    public static Result run(Context context, String action, String payload) {
        String digest = "(unavailable)";
        String stage = "validate request";
        try {
            if (!ACTION.equals(action) || payload == null
                    || !payload.matches("[A-Za-z0-9_-]{1,40}")) {
                throw new SecurityException("outside controlled guest action/payload contract");
            }
            stage = "copy/validate bundled APK";
            try (ContainerV0TrustedGuest guest = ContainerV0TrustedGuest.open(context)) {
                digest = guest.getSha256();
                File verifiedApk = guest.getVerifiedResourceApk();
                stage = "read APK archive manifest GET_ACTIVITIES";
                @SuppressWarnings("deprecation")
                PackageInfo info = context.getPackageManager().getPackageArchiveInfo(
                        verifiedApk.getAbsolutePath(),
                        PackageManager.GET_ACTIVITIES | PackageManager.GET_PERMISSIONS);
                if (info == null
                        || !ContainerV0TrustedGuest.PACKAGE.equals(info.packageName)
                        || info.activities == null || info.activities.length != 1) {
                    throw new SecurityException("guest APK expected exactly one manifest Activity");
                }
                ActivityInfo activity = info.activities[0];
                if (!GUEST_COMPONENT.equals(activity.name) || activity.exported
                        || activity.permission != null) {
                    throw new SecurityException("guest Activity declaration is not expected private component");
                }
                if (info.requestedPermissions != null && info.requestedPermissions.length != 0) {
                    throw new SecurityException("guest fixture suddenly requests permissions");
                }
                stage = "verify guest-only DEX Activity and router";
                ClassLoader loader = guest.getGuestClass().getClassLoader();
                Class<?> declared = Class.forName(GUEST_COMPONENT, false, loader);
                Class<?> router = Class.forName(GUEST_ROUTE, true, loader);
                if (declared.getClassLoader() != loader || router.getClassLoader() != loader
                        || !Activity.class.isAssignableFrom(declared)) {
                    throw new SecurityException("guest component did not resolve from fixture loader");
                }
                stage = "execute separate guest route handler (NOT Activity start)";
                Method handler = router.getMethod("handle", String.class, String.class);
                String response;
                try {
                    response = (String) handler.invoke(null, action, payload);
                } catch (InvocationTargetException failure) {
                    Throwable actual = failure.getTargetException();
                    throw new IllegalStateException("guest handler: "
                            + actual.getClass().getSimpleName() + ": " + actual.getMessage(), actual);
                }
                String expected = "GUEST_V2_RESULT:"
                        + new StringBuilder(payload).reverse().toString();
                if (!expected.equals(response)) {
                    throw new SecurityException("guest route response does not match synthetic contract");
                }
                String detail = "PASS：独立测试 APK Manifest 解析成功\n"
                        + "guestPackage=" + info.packageName + "\n"
                        + "declaredActivity=" + activity.name + "\n"
                        + "activityExported=" + activity.exported
                        + "；guestPermissionCount=0\n"
                        + "guestActivityClassLoader=fixture DEX；guestRouteClassLoader=fixture DEX\n"
                        + "PASS：独立 APK 纯 Java 路由执行，返回受控响应\n"
                        + "注意：这里只是解析声明并通过宿主代理执行处理函数，"
                        + "绝未由系统启动未安装的 guest Activity。";
                return new Result(true, digest, response, detail);
            }
        } catch (Throwable failure) {
            String message = failure.getMessage() == null ? "" : failure.getMessage();
            return new Result(false, digest, "", "FAIL：V2 stage=" + stage
                    + "；cause=" + failure.getClass().getSimpleName()
                    + "；detail=" + message.substring(0, Math.min(350, message.length())));
        }
    }
}
