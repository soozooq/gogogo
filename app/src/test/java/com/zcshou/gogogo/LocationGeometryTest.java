package com.zcshou.gogogo;

import org.junit.Test;

import static org.junit.Assert.*;

public class LocationGeometryTest {
    @Test public void roamTargetsStayValidAtBothPolesAndTheDateline() {
        for (double[] center : new double[][] {{90, 0}, {-90, 0}, {89.999, 179.999},
                {-89.999, -179.999}, {0, 179.999}, {40.7, -74}}) {
            for (int bearing = 0; bearing < 360; bearing += 15) {
                double[] point = LocationGeometry.destination(center[0], center[1], bearing, 5000);
                assertTrue(LocationPublishGate.validTarget(point[0], point[1], 0));
                assertEquals(5000, LocationGeometry.distanceMeters(
                        center[0], center[1], point[0], point[1]), 0.01);
            }
        }
    }

    @Test public void crossingTheNorthPoleReflectsLatitudeInsteadOfExceedingNinety() {
        double[] point = LocationGeometry.destination(89.999, 0, 0, 5000);
        assertTrue(point[0] < 90);
        assertTrue(point[0] > 89.9);
        assertEquals(180, Math.abs(point[1]), 1e-6);
    }

    @Test public void antipodalDistancesRemainFiniteDespiteRounding() {
        for (int i = 0; i < 50; i++) {
            double lat = -89.9 + i * 3.6;
            double lon = -179.7 + i * 7.1;
            double distance = LocationGeometry.distanceMeters(lat, lon, -lat,
                    LocationGeometry.normalizeLongitude(lon + 180));
            assertTrue(Double.isFinite(distance));
            assertEquals(Math.PI * 6371000, distance, 0.3);
        }
    }

    @Test(timeout = 1000) public void longitudeNormalizationIsBoundedForHugeFiniteValues() {
        for (double lon : new double[] {Double.MAX_VALUE, -Double.MAX_VALUE, 540, -540, 180, -180}) {
            double result = LocationGeometry.normalizeLongitude(lon);
            assertTrue(result >= -180 && result <= 180);
        }
        assertEquals(-179, LocationGeometry.normalizeLongitude(181), 0);
        assertEquals(179, LocationGeometry.normalizeLongitude(-181), 0);
    }

    @Test public void rejectsNonFiniteOrImpossibleDestinations() {
        assertThrows(IllegalArgumentException.class, () -> LocationGeometry.destination(91, 0, 0, 10));
        assertThrows(IllegalArgumentException.class, () -> LocationGeometry.destination(0, 0, Double.NaN, 10));
        assertThrows(IllegalArgumentException.class, () -> LocationGeometry.destination(0, 0, 0, -1));
        assertThrows(IllegalArgumentException.class, () -> LocationGeometry.destination(0, 0, 0, Double.POSITIVE_INFINITY));
    }
}
