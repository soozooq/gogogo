package com.soozooq.tianwaitian.containerresearch;

import android.content.Context;
import android.net.Uri;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.Locale;
import top.niunaijun.bcore.BlackBoxCore;
import top.niunaijun.bcore.entity.pm.InstallResult;

/** Only a pinned, unmodified third-party release; not a custom guest protocol. */
public final class GuestImport {
    public static final String PACKAGE = "org.fossify.math";
    public static final String SHA256 = "7c5443c22d6d62c67dfe80df33ea9161ff0fa440f92590bcb45d7008b57613ed";
    public static final long MAX_BYTES = 10L * 1024 * 1024;
    private GuestImport() {}

    public static File copyAndVerify(Context context, Uri uri) throws Exception {
        File directory = new File(context.getFilesDir(), "research-import");
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IllegalStateException("NO_DIRECTORY");
        File temporary = File.createTempFile("guest-", ".apk", directory);
        boolean complete = false;
        try {
            try (InputStream input = context.getContentResolver().openInputStream(uri);
                 FileOutputStream output = new FileOutputStream(temporary)) {
                if (input == null) throw new IllegalArgumentException("NO_INPUT");
                byte[] buffer = new byte[8192];
                long total = 0;
                int read;
                while ((read = input.read(buffer)) != -1) {
                    total += read;
                    if (total > MAX_BYTES) throw new IllegalArgumentException("APK_TOO_LARGE");
                    output.write(buffer, 0, read);
                }
                output.getFD().sync();
            }
            verify(temporary);
            if (!temporary.setReadOnly()) throw new IllegalStateException("APK_NOT_READ_ONLY");
            complete = true;
            return temporary;
        } finally {
            if (!complete) temporary.delete();
        }
    }

    public static void verify(File file) throws Exception {
        if (file.length() <= 0 || file.length() > MAX_BYTES) throw new IllegalArgumentException("APK_SIZE");
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
        }
        StringBuilder hex = new StringBuilder();
        for (byte value : digest.digest()) hex.append(String.format(Locale.US, "%02x", value & 255));
        if (!SHA256.equals(hex.toString())) throw new IllegalArgumentException("UNAPPROVED_GUEST");
    }

    public static void installVerified(File file) throws Exception {
        verify(file);
        InstallResult result = BlackBoxCore.get().installPackageAsUser(file, 0);
        if (!result.success || !PACKAGE.equals(result.packageName)) {
            throw new IllegalStateException("IMPORT_FAILED: " + result.msg);
        }
    }

    /** True means launch was requested, not that the guest rendered or accepted location. */
    public static boolean requestLaunch() { return BlackBoxCore.get().launchApk(PACKAGE, 0); }
}
