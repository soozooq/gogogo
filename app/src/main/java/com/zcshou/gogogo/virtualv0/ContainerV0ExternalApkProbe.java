package com.zcshou.gogogo.virtualv0;

import android.content.Context;
import android.os.Build;
import android.os.Process;

/**
 * Runs only the separate APK bundled at build time; never installs arbitrary APKs.
 * Two different DexClassLoaders execute this controlled payload in host process.
 */
public final class ContainerV0ExternalApkProbe {
    private ContainerV0ExternalApkProbe() {}

    public static String run(Context context) throws Exception {
        try (ContainerV0TrustedGuest a = ContainerV0TrustedGuest.open(context);
             ContainerV0TrustedGuest b = ContainerV0TrustedGuest.open(context)) {
            if (a.getGuestClass() == b.getGuestClass()
                    || !a.getSha256().equals(b.getSha256())) {
                throw new AssertionError("guest class identity or APK digest mismatch");
            }

            String nonce = Long.toHexString(System.nanoTime());
            String expectedA = "A_" + nonce;
            String expectedB = "B_" + nonce;
            if (!expectedA.equals(a.record(expectedA))
                    || !expectedB.equals(b.record(expectedB))) {
                throw new AssertionError("guest wrote an unexpected marker");
            }
            if (!expectedA.equals(a.read()) || !expectedB.equals(b.read())) {
                throw new AssertionError("guest state mixed between two classloaders");
            }
            String lastA = expectedA + "_again";
            a.record(lastA);
            if (!lastA.equals(a.read()) || !expectedB.equals(b.read())) {
                throw new AssertionError("A -> B -> A update modified B");
            }

            return "PASS：独立测试 APK 解析、DEX 装载与方法执行成功\n"
                    + "APK package：" + ContainerV0TrustedGuest.PACKAGE + "\n"
                    + "APK SHA-256：" + a.getSha256() + "\n"
                    + "PASS：两份 APK guest Class 不同；静态状态 A/B 不串线\n"
                    + "PASS：A → B → A 后 B 保持原标记\n"
                    + "Android API：" + Build.VERSION.SDK_INT + "\n"
                    + "宿主 PID：" + Process.myPid() + "；UID：" + Process.myUid() + "\n"
                    + "限制：仍在宿主进程/UID 运行；没有启动 Guest Activity。";
        }
    }
}
