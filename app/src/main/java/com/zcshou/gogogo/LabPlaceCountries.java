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
