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
        // Tianwaitian extension: approximate WGS-84 city-centre coordinates from
        // public-domain Natural Earth 1:110m populated places (not precise POIs).
        // Source and name mappings: docs/data/tianwaitian-natural-earth-places-2026.tsv
        points.add(new Preset("阿富汗 · 喀布尔", 69.181314, 34.518636));
        points.add(new Preset("阿尔巴尼亚 · 地拉那", 19.818883, 41.327541));
        points.add(new Preset("安道尔 · 安道尔城", 1.526594, 42.510753));
        points.add(new Preset("安提瓜和巴布达 · 圣约翰", -61.850034, 17.118036));
        points.add(new Preset("巴林 · 麦纳麦", 50.583052, 26.236136));
        points.add(new Preset("巴巴多斯 · 布里奇敦", -59.616527, 13.102003));
        points.add(new Preset("伯利兹 · 贝尔莫潘", -88.767073, 17.252033));
        points.add(new Preset("贝宁 · 波多诺伏", 2.616626, 6.483311));
        points.add(new Preset("波斯尼亚和黑塞哥维那 · 萨拉热窝", 18.383002, 43.850022));
        points.add(new Preset("博茨瓦纳 · 哈博罗内", 25.911948, -24.646314));
        points.add(new Preset("布基纳法索 · 瓦加杜古", -1.526670, 12.372262));
        points.add(new Preset("布隆迪 · 布琼布拉", 29.360006, -3.376087));
        points.add(new Preset("喀麦隆 · 雅温得", 11.514705, 3.868647));
        points.add(new Preset("佛得角 · 普拉亚", -23.516689, 14.916698));
        points.add(new Preset("中非共和国 · 班吉", 18.558288, 4.366644));
        points.add(new Preset("乍得 · 恩贾梅纳", 15.047202, 12.115042));
        points.add(new Preset("科摩罗 · 莫罗尼", 43.240244, -11.704158));
        points.add(new Preset("刚果共和国 · 布拉柴维尔", 15.282744, -4.257240));
        points.add(new Preset("刚果民主共和国 · 金沙萨", 15.313026, -4.327778));
        points.add(new Preset("塞浦路斯 · 尼科西亚", 33.366635, 35.166677));
        points.add(new Preset("吉布提 · 吉布提市", 43.148002, 11.595014));
        points.add(new Preset("多米尼克 · 罗索", -61.387013, 15.301016));
        points.add(new Preset("萨尔瓦多 · 圣萨尔瓦多", -89.215576, 13.703280));
        points.add(new Preset("赤道几内亚 · 马拉博", 8.783278, 3.750015));
        points.add(new Preset("厄立特里亚 · 阿斯马拉", 38.933324, 15.333339));
        points.add(new Preset("爱沙尼亚 · 塔林", 24.728041, 59.433877));
        points.add(new Preset("密克罗尼西亚联邦 · 帕利基尔", 158.149974, 6.916644));
        points.add(new Preset("加蓬 · 利伯维尔", 9.457965, 0.385389));
        points.add(new Preset("格林纳达 · 圣乔治", -61.741643, 12.052633));
        points.add(new Preset("几内亚 · 科纳克里", -13.682181, 9.533469));
        points.add(new Preset("几内亚比绍 · 比绍", -15.598361, 11.865024));
        points.add(new Preset("圭亚那 · 乔治敦", -58.167029, 6.801974));
        points.add(new Preset("海地 · 太子港", -72.337980, 18.542970));
        points.add(new Preset("洪都拉斯 · 特古西加尔巴", -87.219475, 14.103991));
        points.add(new Preset("基里巴斯 · 塔拉瓦", 173.017571, 1.338188));
        points.add(new Preset("拉脱维亚 · 里加", 24.099965, 56.950024));
        points.add(new Preset("黎巴嫩 · 贝鲁特", 35.507762, 33.873921));
        points.add(new Preset("莱索托 · 马塞卢", 27.483273, -29.316674));
        points.add(new Preset("利比里亚 · 蒙罗维亚", -10.799660, 6.314582));
        points.add(new Preset("利比亚 · 的黎波里", 13.180012, 32.892500));
        points.add(new Preset("列支敦士登 · 瓦杜兹", 9.516670, 47.133724));
        points.add(new Preset("立陶宛 · 维尔纽斯", 25.316635, 54.683366));
        points.add(new Preset("马拉维 · 利隆圭", 33.783302, -13.983295));
        points.add(new Preset("马里 · 巴马科", -8.001985, 12.651960));
        points.add(new Preset("马耳他 · 瓦莱塔", 14.514711, 35.899732));
        points.add(new Preset("马绍尔群岛 · 马朱罗", 171.380000, 7.103004));
        points.add(new Preset("毛里塔尼亚 · 努瓦克肖特", -15.975340, 18.086427));
        points.add(new Preset("毛里求斯 · 路易港", 57.499994, -20.166639));
        points.add(new Preset("摩尔多瓦 · 基希讷乌", 28.857711, 47.005024));
        points.add(new Preset("摩纳哥 · 摩纳哥城", 7.406913, 43.739646));
        points.add(new Preset("黑山 · 波德戈里察", 19.266307, 42.465972));
        points.add(new Preset("莫桑比克 · 马普托", 32.587217, -25.953332));
        points.add(new Preset("纳米比亚 · 温得和克", 17.083546, -22.570006));
        points.add(new Preset("尼加拉瓜 · 马那瓜", -86.270437, 12.154962));
        points.add(new Preset("尼日尔 · 尼亚美", 2.114710, 13.518652));
        points.add(new Preset("朝鲜 · 平壤", 125.752745, 39.021385));
        points.add(new Preset("北马其顿 · 斯科普里", 21.433461, 42.000006));
        points.add(new Preset("帕劳 · 梅莱凯奥克", 134.626549, 7.487396));
        points.add(new Preset("圣基茨和尼维斯 · 巴斯特尔", -62.717009, 17.302031));
        points.add(new Preset("圣卢西亚 · 卡斯特里", -60.992875, 14.007875));
        points.add(new Preset("圣文森特和格林纳丁斯 · 金斯敦", -61.220236, 13.155756));
        points.add(new Preset("萨摩亚 · 阿皮亚", -171.768599, -13.835715));
        points.add(new Preset("圣马力诺 · 圣马力诺城", 12.441770, 43.936096));
        points.add(new Preset("圣多美和普林西比 · 圣多美", 6.729650, 0.337466));
        points.add(new Preset("塞舌尔 · 维多利亚", 55.449990, -4.616632));
        points.add(new Preset("塞拉利昂 · 弗里敦", -13.236162, 8.471957));
        points.add(new Preset("斯洛伐克 · 布拉迪斯拉发", 17.116981, 48.150018));
        points.add(new Preset("斯洛文尼亚 · 卢布尔雅那", 14.514969, 46.055288));
        points.add(new Preset("所罗门群岛 · 霍尼亚拉", 159.949766, -9.437994));
        points.add(new Preset("索马里 · 摩加迪沙", 45.364732, 2.068627));
        points.add(new Preset("南苏丹 · 朱巴", 31.580026, 4.829975));
        points.add(new Preset("苏丹 · 喀土穆", 32.532233, 15.590024));
        points.add(new Preset("苏里南 · 帕拉马里博", -55.167031, 5.835030));
        points.add(new Preset("叙利亚 · 大马士革", 36.298050, 33.501980));
        points.add(new Preset("巴哈马 · 拿骚", -77.350044, 25.083390));
        points.add(new Preset("冈比亚 · 班珠尔", -16.591701, 13.453876));
        points.add(new Preset("多哥 · 洛美", 1.220811, 6.133883));
        points.add(new Preset("汤加 · 努库阿洛法", -175.220564, -21.138512));
        points.add(new Preset("特立尼达和多巴哥 · 西班牙港", -61.517031, 10.651997));
        points.add(new Preset("图瓦卢 · 富纳富提", 179.216647, -8.516652));
        points.add(new Preset("瓦努阿图 · 维拉港", 168.316641, -17.733350));
        points.add(new Preset("梵蒂冈 · 梵蒂冈城", 12.453387, 41.903282));
        points.add(new Preset("也门 · 萨那", 44.204648, 15.356679));
        points.add(new Preset("埃斯瓦蒂尼 · 姆巴巴内", 31.133335, -26.316651));
        points.add(new Preset("贝宁 · 科托努", 2.404355, 6.362980));
        points.add(new Preset("埃斯瓦蒂尼 · 洛班巴", 31.199997, -26.466667));
        points.add(new Preset("印度 · 加尔各答", 88.369126, 22.569579));
        points.add(new Preset("美国 · 丹佛", -104.985962, 39.741134));
        points.add(new Preset("美国 · 休斯敦", -95.348436, 29.741273));
        points.add(new Preset("美国 · 亚特兰大", -84.367642, 33.739457));
        points.add(new Preset("墨西哥 · 蒙特雷", -100.331931, 25.671941));
        points.add(new Preset("南非 · 布隆方丹", 26.229913, -29.119994));
        points.add(new Preset("南非 · 比勒陀利亚", 28.227483, -25.704975));
        points.add(new Preset("瑞士 · 伯尔尼", 7.466976, 46.916683));
        points.add(new Preset("瑞士 · 日内瓦", 6.140028, 46.210008));
        points.add(new Preset("荷兰 · 海牙", 4.269961, 52.080037));
        points.add(new Preset("土耳其 · 安卡拉", 32.862446, 39.929184));
        points.add(new Preset("智利 · 瓦尔帕莱索", -71.617026, -33.047739));
        points.add(new Preset("斯里兰卡 · 斯里贾亚瓦德纳普拉科特", 79.949993, 6.900004));
        points.add(new Preset("摩洛哥 · 卡萨布兰卡", -7.618313, 33.601922));
        points.add(new Preset("马来西亚 · 布城", 101.695037, 2.932515));
        points.add(new Preset("缅甸 · 内比都", 96.116673, 19.768503));
        points.add(new Preset("中国 · 乌鲁木齐", 87.573060, 43.806958));
        points.add(new Preset("菲律宾 · 碧瑶", 120.569943, 16.429991));
        points.add(new Preset("科特迪瓦 · 亚穆苏克罗", -5.275503, 6.818381));
        points.add(new Preset("坦桑尼亚 · 多多马", 35.750004, -6.183306));
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
