package com.zcshou.gogogo.shizuku;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Convert privileged read-only package/user queries into an anonymous summary.
 * Raw shell output (including OEM user names) MUST NOT be persisted or exported.
 * Package presence is NOT equivalent to an active process or a mock-location fix.
 */
public final class LabCloneUserReadOnly {
    public static final int MAX_QUERIED_USERS = 8;
    private static final Pattern USER_ID = Pattern.compile("UserInfo\\{([0-9]{1,5}):");
    private static final Pattern CURRENT_USER = Pattern.compile(
            "(?m)^\\s*(?:Current user:\\s*)?([0-9]{1,5})\\s*$");

    private LabCloneUserReadOnly() {}

    public static boolean commandSucceeded(String commandResult) {
        return commandResult != null
                && commandResult.contains("[exit 0]");
    }

    public static Integer parseCurrentUser(String result) {
        if (!commandSucceeded(result)) return null;
        Matcher m = CURRENT_USER.matcher(result);
        return m.find() ? Integer.valueOf(m.group(1)) : null;
    }

    public static List<Integer> parseUserIds(String result) {
        List<Integer> resultIds = new ArrayList<>();
        if (!commandSucceeded(result)) return resultIds;
        LinkedHashSet<Integer> ids = new LinkedHashSet<>();
        Matcher matcher = USER_ID.matcher(result);
        while (matcher.find() && ids.size() <= 64) {
            ids.add(Integer.valueOf(matcher.group(1)));
        }
        resultIds.addAll(ids);
        return resultIds;
    }

    /** Only exact package match, with exit 0; permission errors remain UNKNOWN. */
    public static Boolean containsWeChat(String result) {
        if (!commandSucceeded(result)) return null;
        for (String line : result.split("\n")) {
            if ("package:com.tencent.mm".equals(line.trim())) return Boolean.TRUE;
        }
        return Boolean.FALSE;
    }

    public static String summary(Integer currentUser, boolean userQuerySucceeded,
                                 List<Integer> users, String verboseResult,
                                 Map<Integer, Boolean> packageChecks) {
        StringBuilder out = new StringBuilder();
        out.append("Lab 50 · 微信主号/分身只读诊断\n");
        out.append("USER_ENUMERATION=").append(userQuerySucceeded ? "OK" : "UNKNOWN")
                .append('\n');
        out.append("CURRENT_USER=").append(currentUser == null ? "UNKNOWN" : "RESOLVED")
                .append('\n');
        if (!userQuerySucceeded || users == null || users.isEmpty()) {
            out.append("VISIBLE_USER_COUNT=UNKNOWN\n");
        } else {
            out.append("VISIBLE_USER_COUNT=").append(users.size()).append('\n');
        }

        boolean mainKnown = currentUser != null && packageChecks != null
                && packageChecks.containsKey(currentUser);
        Boolean main = mainKnown ? packageChecks.get(currentUser) : null;
        out.append("CURRENT_USER_WECHAT=").append(status(main)).append('\n');

        int otherFound = 0, otherInstalled = 0, otherMissing = 0, otherUnknown = 0;
        int inspected = 0;
        if (userQuerySucceeded && users != null && currentUser != null) {
            for (Integer uid : users) {
                if (uid == null || uid.equals(currentUser)) continue;
                otherFound++;
                if (packageChecks == null || !packageChecks.containsKey(uid)) {
                    otherUnknown++;
                    continue;
                }
                inspected++;
                Boolean present = packageChecks.get(uid);
                if (present == null) otherUnknown++;
                else if (present) otherInstalled++;
                else otherMissing++;
            }
        }
        out.append("ADDITIONAL_USER_COUNT=")
                .append(userQuerySucceeded && currentUser != null ? otherFound : "UNKNOWN")
                .append('\n');
        out.append("ADDITIONAL_USERS_CHECKED=").append(inspected).append('\n');
        out.append("ADDITIONAL_WECHAT=")
                .append(otherInstalled > 0 ? "PACKAGE_PRESENT"
                        : (!userQuerySucceeded || currentUser == null || otherUnknown > 0)
                        ? "UNKNOWN"
                        : "NOT_OBSERVED_IN_QUERIED_USERS")
                .append('\n');
        out.append("STANDARD_CLONE_TYPE_MARKER=")
                .append(commandSucceeded(verboseResult)
                        && verboseResult.contains("android.os.usertype.profile.CLONE")
                        ? "OBSERVED" : "UNKNOWN")
                .append('\n');
        out.append("WECHAT_FORCE_STOP=NOT_CHECKED\n");
        out.append("WECHAT_PROCESS_RUNNING=UNKNOWN\n");
        out.append("WECHAT_LOCATION_SOURCE=UNKNOWN\n");
        out.append("OEM_VIVO_CLONE_IMPLEMENTATION=UNKNOWN\n");
        out.append("PRIVACY=no_user_ids_names_packages_listed_raw_coordinates\n");
        out.append("解释：仅识别允许查询的 Android 用户及微信包安装状态；"
                + "未观察到不等于没有分身。包存在、FLAG_STOPPED、进程运行、"
                + "微信页面定位是四种不同证据。本功能没有强停/清缓存/读微信数据。");
        return out.toString();
    }

    private static String status(Boolean status) {
        return status == null ? "UNKNOWN" : status ? "PACKAGE_PRESENT" : "NOT_OBSERVED";
    }
}
