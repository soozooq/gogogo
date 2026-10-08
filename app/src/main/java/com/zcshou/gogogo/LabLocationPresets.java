package com.zcshou.gogogo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Offline, approximate WGS-84 landmark presets for the home coordinate form
 * and MapLibre map. Selecting a preset never starts a mock location service.
 *
 * IMPORTANT: This is a fixed landmark list, not a geocoding/POI lookup.
 * Coordinates are latitude/longitude in WGS-84; service intents accept
 * longitude first, latitude second.
 */
public final class LabLocationPresets {
    private LabLocationPresets() {}

    public static final class Preset {
        public final String name;
        public final double longitude;
        public final double latitude;

        private Preset(String name, double longitude, double latitude) {
            this.name = name;
            this.longitude = longitude;
            this.latitude = latitude;
        }
    }

    private static final List<Preset> PRESETS;

    static {
        List<Preset> points = new ArrayList<>();
        // Keep the existing GoGoGo start coordinate as the very first option.
        points.add(new Preset("缅甸 · 妙瓦底（默认）", 98.50895, 16.68914));
        points.add(new Preset("缅甸 · 仰光", 96.19513, 16.86607));
        points.add(new Preset("中国 · 北京（天安门广场）", 116.39747, 39.90549));
        points.add(new Preset("日本 · 东京（东京站）", 139.76712, 35.68124));
        points.add(new Preset("韩国 · 首尔（市厅）", 126.97797, 37.56653));
        points.add(new Preset("新加坡 · 滨海湾", 103.85196, 1.29027));
        points.add(new Preset("泰国 · 曼谷", 100.50177, 13.75633));
        points.add(new Preset("越南 · 河内", 105.83416, 21.02776));
        points.add(new Preset("印度 · 新德里", 77.20902, 28.61394));
        points.add(new Preset("阿联酋 · 迪拜", 55.27078, 25.20485));
        points.add(new Preset("美国 · 纽约（时代广场）", -73.98543, 40.75800));
        points.add(new Preset("加拿大 · 多伦多", -79.38318, 43.65323));
        points.add(new Preset("英国 · 伦敦", -0.12776, 51.50735));
        points.add(new Preset("法国 · 巴黎", 2.35222, 48.85661));
        points.add(new Preset("德国 · 柏林", 13.40500, 52.52000));
        points.add(new Preset("澳大利亚 · 悉尼", 151.20930, -33.86882));
        points.add(new Preset("巴西 · 里约热内卢", -43.17290, -22.90685));
        PRESETS = Collections.unmodifiableList(points);
    }

    public static Preset defaultPreset() {
        return PRESETS.get(0);
    }

    public static Preset get(int index) {
        return PRESETS.get(index);
    }

    public static int size() {
        return PRESETS.size();
    }

    public static String[] labels() {
        String[] labels = new String[PRESETS.size()];
        for (int i = 0; i < PRESETS.size(); i++) {
            labels[i] = PRESETS.get(i).name;
        }
        return labels;
    }
}
