package com.zcshou.gogogo.virtualv0;

import android.content.Context;
import android.content.pm.PackageInfo;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.MessageDigest;
import java.util.zip.ZipFile;

import dalvik.system.DexClassLoader;

/**
 * Loads only the build-bundled, controlled fixture APK.
 * This helper does not install APKs or start unregistered Android components.
 */
public final class ContainerV0TrustedGuest implements AutoCloseable {
    public static final String ASSET = "twt-fixture-v0.apk";
    public static final String PACKAGE = "com.soozooq.tianwaitian.fixture";
    public static final String ENTRY = PACKAGE + ".GuestEntry";
    private static final long MAX_BYTES = 10L * 1024 * 1024;

    private final File temporaryApk;
    private final String sha256;
    private final Class<?> guestClass;
    private final Method writeMethod;
    private final Method readMethod;

    private ContainerV0TrustedGuest(File temporaryApk, String sha256, Class<?> guestClass)
            throws NoSuchMethodException {
        this.temporaryApk = temporaryApk;
        this.sha256 = sha256;
        this.guestClass = guestClass;
        this.writeMethod = guestClass.getMethod("record", String.class);
        this.readMethod = guestClass.getMethod("read");
    }

    public static ContainerV0TrustedGuest open(Context context) throws Exception {
        Context app = context.getApplicationContext();
        File dir = new File(app.getCodeCacheDir(), "virtual_container_v0_guest");
        if (!dir.isDirectory() && !dir.mkdirs()) {
            throw new IllegalStateException("cannot create controlled guest cache");
        }

        File apk = File.createTempFile("fixture-", ".apk", dir);
        boolean ready = false;
        try {
            String digest = copyReadOnly(app, apk);

            PackageInfo packageInfo = app.getPackageManager().getPackageArchiveInfo(
                    apk.getAbsolutePath(), 0);
            if (packageInfo == null || !PACKAGE.equals(packageInfo.packageName)) {
                throw new SecurityException("embedded APK identity mismatch");
            }
            try (ZipFile archive = new ZipFile(apk)) {
                if (archive.getEntry("classes.dex") == null) {
                    throw new SecurityException("fixture is missing classes.dex");
                }
            }
            try {
                app.getClassLoader().loadClass(ENTRY);
                throw new SecurityException("guest entry unexpectedly present in host dex");
            } catch (ClassNotFoundException expected) {
                // Only a truly separate fixture APK may provide this class.
            }
            DexClassLoader loader = new DexClassLoader(
                    apk.getAbsolutePath(), dir.getAbsolutePath(), null, app.getClassLoader());
            Class<?> entry = Class.forName(ENTRY, true, loader);
            if (entry.getClassLoader() != loader) {
                throw new SecurityException("guest entry was not defined by the new loader");
            }
            ContainerV0TrustedGuest guest = new ContainerV0TrustedGuest(apk, digest, entry);
            ready = true;
            return guest;
        } finally {
            if (!ready && apk.exists()) apk.delete();
        }
    }

    public String record(String marker) throws Exception {
        if (marker == null || !marker.matches("[A-Za-z0-9_-]{1,80}")) {
            throw new IllegalArgumentException("marker");
        }
        return invoke(writeMethod, marker);
    }

    public String read() throws Exception {
        return invoke(readMethod);
    }

    public String getSha256() {
        return sha256;
    }

    public Class<?> getGuestClass() {
        return guestClass;
    }

    @Override public void close() {
        // The loaded class is already defined in this process. Remove our private copy.
        if (temporaryApk.exists()) temporaryApk.delete();
    }

    private static String invoke(Method method, Object... arguments) throws Exception {
        try {
            Object result = method.invoke(null, arguments);
            if (!(result instanceof String)) throw new IllegalStateException("guest response");
            return (String) result;
        } catch (InvocationTargetException wrapped) {
            Throwable root = wrapped.getCause();
            if (root instanceof Exception) throw (Exception) root;
            if (root instanceof Error) throw (Error) root;
            throw wrapped;
        }
    }

    private static String copyReadOnly(Context context, File apk) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        long copied = 0;
        // Keep the file handle open while making dynamic code read-only.
        try (InputStream in = context.getAssets().open(ASSET);
             FileOutputStream out = new FileOutputStream(apk)) {
            if (!apk.setReadOnly()) throw new SecurityException("cannot mark guest code read-only");
            byte[] block = new byte[8192];
            int count;
            while ((count = in.read(block)) != -1) {
                copied += count;
                if (copied > MAX_BYTES) throw new SecurityException("fixture APK too large");
                out.write(block, 0, count);
                digest.update(block, 0, count);
            }
            out.getFD().sync();
        }
        if (copied < 1024) throw new SecurityException("fixture APK missing or truncated");
        StringBuilder hex = new StringBuilder(64);
        for (byte value : digest.digest()) {
            hex.append(Character.forDigit((value >>> 4) & 0xf, 16));
            hex.append(Character.forDigit(value & 0xf, 16));
        }
        return hex.toString();
    }
}
