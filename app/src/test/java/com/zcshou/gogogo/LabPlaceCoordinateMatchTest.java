package com.zcshou.gogogo;

import org.junit.Test;

import static org.junit.Assert.*;

public class LabPlaceCoordinateMatchTest {
    @Test public void identicalLocationsMatch() {
        assertTrue(LabPlaceCoordinateMatch.same(116.39747, 39.90549,
                116.39747, 39.90549));
    }

    @Test public void tinyRoundingDifferencesMatch() {
        assertTrue(LabPlaceCoordinateMatch.same(116.3974700, 39.9054900,
                116.3974704, 39.9054896));
    }

    @Test public void realDifferentLocationsDoNotMatch() {
        assertFalse(LabPlaceCoordinateMatch.same(116.39747, 39.90549,
                116.39847, 39.90549));
    }

    @Test public void longitudeLatitudeOrderDoesNotAccidentallyMatch() {
        assertFalse(LabPlaceCoordinateMatch.same(116.39747, 39.90549,
                39.90549, 116.39747));
    }

    @Test public void nanAndInfinityCannotBecomeFavorites() {
        assertFalse(LabPlaceCoordinateMatch.same(Double.NaN, 0, Double.NaN, 0));
        assertFalse(LabPlaceCoordinateMatch.same(0, Double.POSITIVE_INFINITY,
                0, Double.POSITIVE_INFINITY));
        assertFalse(LabPlaceCoordinateMatch.same(0, 0,
                Double.NEGATIVE_INFINITY, 0));
    }

    @Test public void matchingIsSymmetric() {
        assertEquals(LabPlaceCoordinateMatch.same(120.1, 30.2, 120.1000003, 30.2000002),
                LabPlaceCoordinateMatch.same(120.1000003, 30.2000002, 120.1, 30.2));
    }

    @Test public void exactZeroLocationIsValid() {
        assertTrue(LabPlaceCoordinateMatch.same(0, 0, 0, 0));
    }

    @Test public void toleranceBoundaryIsExclusive() {
        assertFalse(LabPlaceCoordinateMatch.same(0, 0, 0.000001, 0));
    }
}
