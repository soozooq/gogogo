package com.zcshou.gogogo.virtualv0;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.os.Build;
import android.os.Process;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.security.MessageDigest;
import java.util.zip.ZipFile;

import dalvik.system.DexClassLoader;

/**
 * Executes only the build-bundled, source-controlled standalone fixture APK.
 * It never accepts a user-supplied APK or loads unknown executable code.
 * This is an APK/DexClassLoader smoke test, NOT Activity/Service virtualization.
 */
public final class ContainerV0ExternalApkProbe {
    public static final String ASSET = "twt-fixture-v0.apk";
    public static final String PACKAGE = "com.soozooq.tianwaitian.fixture";
    public static final String ENTRY = PACKAGE + ".GuestEntry";
    private static final long MAX_APK_BYTES = 10L * 1024 * 1024;

    private ContainerV0ExternalApkProbe() {}

    public static String run(Context context) throws Exception {
        Context app = context.getApplicationContext();
        File cache = new File(app.getCodeCacheDir(), "virtual_container_v0_fixture");
        if (!cache.isDirectory() && !cache.mkdirs()) {
            throw new IllegalStateException("Cannot create fixture cache");
        }

        File apk = File.createTempFile("v0-guest-", ".apk", cache);
        try {
            String sha256 = copyTrustedAsset(app, apk);
            PackageInfo info = app.getPackageManager().getPackageArchiveInfo(
                    apk.getAbsolutePath(), 0);
            if (info == null || !PACKAGE.equals(info.packageName)) {
                throw new SecurityException("Fixture APK package mismatch or invalid archive");
            }
            try (ZipFile zip = new ZipFile(apk)) {
                if (zip.getEntry("classes.dex") == null) {
                    throw new SecurityException("Fixture APK contains no classes.dex");
                }
            }
            // No guest code is permitted in the host dex or parent classloader.
            try {
                app.getClassLoader().loadClass(ENTRY);
                throw new SecurityException("Fixture class unexpectedly present in host dex");
            } catch (ClassNotFoundException expected) {
                // Good: the host does not already contain the guest implementation.
            }

            DexClassLoader a = new DexClassLoader(apk.getAbsolutePath(),
                    cache.getAbsolutePath(), null, app.getClassLoader());
            DexClassLoader b = new DexClassLoader(apk.getAbsolutePath(),
                    cache.getAbsolutePath(), null, app.getClassLoader());
            Class<?> classA = Class.forName(ENTRY, true, a);
            Class<?> classB = Class.forName(ENTRY, true, b);
            if (classA == classB || classA.getClassLoader() != a
                    || classB.getClassLoader() != b) {
                throw new AssertionError("Separate APK guest classes did not isolate");
            }
            Method recordA = classA.getMethod("record", String.class);
            Method recordB = classB.getMethod("record", String.class);
            Method readA = classA.getMethod("read");
            Method readB = classB.getMethod("read");

            String nonce = Long.toHexString(System.nanoTime());
            String expectedA = "A_" + nonce;
            String expectedB = "B_" + nonce;
            if (!expectedA.equals(recordA.invoke(null, expectedA))
                    || !expectedB.equals(recordB.invoke(null, expectedB))) {
                throw new AssertionError("Fixture guest method returned wrong marker");
            }
            if (!expectedA.equals(readA.invoke(null))
                    || !expectedB.equals(readB.invoke(null))) {
                throw new AssertionError("Fixture state leaked between ClassLoaders");
            }
            String latestA = expectedA + "_again";
            recordA.invoke(null, latestA);
            if (!latestA.equals(readA.invoke(null))
                    || !expectedB.equals(readB.invoke(null))) {
                throw new AssertionError("A -> B -> A update corrupted B");
            }

            return "PASS：独立测试 APK 解析、DEX 装载与方法执行成功\n"
                    + "APK package：" + info.packageName + "\n"
                    + "APK SHA-256：" + sha256 + "\n"
                    + "PASS：两份 APK guest Class 不同；静态状态 A/B 不串线\n"
                    + "PASS：A → B → A 后 B 保持原标记\n"
                    + "Android API：" + Build.VERSION.SDK_INT + "\n"
                    + "宿主 PID：" + Process.myPid() + "；UID：" + Process.myUid() + "\n"
                    + "限制：仍在宿主进程/UID 运行；没有启动 Guest Activity。";
        } finally {
            // Temp asset copy is not retained after this single user-triggered run.
            // On Linux, loaded dex handles remain valid after the file is unlinked.
            if (apk.exists()) apk.delete();
        }
    }

    private static String copyTrustedAsset(Context app, File dest) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        long length = 0;
        // Mark DCL code read-only BEFORE writing bytes (Android 14+ requirement).
        try (InputStream in = app.getAssets().open(ASSET);
             FileOutputStream out = new FileOutputStream(dest)) {
            if (!dest.setReadOnly()) throw new SecurityException("Cannot mark code read-only");
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) != -1) {
                length += n;
                if (length > MAX_APK_BYTES) throw new SecurityException("Fixture too large");
                digest.update(buffer, 0, n);
                out.write(buffer, 0, n);
            }
            out.getFD().sync();
        }
        if (length < 1024) throw new SecurityException("Missing/truncated fixture APK");
        StringBuilder hex = new StringBuilder();
        for (byte value : digest.digest()) {
            hex.append(Character.forDigit((value >> 4) & 0xf, 16));
            hex.append(Character.forDigit(value & 0xf, 16));
        }
        return hex.toString();
    }
}
