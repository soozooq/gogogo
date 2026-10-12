package com.zcshou.gogogo;

import java.util.Locale;

/** Pure policies: the seven-digit registration segment is not the SIM location. */
public final class LabSimAttribution {
    private LabSimAttribution() {}

    public static boolean validSegment(String input) {
        return input != null && input.matches("1[3-9][0-9]{5}");
    }

    public static String country(String iso) {
        if (iso == null || !iso.matches("[a-zA-Z]{2}")) return "未知";
        Locale value = new Locale("", iso.toUpperCase(Locale.ROOT));
        String label = value.getDisplayCountry(Locale.SIMPLIFIED_CHINESE);
        if (label == null || label.trim().isEmpty()
                || label.equalsIgnoreCase(iso)) return iso.toUpperCase(Locale.ROOT);
        return label + "（" + iso.toUpperCase(Locale.ROOT) + "）";
    }

    public static String displaySegmentResult(String province, String city,
                                              String carrier, String version) {
        String p = safe(province);
        String c = safe(city);
        String op = safe(carrier);
        String v = safe(version);
        return "号码段开户归属地（非实时位置）\n省份：" + p
                + "\n城市：" + c
                + "\n号段运营商（历史记录）：" + op
                + "\n数据库版本：" + v
                + "\n携号转网后实际运营商可能不同；此信息不能判断 SIM/GPS 当前位置。";
    }

    private static String safe(String input) {
        if (input == null || input.trim().isEmpty()) return "未知";
        String trimmed = input.trim();
        return trimmed.length() > 90 ? trimmed.substring(0, 90) : trimmed;
    }
}
