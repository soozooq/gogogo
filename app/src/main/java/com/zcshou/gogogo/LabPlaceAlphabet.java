package com.zcshou.gogogo;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;

/**
 * Deterministic offline Hanyu Pinyin sorting for the Chinese country names
 * in LabLocationPresets. Explicit full names avoid Android ICU differences and
 * keep the A-Z navigation stable across devices and unit-test JVMs.
 */
public final class LabPlaceAlphabet {
    private static final Map<String, String> COUNTRY_PINYIN;
    public static final Comparator<String> COUNTRY_ORDER =
            (a, b) -> {
                String x = pinyinOf(a);
                String y = pinyinOf(b);
                int order = x.compareTo(y);
                return order != 0 ? order : safe(a).compareTo(safe(b));
            };

    static {
        Map<String, String> values = new HashMap<>();
        values.put("缅甸", "miandian");
        values.put("中国", "zhongguo");
        values.put("日本", "riben");
        values.put("韩国", "hanguo");
        values.put("新加坡", "xinjiapo");
        values.put("泰国", "taiguo");
        values.put("越南", "yuenan");
        values.put("印度", "yindu");
        values.put("阿联酋", "alianqiu");
        values.put("美国", "meiguo");
        values.put("加拿大", "jianada");
        values.put("英国", "yingguo");
        values.put("法国", "faguo");
        values.put("德国", "deguo");
        values.put("澳大利亚", "aodaliya");
        values.put("巴西", "baxi");
        values.put("马来西亚", "malaixiya");
        values.put("印度尼西亚", "yindunixiya");
        values.put("菲律宾", "feilvbin");
        values.put("柬埔寨", "jianpuzhai");
        values.put("老挝", "laowo");
        values.put("文莱", "wenlai");
        values.put("东帝汶", "dongdiwen");
        values.put("巴基斯坦", "bajisitan");
        values.put("孟加拉国", "mengjialaguo");
        values.put("尼泊尔", "niboer");
        values.put("斯里兰卡", "sililanka");
        values.put("不丹", "budan");
        values.put("马尔代夫", "maerdaifu");
        values.put("蒙古", "menggu");
        values.put("哈萨克斯坦", "hasakesitan");
        values.put("乌兹别克斯坦", "wuzibiekesitan");
        values.put("吉尔吉斯斯坦", "jierjisisitan");
        values.put("塔吉克斯坦", "tajikesitan");
        values.put("土库曼斯坦", "tukumansitan");
        values.put("伊朗", "yilang");
        values.put("沙特阿拉伯", "shatealabo");
        values.put("卡塔尔", "kataer");
        values.put("科威特", "keweite");
        values.put("阿曼", "aman");
        values.put("以色列", "yiselie");
        values.put("约旦", "yuedan");
        values.put("土耳其", "tuerqi");
        values.put("伊拉克", "yilake");
        values.put("阿塞拜疆", "asaibaijiang");
        values.put("亚美尼亚", "yameiniya");
        values.put("格鲁吉亚", "gelujiya");
        values.put("俄罗斯", "eluosi");
        values.put("乌克兰", "wukelan");
        values.put("白俄罗斯", "baieluosi");
        values.put("荷兰", "helan");
        values.put("比利时", "bilishi");
        values.put("瑞士", "ruishi");
        values.put("奥地利", "aodili");
        values.put("意大利", "yidali");
        values.put("西班牙", "xibanya");
        values.put("葡萄牙", "putaoya");
        values.put("爱尔兰", "aierlan");
        values.put("丹麦", "danmai");
        values.put("挪威", "nuowei");
        values.put("瑞典", "ruidian");
        values.put("芬兰", "fenlan");
        values.put("冰岛", "bingdao");
        values.put("波兰", "bolan");
        values.put("捷克", "jieke");
        values.put("匈牙利", "xiongyali");
        values.put("希腊", "xila");
        values.put("克罗地亚", "keluodiya");
        values.put("塞尔维亚", "saierweiya");
        values.put("罗马尼亚", "luomaniya");
        values.put("保加利亚", "baojialiya");
        values.put("卢森堡", "lusenbao");
        values.put("埃及", "aiji");
        values.put("摩洛哥", "moluoge");
        values.put("突尼斯", "tunisi");
        values.put("阿尔及利亚", "aerjiliya");
        values.put("尼日利亚", "niriliya");
        values.put("加纳", "jiana");
        values.put("肯尼亚", "kenniya");
        values.put("埃塞俄比亚", "aisaiebiya");
        values.put("坦桑尼亚", "tansangniya");
        values.put("乌干达", "wuganda");
        values.put("南非", "nanfei");
        values.put("卢旺达", "luwangda");
        values.put("塞内加尔", "saineijiaer");
        values.put("科特迪瓦", "ketediwa");
        values.put("安哥拉", "angela");
        values.put("赞比亚", "zanbiya");
        values.put("津巴布韦", "jinbabuwei");
        values.put("马达加斯加", "madajiasijia");
        values.put("墨西哥", "moxige");
        values.put("危地马拉", "weidimala");
        values.put("哥斯达黎加", "gesidalijia");
        values.put("巴拿马", "banama");
        values.put("古巴", "guba");
        values.put("牙买加", "yamaijia");
        values.put("多米尼加", "duominijia");
        values.put("哥伦比亚", "gelunbiya");
        values.put("秘鲁", "milu");
        values.put("智利", "zhili");
        values.put("阿根廷", "agenting");
        values.put("乌拉圭", "wulagui");
        values.put("厄瓜多尔", "eguaduoer");
        values.put("玻利维亚", "boliweiya");
        values.put("巴拉圭", "balagui");
        values.put("委内瑞拉", "weineiruila");
        values.put("新西兰", "xinxilan");
        values.put("斐济", "feiji");
        values.put("巴布亚新几内亚", "babuyaxinjineiya");
        COUNTRY_PINYIN = Collections.unmodifiableMap(values);
    }

    private LabPlaceAlphabet() {}

    public static String initialOf(String country) {
        String name = COUNTRY_PINYIN.get(country);
        return name == null || name.isEmpty()
                ? "#" : name.substring(0, 1).toUpperCase(Locale.ROOT);
    }

    public static String pinyinOf(String country) {
        String pinyin = COUNTRY_PINYIN.get(country);
        // Unclassified custom favorites always sort after known countries.
        return pinyin == null ? "~" + safe(country) : pinyin;
    }

    /**
     * Convert country headers into ListView adapter positions. The number of
     * visible city rows is zero for collapsed groups; this protects alphabet
     * jumps against variable amounts of expanded content.
     */
    public static Map<String, Integer> headerAnchors(
            List<String> sortedCountries, Map<String, Integer> visibleCityCounts) {
        Map<String, Integer> positions = new LinkedHashMap<>();
        int row = 0;
        for (String country : sortedCountries) {
            String letter = initialOf(country);
            if (!positions.containsKey(letter)) positions.put(letter, row);
            Integer visible = visibleCityCounts.get(country);
            int count = visible == null ? 0 : Math.max(0, visible);
            row += 1 + count;
        }
        return positions;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
