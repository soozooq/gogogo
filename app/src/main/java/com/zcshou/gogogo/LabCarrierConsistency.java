package com.zcshou.gogogo;

import java.util.Locale;

/** Strict, intentionally narrow PLMN country consistency checks. */
public final class LabCarrierConsistency {
    private LabCarrierConsistency() {}

    public static String countryFromMccMnc(String operator) {
        if (operator == null || !operator.matches("[0-9]{5,6}")) return "UNKNOWN";
        int mcc;
        try {
            mcc = Integer.parseInt(operator.substring(0, 3));
        } catch (NumberFormatException ignored) {
            return "UNKNOWN";
        }
        if (mcc == 460 || mcc == 461) return "CN";
        if (mcc >= 310 && mcc <= 316) return "US";
        return "UNKNOWN"; // No guess for unsupported MCCs.
    }

    public static String check(String simIso, String operator) {
        String country = countryFromMccMnc(operator);
        if ("UNKNOWN".equals(country)
                || simIso == null || !simIso.matches("[A-Za-z]{2}")) {
            return "UNKNOWN_MCC_COUNTRY_OR_SIM_ISO";
        }
        String normalized = simIso.toUpperCase(Locale.ROOT);
        return normalized.equals(country)
                ? "CONSISTENT_" + country
                : "INCONSISTENT_SIM_ISO_" + normalized + "_MCC_" + country;
    }
}
