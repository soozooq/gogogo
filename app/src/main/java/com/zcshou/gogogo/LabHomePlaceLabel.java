package com.zcshou.gogogo;

/** Offline identification is exact-match only: a nearby city is not a country lookup. */
public final class LabHomePlaceLabel {
    private LabHomePlaceLabel() {}

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
