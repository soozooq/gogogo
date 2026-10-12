package com.zcshou.gogogo;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;

/** Validates provider-supplied IANA zone IDs; never infer one from a country alone. */
public final class LabVpnTimeZone {
    private static final Set<String> ZONES =
            new HashSet<>(Arrays.asList(TimeZone.getAvailableIDs()));

    private LabVpnTimeZone() {}

    public static boolean valid(String id) {
        return id != null && id.length() > 2 && id.length() <= 80
                && id.indexOf('/') > 0 && ZONES.contains(id)
                && !id.startsWith("Etc/");
    }

    public static String display(String id, long utcTimeMs) {
        if (!valid(id)) return "时区尚未识别（不会按国家猜测）";
        TimeZone zone = TimeZone.getTimeZone(id);
        SimpleDateFormat clock = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA);
        clock.setTimeZone(zone);
        int offsetMinutes = zone.getOffset(utcTimeMs) / 60000;
        int hour = Math.abs(offsetMinutes) / 60;
        int min = Math.abs(offsetMinutes) % 60;
        String offset = String.format(Locale.US, "UTC%c%02d:%02d",
                offsetMinutes < 0 ? '-' : '+', hour, min);
        return id + " · " + offset + (zone.inDaylightTime(new Date(utcTimeMs)) ? "（夏令时）" : "")
                + "\n当地时间：" + clock.format(new Date(utcTimeMs));
    }
}
