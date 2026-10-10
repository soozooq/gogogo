package com.zcshou.gogogo;

import org.junit.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.*;

public class LabPlaceCountriesTest {
    @Test public void everyPresetHasSupportedCountryAndFlag() {
        Set<String> countries = new HashSet<>();
        Map<String, Integer> cities = new HashMap<>();
        for (int i = 0; i < LabLocationPresets.size(); i++) {
            String label = LabLocationPresets.get(i).name;
            String country = LabPlaceCountries.countryOf(label);
            assertNotEquals("Bad country for " + label,
                    LabPlaceCountries.CUSTOM_GROUP, country);
            assertTrue(LabPlaceCountries.isKnownCountry(country));
            assertEquals("Flag should contain two regional indicator code points",
                    2, LabPlaceCountries.flagOf(country).codePointCount(0,
                            LabPlaceCountries.flagOf(country).length()));
            assertFalse(LabPlaceCountries.cityOf(label).isEmpty());
            countries.add(country);
            cities.put(country, cities.containsKey(country) ? cities.get(country) + 1 : 1);
        }
        assertEquals(109, countries.size());
        assertEquals(157, LabLocationPresets.size());
        assertTrue(cities.get("中国") >= 9);
        assertTrue(cities.get("日本") >= 4);
    }

    @Test public void commonCountryFlagsAreCorrect() {
        assertEquals("🇨🇳", LabPlaceCountries.flagOf("中国"));
        assertEquals("🇯🇵", LabPlaceCountries.flagOf("日本"));
        assertEquals("🇺🇸", LabPlaceCountries.flagOf("美国"));
        assertEquals("🇲🇲", LabPlaceCountries.flagOf("缅甸"));
        assertEquals("🇫🇷", LabPlaceCountries.flagOf("法国"));
    }

    @Test public void placeLabelsYieldCityOnly() {
        assertEquals("上海", LabPlaceCountries.cityOf("中国 · 上海"));
        assertEquals("妙瓦底（默认）", LabPlaceCountries.cityOf("缅甸 · 妙瓦底（默认）"));
        assertEquals("巴厘岛（登巴萨）",
                LabPlaceCountries.cityOf("印度尼西亚 · 巴厘岛（登巴萨）"));
    }

    @Test public void customFavoritesStayInTheirOwnCategory() {
        assertEquals(LabPlaceCountries.CUSTOM_GROUP,
                LabPlaceCountries.countryOf("家附近的测试点"));
        assertEquals("家附近的测试点",
                LabPlaceCountries.cityOf("家附近的测试点"));
        assertEquals("📍", LabPlaceCountries.flagOf(LabPlaceCountries.CUSTOM_GROUP));
    }

    @Test public void fakeCountryPrefixesCannotBecomeNationalFlag() {
        assertEquals(LabPlaceCountries.CUSTOM_GROUP,
                LabPlaceCountries.countryOf("New York · office"));
        assertEquals("New York · office",
                LabPlaceCountries.cityOf("New York · office"));
        assertFalse(LabPlaceCountries.isKnownCountry("New York"));
    }

    @Test public void nullOrBlankLabelsAreSafe() {
        assertEquals(LabPlaceCountries.CUSTOM_GROUP, LabPlaceCountries.countryOf(null));
        assertEquals(LabPlaceCountries.CUSTOM_GROUP, LabPlaceCountries.countryOf(""));
        assertEquals("未命名位置", LabPlaceCountries.cityOf(null));
        assertEquals("未命名位置", LabPlaceCountries.cityOf(" "));
    }

    @Test public void citiesSearchWorksWhenGroupsAreCollapsed() {
        assertTrue(LabPlaceSearch.matches("日本 · 东京（东京站）", "日本 东京"));
        assertFalse(LabPlaceSearch.matches("日本 · 大阪", "日本 东京"));
        assertTrue(LabPlaceSearch.matches("中国 · 上海", "中国"));
    }

    @Test public void unknownFlagsAreNeutralNotIncorrectNations() {
        assertEquals("📍", LabPlaceCountries.flagOf(null));
        assertEquals("📍", LabPlaceCountries.flagOf("不存在的国家"));
    }
}
