package com.soozooq.tianwaitian.containerresearch;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.Build;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import top.niunaijun.bcore.BlackBoxCore;
import top.niunaijun.bcore.entity.pm.InstallResult;

/** Exact, unmodified releases from the build-generated pinned source catalog. */
public final class GuestImport {
    // Retained for the existing calculator regression; the ordinary UI uses Entry.
    public static final String PACKAGE = "org.fossify.math";
    public static final long MAX_BYTES = 512L * 1024 * 1024;
    private GuestImport() {}

    public static final class Entry {
        public final String label, packageName, sha256, certificateSha256, versionName, url;
        public final long size, versionCode;
        public final boolean location, arm64Only;
        private Entry(JSONObject json) throws Exception {
            label = json.getString("label"); packageName = json.getString("package");
            sha256 = json.getString("sha256"); certificateSha256 = json.getString("certificate_sha256");
            versionName = json.getString("version_name"); versionCode = json.getLong("version_code");
            size = json.getLong("size"); location = json.getBoolean("location"); url = json.getString("url");
            arm64Only = json.optBoolean("arm64_only", false);
            if (!sha256.matches("[0-9a-f]{64}") || !certificateSha256.matches("[0-9a-f]{64}")
                    || size <= 0 || size > MAX_BYTES) throw new IllegalStateException("CATALOG_INVALID");
        }
        @Override public String toString() { return label + " " + versionName; }
        public boolean supportsDevice() {
            return !arm64Only || (Build.SUPPORTED_ABIS.length > 0
                    && "arm64-v8a".equals(Build.SUPPORTED_ABIS[0]));
        }
    }

    public static List<Entry> catalog(Context context) throws Exception {
        try (InputStream input = context.getAssets().open("approved-guests.json")) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096]; int n;
            while ((n = input.read(buffer)) != -1) {
                if (bytes.size() + n > 65536) throw new IllegalStateException("CATALOG_TOO_LARGE");
                bytes.write(buffer, 0, n);
            }
            JSONArray data = new JSONArray(new String(bytes.toByteArray(), java.nio.charset.StandardCharsets.UTF_8));
            List<Entry> entries = new ArrayList<>();
            for (int i = 0; i < data.length(); i++) entries.add(new Entry(data.getJSONObject(i)));
            return Collections.unmodifiableList(entries);
        }
    }

    public static Entry entryFor(Context context, String packageName) throws Exception {
        for (Entry entry : catalog(context)) if (entry.packageName.equals(packageName)) return entry;
        throw new IllegalArgumentException("UNAPPROVED_GUEST");
    }

    public static File copyAndVerify(Context context, Uri uri, Entry entry) throws Exception {
        if (!entry.supportsDevice()) throw new IllegalArgumentException("ARM64_GUEST_REQUIRED");
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
                    if (total > entry.size) throw new IllegalArgumentException("APK_SIZE");
                    output.write(buffer, 0, read);
                }
                output.getFD().sync();
            }
            verify(temporary, entry);
            if (!temporary.setReadOnly()) throw new IllegalStateException("APK_NOT_READ_ONLY");
            complete = true;
            return temporary;
        } finally {
            if (!complete) temporary.delete();
        }
    }

    /** Only read the selected package's public original APK; never copy its private app data. */
    public static File copyInstalled(Context context, Entry entry) throws Exception {
        if (!entry.supportsDevice()) throw new IllegalArgumentException("ARM64_GUEST_REQUIRED");
        PackageInfo installed = context.getPackageManager().getPackageInfo(entry.packageName, 0);
        if (installed.applicationInfo == null || installed.applicationInfo.sourceDir == null
                || !entry.versionName.equals(installed.versionName)
                || entry.versionCode != (Build.VERSION.SDK_INT >= 28
                    ? installed.getLongVersionCode() : installed.versionCode)) {
            throw new IllegalArgumentException("INSTALLED_VERSION_NOT_PINNED");
        }
        String[] splits = installed.applicationInfo.splitSourceDirs;
        if (splits != null && splits.length > 0) throw new IllegalArgumentException("INSTALLED_SPLITS_UNSUPPORTED");
        return copyAndVerify(context, Uri.fromFile(new File(installed.applicationInfo.sourceDir)), entry);
    }

    public static void verify(File file, Entry entry) throws Exception {
        if (file.length() != entry.size || !entry.sha256.equals(hashFile(file))) {
            throw new IllegalArgumentException("UNAPPROVED_GUEST");
        }
    }

    private static String hashFile(File file) throws Exception {
        if (file.length() <= 0 || file.length() > MAX_BYTES) throw new IllegalArgumentException("APK_SIZE");
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
        }
        return hex(digest.digest());
    }

    private static String hex(byte[] bytes) {
        StringBuilder hex = new StringBuilder();
        for (byte value : bytes) hex.append(String.format(Locale.US, "%02x", value & 255));
        return hex.toString();
    }

    public static void installVerified(File file, Entry entry) throws Exception {
        if (!entry.supportsDevice()) throw new IllegalArgumentException("ARM64_GUEST_REQUIRED");
        verify(file, entry);
        PackageInfo archive = BlackBoxCore.getPackageManager().getPackageArchiveInfo(file.getAbsolutePath(),
                Build.VERSION.SDK_INT >= 28 ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES);
        if (archive == null || !entry.packageName.equals(archive.packageName)
                || !entry.versionName.equals(archive.versionName)
                || entry.versionCode != (Build.VERSION.SDK_INT >= 28 ? archive.getLongVersionCode() : archive.versionCode)) {
            throw new IllegalArgumentException("ARCHIVE_IDENTITY");
        }
        Signature[] signers = Build.VERSION.SDK_INT >= 28 && archive.signingInfo != null
                ? archive.signingInfo.getApkContentsSigners() : archive.signatures;
        if (signers == null || signers.length != 1 || !entry.certificateSha256.equals(hex(
                MessageDigest.getInstance("SHA-256").digest(signers[0].toByteArray())))) {
            throw new IllegalArgumentException("ARCHIVE_SIGNATURE");
        }
        InstallResult result = BlackBoxCore.get().installPackageAsUser(file, 0);
        if (!result.success || !entry.packageName.equals(result.packageName)) {
            throw new IllegalStateException("IMPORT_FAILED");
        }
    }

    public static void verify(File file) throws Exception { verify(file, entryFor(BlackBoxCore.getContext(), PACKAGE)); }
    public static void installVerified(File file) throws Exception { installVerified(file, entryFor(BlackBoxCore.getContext(), PACKAGE)); }
    /** True means launch requested, not proof that the Guest rendered or adopted location. */
    public static boolean requestLaunch() { return BlackBoxCore.get().launchApk(PACKAGE, 0); }
}
