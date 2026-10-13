package com.zcshou.gogogo;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/** Offline ISO 3166-1 alpha-2 flag mapping for country sections. */
public final class LabPlaceCountries {
    public static final String CUSTOM_GROUP = "我的自定义地点";
    private static final Map<String, String> CODES;

    static {
        Map<String, String> codes = new HashMap<>();
        codes.put("缅甸", "MM");
        codes.put("中国", "CN");
        codes.put("日本", "JP");
        codes.put("韩国", "KR");
        codes.put("新加坡", "SG");
        codes.put("泰国", "TH");
        codes.put("越南", "VN");
        codes.put("印度", "IN");
        codes.put("阿联酋", "AE");
        codes.put("美国", "US");
        codes.put("加拿大", "CA");
        codes.put("英国", "GB");
        codes.put("法国", "FR");
        codes.put("德国", "DE");
        codes.put("澳大利亚", "AU");
        codes.put("巴西", "BR");
        codes.put("马来西亚", "MY");
        codes.put("印度尼西亚", "ID");
        codes.put("菲律宾", "PH");
        codes.put("柬埔寨", "KH");
        codes.put("老挝", "LA");
        codes.put("文莱", "BN");
        codes.put("东帝汶", "TL");
        codes.put("巴基斯坦", "PK");
        codes.put("孟加拉国", "BD");
        codes.put("尼泊尔", "NP");
        codes.put("斯里兰卡", "LK");
        codes.put("不丹", "BT");
        codes.put("马尔代夫", "MV");
        codes.put("蒙古", "MN");
        codes.put("哈萨克斯坦", "KZ");
        codes.put("乌兹别克斯坦", "UZ");
        codes.put("吉尔吉斯斯坦", "KG");
        codes.put("塔吉克斯坦", "TJ");
        codes.put("土库曼斯坦", "TM");
        codes.put("伊朗", "IR");
        codes.put("沙特阿拉伯", "SA");
        codes.put("卡塔尔", "QA");
        codes.put("科威特", "KW");
        codes.put("阿曼", "OM");
        codes.put("以色列", "IL");
        codes.put("约旦", "JO");
        codes.put("土耳其", "TR");
        codes.put("伊拉克", "IQ");
        codes.put("阿塞拜疆", "AZ");
        codes.put("亚美尼亚", "AM");
        codes.put("格鲁吉亚", "GE");
        codes.put("俄罗斯", "RU");
        codes.put("乌克兰", "UA");
        codes.put("白俄罗斯", "BY");
        codes.put("荷兰", "NL");
        codes.put("比利时", "BE");
        codes.put("瑞士", "CH");
        codes.put("奥地利", "AT");
        codes.put("意大利", "IT");
        codes.put("西班牙", "ES");
        codes.put("葡萄牙", "PT");
        codes.put("爱尔兰", "IE");
        codes.put("丹麦", "DK");
        codes.put("挪威", "NO");
        codes.put("瑞典", "SE");
        codes.put("芬兰", "FI");
        codes.put("冰岛", "IS");
        codes.put("波兰", "PL");
        codes.put("捷克", "CZ");
        codes.put("匈牙利", "HU");
        codes.put("希腊", "GR");
        codes.put("克罗地亚", "HR");
        codes.put("塞尔维亚", "RS");
        codes.put("罗马尼亚", "RO");
        codes.put("保加利亚", "BG");
        codes.put("卢森堡", "LU");
        codes.put("埃及", "EG");
        codes.put("摩洛哥", "MA");
        codes.put("突尼斯", "TN");
        codes.put("阿尔及利亚", "DZ");
        codes.put("尼日利亚", "NG");
        codes.put("加纳", "GH");
        codes.put("肯尼亚", "KE");
        codes.put("埃塞俄比亚", "ET");
        codes.put("坦桑尼亚", "TZ");
        codes.put("乌干达", "UG");
        codes.put("南非", "ZA");
        codes.put("卢旺达", "RW");
        codes.put("塞内加尔", "SN");
        codes.put("科特迪瓦", "CI");
        codes.put("安哥拉", "AO");
        codes.put("赞比亚", "ZM");
        codes.put("津巴布韦", "ZW");
        codes.put("马达加斯加", "MG");
        codes.put("墨西哥", "MX");
        codes.put("危地马拉", "GT");
        codes.put("哥斯达黎加", "CR");
        codes.put("巴拿马", "PA");
        codes.put("古巴", "CU");
        codes.put("牙买加", "JM");
        codes.put("多米尼加", "DO");
        codes.put("哥伦比亚", "CO");
        codes.put("秘鲁", "PE");
        codes.put("智利", "CL");
        codes.put("阿根廷", "AR");
        codes.put("乌拉圭", "UY");
        codes.put("厄瓜多尔", "EC");
        codes.put("玻利维亚", "BO");
        codes.put("巴拉圭", "PY");
        codes.put("委内瑞拉", "VE");
        codes.put("新西兰", "NZ");
        codes.put("斐济", "FJ");
        codes.put("巴布亚新几内亚", "PG");
        // Additional internationally recognized country/region ISO labels
        // used by the Natural Earth city-centre expansion.
        codes.put("阿富汗", "AF");
        codes.put("阿尔巴尼亚", "AL");
        codes.put("安道尔", "AD");
        codes.put("安提瓜和巴布达", "AG");
        codes.put("巴林", "BH");
        codes.put("巴巴多斯", "BB");
        codes.put("伯利兹", "BZ");
        codes.put("贝宁", "BJ");
        codes.put("波斯尼亚和黑塞哥维那", "BA");
        codes.put("博茨瓦纳", "BW");
        codes.put("布基纳法索", "BF");
        codes.put("布隆迪", "BI");
        codes.put("喀麦隆", "CM");
        codes.put("佛得角", "CV");
        codes.put("中非共和国", "CF");
        codes.put("乍得", "TD");
        codes.put("科摩罗", "KM");
        codes.put("刚果共和国", "CG");
        codes.put("刚果民主共和国", "CD");
        codes.put("塞浦路斯", "CY");
        codes.put("吉布提", "DJ");
        codes.put("多米尼克", "DM");
        codes.put("萨尔瓦多", "SV");
        codes.put("赤道几内亚", "GQ");
        codes.put("厄立特里亚", "ER");
        codes.put("爱沙尼亚", "EE");
        codes.put("密克罗尼西亚联邦", "FM");
        codes.put("加蓬", "GA");
        codes.put("格林纳达", "GD");
        codes.put("几内亚", "GN");
        codes.put("几内亚比绍", "GW");
        codes.put("圭亚那", "GY");
        codes.put("海地", "HT");
        codes.put("洪都拉斯", "HN");
        codes.put("基里巴斯", "KI");
        codes.put("拉脱维亚", "LV");
        codes.put("黎巴嫩", "LB");
        codes.put("莱索托", "LS");
        codes.put("利比里亚", "LR");
        codes.put("利比亚", "LY");
        codes.put("列支敦士登", "LI");
        codes.put("立陶宛", "LT");
        codes.put("马拉维", "MW");
        codes.put("马里", "ML");
        codes.put("马耳他", "MT");
        codes.put("马绍尔群岛", "MH");
        codes.put("毛里塔尼亚", "MR");
        codes.put("毛里求斯", "MU");
        codes.put("摩尔多瓦", "MD");
        codes.put("摩纳哥", "MC");
        codes.put("黑山", "ME");
        codes.put("莫桑比克", "MZ");
        codes.put("纳米比亚", "NA");
        codes.put("尼加拉瓜", "NI");
        codes.put("尼日尔", "NE");
        codes.put("朝鲜", "KP");
        codes.put("北马其顿", "MK");
        codes.put("帕劳", "PW");
        codes.put("圣基茨和尼维斯", "KN");
        codes.put("圣卢西亚", "LC");
        codes.put("圣文森特和格林纳丁斯", "VC");
        codes.put("萨摩亚", "WS");
        codes.put("圣马力诺", "SM");
        codes.put("圣多美和普林西比", "ST");
        codes.put("塞舌尔", "SC");
        codes.put("塞拉利昂", "SL");
        codes.put("斯洛伐克", "SK");
        codes.put("斯洛文尼亚", "SI");
        codes.put("所罗门群岛", "SB");
        codes.put("索马里", "SO");
        codes.put("南苏丹", "SS");
        codes.put("苏丹", "SD");
        codes.put("苏里南", "SR");
        codes.put("叙利亚", "SY");
        codes.put("巴哈马", "BS");
        codes.put("冈比亚", "GM");
        codes.put("多哥", "TG");
        codes.put("汤加", "TO");
        codes.put("特立尼达和多巴哥", "TT");
        codes.put("图瓦卢", "TV");
        codes.put("瓦努阿图", "VU");
        codes.put("梵蒂冈", "VA");
        codes.put("也门", "YE");
        codes.put("埃斯瓦蒂尼", "SZ");
        CODES = Collections.unmodifiableMap(codes);
    }

    private LabPlaceCountries() {}

    public static String countryOf(String placeLabel) {
        if (placeLabel == null) return CUSTOM_GROUP;
        int separator = placeLabel.indexOf(" · ");
        if (separator < 1) return CUSTOM_GROUP;
        String candidate = placeLabel.substring(0, separator).trim();
        return CODES.containsKey(candidate) ? candidate : CUSTOM_GROUP;
    }

    public static String cityOf(String placeLabel) {
        String country = countryOf(placeLabel);
        if (CUSTOM_GROUP.equals(country)) {
            return placeLabel == null || placeLabel.trim().isEmpty()
                    ? "未命名位置" : placeLabel;
        }
        return placeLabel.substring(country.length() + 3).trim();
    }

    public static String flagOf(String country) {
        String code = CODES.get(country);
        if (code == null) return "📍";
        // Android supports regional indicator pairs as a country flag emoji.
        int a = 0x1F1E6 + (code.charAt(0) - 'A');
        int b = 0x1F1E6 + (code.charAt(1) - 'A');
        return new String(Character.toChars(a)) + new String(Character.toChars(b));
    }

    public static boolean isKnownCountry(String country) {
        return CODES.containsKey(country);
    }
}
