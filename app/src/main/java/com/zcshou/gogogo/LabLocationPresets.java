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
        // Additional worldwide city-center reference points (offline, approximate).
        points.add(new Preset("中国 · 上海", 121.47370, 31.23040));
        points.add(new Preset("中国 · 广州", 113.26440, 23.12910));
        points.add(new Preset("中国 · 深圳", 114.05787, 22.54310));
        points.add(new Preset("中国 · 成都", 104.06654, 30.57227));
        points.add(new Preset("中国 · 重庆", 106.55156, 29.56301));
        points.add(new Preset("中国 · 杭州", 120.15515, 30.27415));
        points.add(new Preset("中国 · 香港", 114.16936, 22.31930));
        points.add(new Preset("中国 · 澳门", 113.54387, 22.19875));
        points.add(new Preset("中国 · 台北", 121.56542, 25.03396));
        points.add(new Preset("日本 · 大阪", 135.50225, 34.69374));
        points.add(new Preset("日本 · 京都", 135.76815, 35.01156));
        points.add(new Preset("日本 · 札幌", 141.35450, 43.06180));
        points.add(new Preset("韩国 · 釜山", 129.07560, 35.17960));
        points.add(new Preset("缅甸 · 曼德勒", 96.08910, 21.95880));
        points.add(new Preset("泰国 · 清迈", 98.98530, 18.78830));
        points.add(new Preset("泰国 · 普吉", 98.39230, 7.88040));
        points.add(new Preset("越南 · 胡志明市", 106.62970, 10.82310));
        points.add(new Preset("越南 · 岘港", 108.20220, 16.05440));
        points.add(new Preset("马来西亚 · 吉隆坡", 101.68690, 3.13900));
        points.add(new Preset("印度尼西亚 · 雅加达", 106.84560, -6.20880));
        points.add(new Preset("印度尼西亚 · 巴厘岛（登巴萨）", 115.21670, -8.65000));
        points.add(new Preset("菲律宾 · 马尼拉", 120.98420, 14.59950));
        points.add(new Preset("柬埔寨 · 金边", 104.92820, 11.55640));
        points.add(new Preset("老挝 · 万象", 102.63310, 17.97570));
        points.add(new Preset("文莱 · 斯里巴加湾市", 114.93980, 4.90310));
        points.add(new Preset("东帝汶 · 帝力", 125.57360, -8.55690));
        points.add(new Preset("印度 · 孟买", 72.87770, 19.07600));
        points.add(new Preset("印度 · 班加罗尔", 77.59460, 12.97160));
        points.add(new Preset("巴基斯坦 · 伊斯兰堡", 73.04790, 33.68440));
        points.add(new Preset("孟加拉国 · 达卡", 90.41250, 23.81030));
        points.add(new Preset("尼泊尔 · 加德满都", 85.32400, 27.71720));
        points.add(new Preset("斯里兰卡 · 科伦坡", 79.86120, 6.92710));
        points.add(new Preset("不丹 · 廷布", 89.63900, 27.47280));
        points.add(new Preset("马尔代夫 · 马累", 73.50930, 4.17550));
        points.add(new Preset("蒙古 · 乌兰巴托", 106.90570, 47.91840));
        points.add(new Preset("哈萨克斯坦 · 阿斯塔纳", 71.43040, 51.16940));
        points.add(new Preset("乌兹别克斯坦 · 塔什干", 69.24010, 41.29950));
        points.add(new Preset("吉尔吉斯斯坦 · 比什凯克", 74.56980, 42.87460));
        points.add(new Preset("塔吉克斯坦 · 杜尚别", 68.78700, 38.55980));
        points.add(new Preset("土库曼斯坦 · 阿什哈巴德", 58.32610, 37.96010));
        points.add(new Preset("伊朗 · 德黑兰", 51.38900, 35.68920));
        points.add(new Preset("沙特阿拉伯 · 利雅得", 46.67530, 24.71360));
        points.add(new Preset("卡塔尔 · 多哈", 51.53100, 25.28540));
        points.add(new Preset("科威特 · 科威特城", 47.97740, 29.37590));
        points.add(new Preset("阿曼 · 马斯喀特", 58.38290, 23.58800));
        points.add(new Preset("以色列 · 特拉维夫", 34.78180, 32.08530));
        points.add(new Preset("约旦 · 安曼", 35.93040, 31.95390));
        points.add(new Preset("土耳其 · 伊斯坦布尔", 28.97840, 41.00820));
        points.add(new Preset("伊拉克 · 巴格达", 44.36610, 33.31520));
        points.add(new Preset("阿塞拜疆 · 巴库", 49.86710, 40.40930));
        points.add(new Preset("亚美尼亚 · 埃里温", 44.51520, 40.18720));
        points.add(new Preset("格鲁吉亚 · 第比利斯", 44.82710, 41.71510));
        points.add(new Preset("阿联酋 · 阿布扎比", 54.37730, 24.45390));
        points.add(new Preset("俄罗斯 · 莫斯科", 37.61730, 55.75580));
        points.add(new Preset("俄罗斯 · 圣彼得堡", 30.33510, 59.93430));
        points.add(new Preset("乌克兰 · 基辅", 30.52340, 50.45010));
        points.add(new Preset("白俄罗斯 · 明斯克", 27.56150, 53.90060));
        points.add(new Preset("荷兰 · 阿姆斯特丹", 4.90410, 52.36760));
        points.add(new Preset("比利时 · 布鲁塞尔", 4.35170, 50.85030));
        points.add(new Preset("瑞士 · 苏黎世", 8.54170, 47.37690));
        points.add(new Preset("奥地利 · 维也纳", 16.37380, 48.20820));
        points.add(new Preset("意大利 · 罗马", 12.49640, 41.90280));
        points.add(new Preset("意大利 · 米兰", 9.19000, 45.46420));
        points.add(new Preset("西班牙 · 马德里", -3.70380, 40.41680));
        points.add(new Preset("西班牙 · 巴塞罗那", 2.17340, 41.38510));
        points.add(new Preset("葡萄牙 · 里斯本", -9.13930, 38.72230));
        points.add(new Preset("爱尔兰 · 都柏林", -6.26030, 53.34980));
        points.add(new Preset("丹麦 · 哥本哈根", 12.56830, 55.67610));
        points.add(new Preset("挪威 · 奥斯陆", 10.75220, 59.91390));
        points.add(new Preset("瑞典 · 斯德哥尔摩", 18.06860, 59.32930));
        points.add(new Preset("芬兰 · 赫尔辛基", 24.93840, 60.16990));
        points.add(new Preset("冰岛 · 雷克雅未克", -21.94260, 64.14660));
        points.add(new Preset("波兰 · 华沙", 21.01220, 52.22970));
        points.add(new Preset("捷克 · 布拉格", 14.43780, 50.07550));
        points.add(new Preset("匈牙利 · 布达佩斯", 19.04020, 47.49790));
        points.add(new Preset("希腊 · 雅典", 23.72750, 37.98380));
        points.add(new Preset("克罗地亚 · 萨格勒布", 15.98190, 45.81500));
        points.add(new Preset("塞尔维亚 · 贝尔格莱德", 20.44890, 44.78660));
        points.add(new Preset("罗马尼亚 · 布加勒斯特", 26.10250, 44.42680));
        points.add(new Preset("保加利亚 · 索非亚", 23.32190, 42.69770));
        points.add(new Preset("卢森堡 · 卢森堡市", 6.13190, 49.61160));
        points.add(new Preset("英国 · 曼彻斯特", -2.24260, 53.48080));
        points.add(new Preset("法国 · 马赛", 5.36980, 43.29650));
        points.add(new Preset("德国 · 慕尼黑", 11.58200, 48.13510));
        points.add(new Preset("埃及 · 开罗", 31.23570, 30.04440));
        points.add(new Preset("摩洛哥 · 拉巴特", -6.84980, 34.02090));
        points.add(new Preset("突尼斯 · 突尼斯市", 10.18150, 36.80650));
        points.add(new Preset("阿尔及利亚 · 阿尔及尔", 3.05880, 36.75380));
        points.add(new Preset("尼日利亚 · 拉各斯", 3.37920, 6.52440));
        points.add(new Preset("尼日利亚 · 阿布贾", 7.39860, 9.07650));
        points.add(new Preset("加纳 · 阿克拉", -0.18696, 5.60372));
        points.add(new Preset("肯尼亚 · 内罗毕", 36.82190, -1.29210));
        points.add(new Preset("埃塞俄比亚 · 亚的斯亚贝巴", 38.75780, 8.98060));
        points.add(new Preset("坦桑尼亚 · 达累斯萨拉姆", 39.20830, -6.79240));
        points.add(new Preset("乌干达 · 坎帕拉", 32.58250, 0.34760));
        points.add(new Preset("南非 · 约翰内斯堡", 28.04730, -26.20410));
        points.add(new Preset("南非 · 开普敦", 18.42410, -33.92490));
        points.add(new Preset("卢旺达 · 基加利", 30.06190, -1.94410));
        points.add(new Preset("塞内加尔 · 达喀尔", -17.46770, 14.71670));
        points.add(new Preset("科特迪瓦 · 阿比让", -4.00830, 5.35995));
        points.add(new Preset("安哥拉 · 罗安达", 13.23430, -8.83900));
        points.add(new Preset("赞比亚 · 卢萨卡", 28.32280, -15.38750));
        points.add(new Preset("津巴布韦 · 哈拉雷", 31.05300, -17.82520));
        points.add(new Preset("马达加斯加 · 塔那那利佛", 47.50790, -18.87920));
        points.add(new Preset("美国 · 洛杉矶", -118.24370, 34.05220));
        points.add(new Preset("美国 · 旧金山", -122.41940, 37.77490));
        points.add(new Preset("美国 · 华盛顿特区", -77.03690, 38.90720));
        points.add(new Preset("美国 · 芝加哥", -87.62980, 41.87810));
        points.add(new Preset("美国 · 西雅图", -122.33210, 47.60620));
        points.add(new Preset("美国 · 迈阿密", -80.19180, 25.76170));
        points.add(new Preset("美国 · 波士顿", -71.05890, 42.36010));
        points.add(new Preset("加拿大 · 温哥华", -123.12070, 49.28270));
        points.add(new Preset("加拿大 · 蒙特利尔", -73.56730, 45.50170));
        points.add(new Preset("加拿大 · 渥太华", -75.69720, 45.42150));
        points.add(new Preset("墨西哥 · 墨西哥城", -99.13320, 19.43260));
        points.add(new Preset("危地马拉 · 危地马拉城", -90.50690, 14.63490));
        points.add(new Preset("哥斯达黎加 · 圣何塞", -84.09070, 9.92810));
        points.add(new Preset("巴拿马 · 巴拿马城", -79.51670, 8.98240));
        points.add(new Preset("古巴 · 哈瓦那", -82.36660, 23.11360));
        points.add(new Preset("牙买加 · 金斯敦", -76.79200, 17.97120));
        points.add(new Preset("多米尼加 · 圣多明各", -69.93120, 18.48610));
        points.add(new Preset("哥伦比亚 · 波哥大", -74.07210, 4.71100));
        points.add(new Preset("秘鲁 · 利马", -77.04280, -12.04640));
        points.add(new Preset("智利 · 圣地亚哥", -70.66930, -33.44890));
        points.add(new Preset("阿根廷 · 布宜诺斯艾利斯", -58.38160, -34.60370));
        points.add(new Preset("乌拉圭 · 蒙得维的亚", -56.16450, -34.90110));
        points.add(new Preset("厄瓜多尔 · 基多", -78.46780, -0.18070));
        points.add(new Preset("玻利维亚 · 拉巴斯", -68.11930, -16.48970));
        points.add(new Preset("巴拉圭 · 亚松森", -57.57590, -25.26370));
        points.add(new Preset("委内瑞拉 · 加拉加斯", -66.90360, 10.48060));
        points.add(new Preset("巴西 · 圣保罗", -46.63330, -23.55050));
        points.add(new Preset("巴西 · 巴西利亚", -47.88250, -15.79420));
        points.add(new Preset("澳大利亚 · 墨尔本", 144.96310, -37.81360));
        points.add(new Preset("澳大利亚 · 布里斯班", 153.02510, -27.46980));
        points.add(new Preset("澳大利亚 · 珀斯", 115.86050, -31.95050));
        points.add(new Preset("澳大利亚 · 堪培拉", 149.13000, -35.28090));
        points.add(new Preset("新西兰 · 奥克兰", 174.76330, -36.84850));
        points.add(new Preset("新西兰 · 惠灵顿", 174.77670, -41.28650));
        points.add(new Preset("斐济 · 苏瓦", 178.45010, -18.14160));
        points.add(new Preset("巴布亚新几内亚 · 莫尔兹比港", 147.18030, -9.44380));
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
