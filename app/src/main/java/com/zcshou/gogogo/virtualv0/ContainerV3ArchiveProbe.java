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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Read-only, user-selected APK inspection. No code loading, installation,
 * component launching, native loading, hook, or permissions requested.
 *
 * This report establishes scope for the future V3 virtual-package/runtime work,
 * NOT compatibility or trustworthiness of the selected app.
 */
public final class ContainerV3ArchiveProbe {
    private static final long MAX_BYTES = 512L * 1024 * 1024;
    private static final int MAX_ZIP_ENTRIES = 50000;
    private ContainerV3ArchiveProbe() {}

    public static String inspect(Context context, Uri selected) {
        String stage = "selected URI";
        File temporary = null;
        String apkSha = "(not yet computed)";
        try {
            if (selected == null || !"content".equals(selected.getScheme())) {
                throw new SecurityException("must select APK via Android document provider");
            }
            stage = "copy user-selected document into private disposable cache";
            temporary = File.createTempFile("twt-v3-inspect-", ".apk", context.getCacheDir());
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long total = 0;
            byte[] buffer = new byte[32768];
            int firstCount = 0;
            byte[] first = new byte[4];
            try (InputStream source = context.getContentResolver().openInputStream(selected);
                 FileOutputStream dest = new FileOutputStream(temporary)) {
                if (source == null) throw new IllegalStateException("document provider returned no data");
                int count;
                while ((count = source.read(buffer)) != -1) {
                    if (count == 0) continue;
                    if (total + count > MAX_BYTES) {
                        throw new SecurityException("archive exceeds 512 MiB read-only inspection limit");
                    }
                    for (int i = 0; i < count && firstCount < 4; i++) first[firstCount++] = buffer[i];
                    dest.write(buffer, 0, count);
                    digest.update(buffer, 0, count);
                    total += count;
                }
                dest.getFD().sync();
            }
            if (firstCount < 4 || first[0] != 0x50 || first[1] != 0x4b
                    || first[2] != 0x03 || first[3] != 0x04) {
                throw new SecurityException("selected file does not have an APK-compatible ZIP header");
            }
            apkSha = toHex(digest.digest());
            stage = "read ZIP central directory without extracting or loading any guest code";
            int entries = 0;
            int dexCount = 0;
            boolean hasManifest = false;
            boolean hasResources = false;
            Set<String> abis = new HashSet<>();
            try (ZipFile zip = new ZipFile(temporary)) {
                java.util.Enumeration<? extends ZipEntry> all = zip.entries();
                while (all.hasMoreElements()) {
                    ZipEntry entry = all.nextElement();
                    if (++entries > MAX_ZIP_ENTRIES) {
                        throw new SecurityException("unreasonable ZIP entry count");
                    }
                    String name = entry.getName();
                    if ("AndroidManifest.xml".equals(name)) hasManifest = true;
                    if ("resources.arsc".equals(name)) hasResources = true;
                    if (name.matches("classes([2-9][0-9]*)?\\.dex")) dexCount++;
                    if (name.startsWith("lib/")) {
                        int end = name.indexOf('/', 4);
                        if (end > 4 && end < 64) abis.add(name.substring(4, end));
                    }
                }
            }
            if (!hasManifest || dexCount == 0) {
                throw new IllegalArgumentException("missing AndroidManifest.xml or compiled DEX");
            }

            stage = "parse external APK archive package/component metadata (no execution)";
            int flags = PackageManager.GET_ACTIVITIES | PackageManager.GET_SERVICES
                    | PackageManager.GET_RECEIVERS | PackageManager.GET_PROVIDERS
                    | PackageManager.GET_PERMISSIONS;
            PackageInfo info;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                info = context.getPackageManager().getPackageArchiveInfo(
                        temporary.getAbsolutePath(), PackageManager.PackageInfoFlags.of(flags));
            } else {
                info = context.getPackageManager().getPackageArchiveInfo(
                        temporary.getAbsolutePath(), flags);
            }
            if (info == null || info.packageName == null || info.packageName.isEmpty()) {
                throw new IllegalArgumentException("Android PackageManager could not parse APK metadata");
            }
            int activities = length(info.activities);
            int services = length(info.services);
            int receivers = length(info.receivers);
            int providers = length(info.providers);
            int permissions = length(info.requestedPermissions);
            StringBuilder sample = new StringBuilder();
            if (info.activities != null) {
                for (ActivityInfo act : info.activities) {
                    if (sample.length() > 700) break;
                    if (sample.length() > 0) sample.append("\n");
                    sample.append(" · ").append(act.name)
                            .append(act.exported ? " [exported]" : " [private]");
                }
            }
            String abiText = abis.isEmpty() ? "(none)" : String.join(", ", abis);
            return "天外天 / V3 真实 APK 只读归档审计\n"
                    + "Android API=" + Build.VERSION.SDK_INT + "\n"
                    + "外部 APK 包名=" + info.packageName + "\n"
                    + "APK SHA-256=" + apkSha + "\n"
                    + "APK 文件大小=" + total + " bytes\n"
                    + "ZIP 条目=" + entries + "；DEX 数=" + dexCount + "\n"
                    + "资源表=" + hasResources + "；Native ABI=" + abiText + "\n"
                    + "Activity=" + activities + "；Service=" + services
                    + "；Receiver=" + receivers + "；Provider=" + providers + "\n"
                    + "请求权限声明数=" + permissions + "\n"
                    + "部分 Activity 声明（最多约 700 字符）：\n"
                    + (sample.length() == 0 ? " · (none)\n" : sample + "\n")
                    + "V3 真实 APK 归档解析：PASS\n"
                    + "V3 第三方 APK 实际安装/启动：NOT TESTED\n"
                    + "V3 第三方运行兼容性：UNKNOWN\n"
                    + "说明：用户主动选取文件；仅在临时缓存读取 APK，不执行 DEX、"
                    + "不安装应用、不提取资源到运行环境、不更改权限。"
                    + "文件在审计结束时删除，无法根据组件数量推断能否运行。\n";
        } catch (Throwable failure) {
            String description = failure.getMessage() == null ? "" : failure.getMessage();
            return "天外天 / V3 真实 APK 只读归档审计\n"
                    + "V3 归档解析：FAIL\n"
                    + "stage=" + stage + "\n"
                    + "APK SHA-256=" + apkSha + "\n"
                    + "rootCause=" + failure.getClass().getSimpleName() + ": "
                    + description.substring(0, Math.min(description.length(), 220)) + "\n"
                    + "第三方 APK 没有执行；不表示虚拟容器已经兼容该应用。\n";
        } finally {
            if (temporary != null && temporary.exists() && !temporary.delete()) {
                // Defensive warning is not a compatibility success signal; Android
                // private cache will also be cleared by the OS as necessary.
                android.util.Log.w("TwtV3Archive", "temporary selected archive could not be deleted");
            }
        }
    }

    private static int length(Object[] array) {
        return array == null ? 0 : array.length;
    }

    private static String toHex(byte[] value) {
        StringBuilder result = new StringBuilder(value.length * 2);
        for (byte octet : value) result.append(String.format(Locale.US, "%02x", octet & 0xff));
        return result.toString();
    }
}
