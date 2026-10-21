package com.zcshou.gogogo.virtualv0;

import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import dalvik.system.DexClassLoader;

/**
 * Explicit SAF import of exactly the demo built alongside this host APK.
 * The build supplies an exact SHA-256 pin, not a package-name-only allowlist.
 * No external DEX is loaded until the complete private copy matches that pin.
 * This is a trusted-code UI bridge sharing host UID, not an untrusted sandbox.
 */
public final class ContainerV31TrustedDemo implements AutoCloseable {
    private final File apk;
    private final String sha256;
    private Class<?> guestClass;
    private boolean closed;

    private ContainerV31TrustedDemo(File apk, String sha256, Class<?> entry) {
        this.apk = apk;
        this.sha256 = sha256;
        this.guestClass = entry;
    }

    public static ContainerV31TrustedDemo open(Context context, Uri selected) throws Exception {
        if (Build.VERSION.SDK_INT < 30) {
            throw new UnsupportedOperationException("V3.1 requires Android API 30+");
        }
        if (selected == null || !"content".equals(selected.getScheme())) {
            throw new SecurityException("select the paired demo through the Android file picker");
        }
        Context app = context.getApplicationContext();
        File directory = new File(app.getCodeCacheDir(), "external_demo_v31");
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new IllegalStateException("cannot create private demo code cache");
        }
        File apk = File.createTempFile("external-demo-", ".apk", directory);
        boolean accepted = false;
        try {
            String digest = copyAndDigest(app, selected, apk);
            // This check MUST precede PackageManager parsing and DexClassLoader.
            if (!ContainerV31AdmissionPolicy.matchesDigest(
                    ContainerV31BuildTrust.APK_SHA256, digest)) {
                throw new SecurityException("APK SHA-256 is not the paired build's trusted demo; "
                        + "other APKs remain read-only in V3");
            }
            verifyArchive(apk);
            verifyPackage(app, apk);
            try {
                app.getClassLoader().loadClass(ContainerV31AdmissionPolicy.ENTRY);
                throw new SecurityException("external demo entry was embedded in host DEX");
            } catch (ClassNotFoundException expected) {
                // The demo must come from the user-selected independent APK.
            }
            // Android framework classes are shared; host application classes are
            // not exposed through the parent. Trusted demo has no host dependency.
            ClassLoader framework = ClassLoader.getSystemClassLoader().getParent();
            DexClassLoader loader = new DexClassLoader(apk.getAbsolutePath(),
                    directory.getAbsolutePath(), null, framework);
            Class<?> entry = Class.forName(ContainerV31AdmissionPolicy.ENTRY, false, loader);
            if (entry.getClassLoader() != loader) {
                throw new SecurityException("demo entry was not defined by its private loader");
            }
            ContainerV31TrustedDemo result = new ContainerV31TrustedDemo(apk, digest, entry);
            accepted = true;
            return result;
        } finally {
            if (!accepted && apk.exists() && !apk.delete()) {
                throw new IllegalStateException("rejected private demo copy could not be removed");
            }
        }
    }

    private static String copyAndDigest(Context context, Uri selected, File apk) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        long copied = 0;
        // Android 14 dynamic-code rule: mark read-only with the write FD already
        // open, then fill and fsync the private file. Never load the SAF URI.
        try (InputStream input = context.getContentResolver().openInputStream(selected);
             FileOutputStream output = new FileOutputStream(apk)) {
            if (input == null) throw new IllegalArgumentException("selected document has no data");
            if (!apk.setReadOnly()) throw new SecurityException("cannot make demo code read-only");
            byte[] block = new byte[32768];
            int count;
            while ((count = input.read(block)) != -1) {
                if (count == 0) continue;
                copied += count;
                if (copied > ContainerV31AdmissionPolicy.MAX_BYTES) {
                    throw new SecurityException("demo exceeds 8 MiB import limit");
                }
                output.write(block, 0, count);
                digest.update(block, 0, count);
            }
            output.getFD().sync();
        }
        if (!ContainerV31AdmissionPolicy.validLength(copied)) {
            throw new SecurityException("demo APK is truncated or empty");
        }
        StringBuilder hex = new StringBuilder(64);
        for (byte value : digest.digest()) {
            hex.append(Character.forDigit((value >>> 4) & 15, 16));
            hex.append(Character.forDigit(value & 15, 16));
        }
        return hex.toString();
    }

    private static void verifyArchive(File apk) throws Exception {
        Set<String> names = new HashSet<>();
        try (ZipFile archive = new ZipFile(apk)) {
            java.util.Enumeration<? extends ZipEntry> entries = archive.entries();
            while (entries.hasMoreElements()) {
                ZipEntry item = entries.nextElement();
                String name = item.getName();
                if (names.size() >= 1024 || !names.add(name) || name.startsWith("/")
                        || name.contains("..") || name.contains("\\")
                        || name.startsWith("lib/") || item.getSize() < 0
                        || item.getSize() > 16L * 1024 * 1024) {
                    throw new SecurityException("unexpected trusted demo ZIP structure");
                }
            }
            for (String required : new String[]{"AndroidManifest.xml", "classes.dex",
                    "resources.arsc", "res/layout/demo_panel.xml"}) {
                ZipEntry item = archive.getEntry(required);
                if (item == null || item.isDirectory() || item.getSize() <= 0) {
                    throw new SecurityException("trusted demo missing " + required);
                }
            }
        }
    }

    private static void verifyPackage(Context context, File apk) {
        int flags = PackageManager.GET_ACTIVITIES | PackageManager.GET_PERMISSIONS
                | PackageManager.GET_SERVICES | PackageManager.GET_RECEIVERS
                | PackageManager.GET_PROVIDERS;
        @SuppressWarnings("deprecation")
        PackageInfo info = context.getPackageManager().getPackageArchiveInfo(
                apk.getAbsolutePath(), flags);
        ActivityInfo activity = info == null || info.activities == null
                || info.activities.length != 1 ? null : info.activities[0];
        if (info == null || !ContainerV31AdmissionPolicy.matchesIdentity(
                info.packageName, info.getLongVersionCode(),
                activity == null ? null : activity.name, length(info.activities),
                length(info.requestedPermissions), length(info.services)
                        + length(info.receivers) + length(info.providers))) {
            throw new SecurityException("trusted demo manifest identity/permissions mismatch");
        }
    }

    private static int length(Object[] items) { return items == null ? 0 : items.length; }

    public synchronized File getVerifiedResourceApk() {
        if (closed || !apk.isFile() || apk.canWrite()) {
            throw new IllegalStateException("trusted demo copy is closed or writable");
        }
        return apk;
    }

    public String getSha256() { return sha256; }
    public String getGuestPackageName() { return ContainerV31AdmissionPolicy.PACKAGE; }

    public synchronized Class<?> getGuestClass() {
        if (closed) throw new IllegalStateException("trusted demo is closed");
        return guestClass;
    }

    public synchronized boolean isClosed() { return closed; }
    public synchronized boolean isApkDeleted() { return !apk.exists(); }

    @Override public synchronized void close() {
        closed = true;
        guestClass = null;
        if (apk.exists() && !apk.delete()) {
            throw new IllegalStateException("private trusted demo APK cleanup failed");
        }
    }
}
