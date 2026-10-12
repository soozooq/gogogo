package com.zcshou.gogogo.shizuku;

import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class LabCloneUserReadOnlyTest {
    @Test public void parseRealisticUsersWithoutExportingNames() {
        String raw = "Users:\n\tUserInfo{0:Main Owner:13} running\n"
                + "\tUserInfo{999:微信分身专用:30} running\n[exit 0]\n";
        List<Integer> ids = LabCloneUserReadOnly.parseUserIds(raw);
        assertEquals(Arrays.asList(0, 999), ids);
        Map<Integer, Boolean> statuses = new HashMap<>();
        statuses.put(0, Boolean.TRUE);
        statuses.put(999, Boolean.TRUE);
        String summary = LabCloneUserReadOnly.summary(0, true, ids,
                "[exit 1]\n", statuses);
        assertTrue(summary.contains("VISIBLE_USER_COUNT=2"));
        assertTrue(summary.contains("CURRENT_USER_WECHAT=PACKAGE_PRESENT"));
        assertTrue(summary.contains("ADDITIONAL_WECHAT=PACKAGE_PRESENT"));
        assertTrue(summary.contains("WECHAT_PROCESS_RUNNING=UNKNOWN"));
        assertFalse(summary.contains("微信分身专用"));
        assertFalse(summary.contains("999"));
        assertFalse(summary.contains("Main Owner"));
    }

    @Test public void currentUserRequiresSuccessfulCommand() {
        assertEquals(Integer.valueOf(0),
                LabCloneUserReadOnly.parseCurrentUser("0\n[exit 0]\n"));
        assertEquals(Integer.valueOf(10),
                LabCloneUserReadOnly.parseCurrentUser("Current user: 10\n[exit 0]\n"));
        assertNull(LabCloneUserReadOnly.parseCurrentUser("SecurityException\n[exit 1]\n"));
    }

    @Test public void packageQueryRequiresExactName() {
        assertEquals(Boolean.TRUE,
                LabCloneUserReadOnly.containsWeChat("package:com.tencent.mm\n[exit 0]\n"));
        assertEquals(Boolean.FALSE,
                LabCloneUserReadOnly.containsWeChat("package:com.tencent.mm.clone\n[exit 0]\n"));
        assertNull(LabCloneUserReadOnly.containsWeChat("Permission denied\n[exit 1]\n"));
    }

    @Test public void permissionFailureIsUnknownNotNoClone() {
        String summary = LabCloneUserReadOnly.summary(null, false,
                Collections.emptyList(), "[exit 1]\n", Collections.emptyMap());
        assertTrue(summary.contains("USER_ENUMERATION=UNKNOWN"));
        assertTrue(summary.contains("ADDITIONAL_WECHAT=UNKNOWN"));
        assertTrue(summary.contains("OEM_VIVO_CLONE_IMPLEMENTATION=UNKNOWN"));
        assertFalse(summary.contains("USER_ENUMERATION=OK"));
    }

    @Test public void noOtherUserDoesNotProveOemCloneAbsent() {
        Map<Integer, Boolean> map = new HashMap<>();
        map.put(0, Boolean.TRUE);
        String summary = LabCloneUserReadOnly.summary(0, true, Arrays.asList(0),
                "[exit 0]\n", map);
        assertTrue(summary.contains("ADDITIONAL_WECHAT=NOT_OBSERVED_IN_QUERIED_USERS"));
        assertTrue(summary.contains("OEM_VIVO_CLONE_IMPLEMENTATION=UNKNOWN"));
        assertTrue(summary.contains("WECHAT_FORCE_STOP=NOT_CHECKED"));
    }

    @Test public void unqueriedUserPreventsFalseNegative() {
        Map<Integer, Boolean> map = new HashMap<>();
        map.put(0, Boolean.TRUE);
        map.put(10, Boolean.FALSE);
        String summary = LabCloneUserReadOnly.summary(0, true,
                Arrays.asList(0, 10, 11), null, map);
        assertTrue(summary.contains("ADDITIONAL_WECHAT=UNKNOWN"));
    }

    @Test public void unknownMainPackageNotDeclaredNotInstalled() {
        String summary = LabCloneUserReadOnly.summary(0, true, Arrays.asList(0),
                null, Collections.emptyMap());
        assertTrue(summary.contains("CURRENT_USER_WECHAT=UNKNOWN"));
    }

    @Test public void verboseCloneMarkerOnlyDescribesSystemUserList() {
        String summary = LabCloneUserReadOnly.summary(0, true, Arrays.asList(0, 10),
                "android.os.usertype.profile.CLONE\n[exit 0]\n",
                Collections.emptyMap());
        assertTrue(summary.contains("STANDARD_CLONE_TYPE_MARKER=OBSERVED"));
        assertTrue(summary.contains("ADDITIONAL_WECHAT=UNKNOWN"));
    }

    @Test public void errorAndNicknamesAreNeverInReport() {
        Map<Integer, Boolean> map = new HashMap<>();
        map.put(0, null);
        String bad = "UserInfo{10:张三手机号-13812345678:20} [exit 0]";
        String summary = LabCloneUserReadOnly.summary(0, true,
                LabCloneUserReadOnly.parseUserIds(bad), bad, map);
        assertFalse(summary.contains("13812345678"));
        assertFalse(summary.contains("张三"));
        assertFalse(summary.contains("UserInfo{10"));
    }
}
