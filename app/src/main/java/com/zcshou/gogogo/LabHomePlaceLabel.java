package com.zcshou.gogogo;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Offline identification is exact-match only: a nearby city is not a country lookup. */
public final class LabHomePlaceLabel {
    private LabHomePlaceLabel() {}

    private static final Map<String, String> ENGLISH_TO_COUNTRY_CODE = countryCodes();

    private static Map<String, String> countryCodes() {
        Map<String, String> codes = new HashMap<>();
        for (String isoCode : Locale.getISOCountries()) {
            Locale locale = new Locale("", isoCode);
            String englishName = locale.getDisplayCountry(Locale.ENGLISH);
            if (englishName != null && !englishName.isEmpty()) {
                codes.put(englishName.trim().toLowerCase(Locale.ROOT), isoCode);
            }
        }
        codes.put("united states of america", "US");
        codes.put("usa", "US");
        codes.put("uk", "GB");
        return codes;
    }

    /**
     * Local-only display of the VPN exit country/city already saved with the
     * exact mock target. Never infer the region of a manually edited coordinate.
     * Returns null if the source coordinate does not match.
     */
    public static String vpnLabelForCoordinates(String longitudeText, String latitudeText,
                                                double savedLongitude, double savedLatitude,
                                                String country, String city) {
        final LabHomeCoordinates.Point selected;
        try {
            selected = LabHomeCoordinates.parse(longitudeText, latitudeText);
        } catch (IllegalArgumentException invalid) {
            return null;
        }
        if (!LabPlaceCoordinateMatch.same(selected.longitude, selected.latitude,
                savedLongitude, savedLatitude)
                || country == null || country.trim().isEmpty()) return null;
        String rawCountry = country.trim();
        String iso = ENGLISH_TO_COUNTRY_CODE.get(rawCountry.toLowerCase(Locale.ROOT));
        String localCountry = iso == null ? rawCountry
                : new Locale("", iso).getDisplayCountry(Locale.SIMPLIFIED_CHINESE);
        String flag = iso == null ? "📍" : flagForIso(iso);
        String localCity = city == null ? "" : city.trim();
        if (localCity.length() > 90) localCity = localCity.substring(0, 90) + "…";
        return "所选国家 / 城市：" + flag + " " + localCountry
                + (localCity.isEmpty() ? "" : " · " + localCity)
                + "（VPN 出口 IP 估算，非 GPS 反查）";
    }

    private static String flagForIso(String countryCode) {
        if (countryCode.length() != 2) return "📍";
        int first = Character.toUpperCase(countryCode.charAt(0)) - 'A' + 0x1F1E6;
        int second = Character.toUpperCase(countryCode.charAt(1)) - 'A' + 0x1F1E6;
        return new String(Character.toChars(first))
                + new String(Character.toChars(second));
    }


    public static String forCoordinates(String longitudeText, String latitudeText) {
        final LabHomeCoordinates.Point point;
        try {
            point = LabHomeCoordinates.parse(longitudeText, latitudeText);
        } catch (IllegalArgumentException invalid) {
            return "所选国家 / 城市：坐标尚未有效填写";
        }
        for (int i = 0; i < LabLocationPresets.size(); i++) {
            LabLocationPresets.Preset preset = LabLocationPresets.get(i);
            if (LabPlaceCoordinateMatch.same(point.longitude, point.latitude,
                    preset.longitude, preset.latitude)) {
                String country = LabPlaceCountries.countryOf(preset.name);
                String flag = LabPlaceCountries.flagOf(country);
                return "所选国家 / 城市：" + flag + " " + preset.name + "（离线预设）";
            }
        }
        return "所选国家 / 城市：自定义坐标 · 国家待识别";
    }
}
