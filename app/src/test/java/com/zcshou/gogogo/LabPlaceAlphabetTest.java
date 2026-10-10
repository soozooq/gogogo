package com.zcshou.gogogo;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.*;

public class LabPlaceAlphabetTest {
    @Test public void all109PresetCountriesHaveExplicitPinyinAndLetter() {
        Set<String> countries = new HashSet<>();
        for (int i = 0; i < LabLocationPresets.size(); i++) {
            String country = LabPlaceCountries.countryOf(
                    LabLocationPresets.get(i).name);
            String pinyin = LabPlaceAlphabet.pinyinOf(country);
            assertFalse("Missing Pinyin: " + country, pinyin.startsWith("~"));
            assertTrue("Invalid Pinyin: " + country, pinyin.matches("[a-z]+"));
            assertTrue("Invalid letter: " + country,
                    LabPlaceAlphabet.initialOf(country).matches("[A-Z]"));
            countries.add(country);
        }
        assertEquals(109, countries.size());
    }

    @Test public void commonCountryNamesJumpToExpectedLetters() {
        assertEquals("Z", LabPlaceAlphabet.initialOf("中国"));
        assertEquals("R", LabPlaceAlphabet.initialOf("日本"));
        assertEquals("M", LabPlaceAlphabet.initialOf("美国"));
        assertEquals("M", LabPlaceAlphabet.initialOf("缅甸"));
        assertEquals("F", LabPlaceAlphabet.initialOf("法国"));
        assertEquals("A", LabPlaceAlphabet.initialOf("阿联酋"));
        assertEquals("E", LabPlaceAlphabet.initialOf("俄罗斯"));
    }

    @Test public void sortUsesFullPinyinInsideEachLetterNotCatalogOrder() {
        List<String> countries = new ArrayList<>(Arrays.asList(
                "中国", "智利", "赞比亚", "日本", "瑞典", "法国", "俄罗斯"));
        countries.sort(LabPlaceAlphabet.COUNTRY_ORDER);
        assertEquals(Arrays.asList("俄罗斯", "法国", "日本", "瑞典",
                "赞比亚", "智利", "中国"), countries);
    }

    @Test public void collapsedSectionsHaveSequentialLetterAnchors() {
        List<String> countries = new ArrayList<>(Arrays.asList("日本","中国","美国","阿根廷"));
        countries.sort(LabPlaceAlphabet.COUNTRY_ORDER);
        Map<String,Integer> anchors = LabPlaceAlphabet.headerAnchors(
                countries, new HashMap<>());
        assertEquals(Integer.valueOf(0), anchors.get("A"));
        assertEquals(Integer.valueOf(1), anchors.get("M"));
        assertEquals(Integer.valueOf(2), anchors.get("R"));
        assertEquals(Integer.valueOf(3), anchors.get("Z"));
    }

    @Test public void expandedCitiesShiftLaterJumpTargets() {
        List<String> countries = new ArrayList<>(Arrays.asList("日本","中国","美国","阿根廷"));
        countries.sort(LabPlaceAlphabet.COUNTRY_ORDER);
        Map<String,Integer> expanded = new HashMap<>();
        expanded.put("阿根廷", 2);
        expanded.put("美国", 0);
        expanded.put("日本", 3);
        expanded.put("中国", 1);
        Map<String,Integer> anchors = LabPlaceAlphabet.headerAnchors(
                countries, expanded);
        assertEquals(Integer.valueOf(0), anchors.get("A"));
        assertEquals(Integer.valueOf(3), anchors.get("M"));
        assertEquals(Integer.valueOf(4), anchors.get("R"));
        assertEquals(Integer.valueOf(8), anchors.get("Z"));
    }

    @Test public void repeatedLetterPointsToFirstCountryOfGroup() {
        List<String> countries = new ArrayList<>(Arrays.asList(
                "阿联酋", "阿根廷", "阿尔及利亚", "美国"));
        countries.sort(LabPlaceAlphabet.COUNTRY_ORDER);
        Map<String,Integer> expanded = new HashMap<>();
        expanded.put("阿尔及利亚", 2);
        expanded.put("阿根廷", 1);
        Map<String,Integer> anchors = LabPlaceAlphabet.headerAnchors(
                countries, expanded);
        assertEquals(Integer.valueOf(0), anchors.get("A"));
        assertEquals(Integer.valueOf(6), anchors.get("M"));
        assertEquals(2, anchors.size());
    }

    @Test public void customFavoriteCategoryGoesLastWithNeutralIndex() {
        List<String> countries = new ArrayList<>(Arrays.asList(
                LabPlaceCountries.CUSTOM_GROUP, "美国", "中国", "阿曼"));
        countries.sort(LabPlaceAlphabet.COUNTRY_ORDER);
        assertEquals("阿曼", countries.get(0));
        assertEquals(LabPlaceCountries.CUSTOM_GROUP, countries.get(3));
        assertEquals("#", LabPlaceAlphabet.initialOf(LabPlaceCountries.CUSTOM_GROUP));
        Map<String,Integer> anchors = LabPlaceAlphabet.headerAnchors(
                countries, new HashMap<>());
        assertEquals(Integer.valueOf(3), anchors.get("#"));
    }

    @Test public void unknownNullCountriesAreSafe() {
        assertEquals("#", LabPlaceAlphabet.initialOf(null));
        assertTrue(LabPlaceAlphabet.pinyinOf(null).startsWith("~"));
    }

    @Test public void negativeOrMissingVisibleCountsCannotBreakAnchors() {
        List<String> countries = Arrays.asList("阿曼","法国");
        Map<String,Integer> values = new HashMap<>();
        values.put("阿曼", -200);
        assertEquals(Integer.valueOf(1),
                LabPlaceAlphabet.headerAnchors(countries, values).get("F"));
    }
}
