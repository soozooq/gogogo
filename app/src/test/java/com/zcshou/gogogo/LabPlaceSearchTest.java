package com.zcshou.gogogo;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LabPlaceSearchTest {
    @Test public void emptyQueryShowsEverything() {
        assertTrue(LabPlaceSearch.matches("日本 · 东京", ""));
        assertTrue(LabPlaceSearch.matches("中国 · 北京（天安门广场）", null));
    }

    @Test public void findsCountryAcrossMultipleCities() {
        assertTrue(LabPlaceSearch.matches("中国 · 上海", "中国"));
        assertTrue(LabPlaceSearch.matches("中国 · 北京", "中国"));
        assertFalse(LabPlaceSearch.matches("日本 · 东京", "中国"));
    }

    @Test public void searchesCityWithoutCountry() {
        assertTrue(LabPlaceSearch.matches("缅甸 · 妙瓦底（默认）", "妙瓦底"));
        assertTrue(LabPlaceSearch.matches("加拿大 · 多伦多", "多伦多"));
        assertFalse(LabPlaceSearch.matches("法国 · 巴黎", "多伦多"));
    }

    @Test public void multipleTermsNarrowResults() {
        assertTrue(LabPlaceSearch.matches("中国 · 上海", " 中国  上海 "));
        assertFalse(LabPlaceSearch.matches("中国 · 北京", "中国 上海"));
    }

    @Test public void insensitiveToSeparatorsAndPunctuation() {
        assertTrue(LabPlaceSearch.matches("中国 · 北京（天安门广场）", "中国 北京"));
        assertTrue(LabPlaceSearch.matches("中国 · 北京（天安门广场）", "天安门"));
    }

    @Test public void latinNamesAreCaseInsensitiveWhenStoredInFavorites() {
        assertTrue(LabPlaceSearch.matches("New York · office", "new YORK"));
    }

    @Test public void noLabelIsNeverAResult() {
        assertFalse(LabPlaceSearch.matches(null, "东京"));
        assertFalse(LabPlaceSearch.matches(null, ""));
    }

    @Test public void accentsAndUnknownTermsDoNotMatchUnexpectedly() {
        assertFalse(LabPlaceSearch.matches("法国 · 巴黎", "东京"));
        assertFalse(LabPlaceSearch.matches("法国 · 巴黎", "法国 东京"));
    }
}
