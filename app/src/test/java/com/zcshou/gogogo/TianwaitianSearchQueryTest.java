package com.zcshou.gogogo;

import org.junit.Test;
import static org.junit.Assert.*;

public class TianwaitianSearchQueryTest {
    @Test public void commaSeparatedCoordinatesAreLongitudeFirst() {
        LabHomeCoordinates.Point p =
                TianwaitianSearchQuery.coordinatesOrNull(" 98.50895,16.68914 ");
        assertNotNull(p);
        assertEquals(98.50895, p.longitude, 0.00000001);
        assertEquals(16.68914, p.latitude, 0.00000001);
    }

    @Test public void chineseCommaAndSignedCoordinatesWork() {
        LabHomeCoordinates.Point p =
                TianwaitianSearchQuery.coordinatesOrNull("-122.4194，37.7749");
        assertEquals(-122.4194, p.longitude, 0.00000001);
        assertEquals(37.7749, p.latitude, 0.00000001);
        assertTrue(TianwaitianSearchQuery.looksLikeCoordinates("-122.4194，37.7749"));
    }

    @Test public void labeledLongitudeAndLatitudeWork() {
        LabHomeCoordinates.Point p =
                TianwaitianSearchQuery.coordinatesOrNull("经度: 116.397 纬度: 39.908");
        assertNotNull(p);
        assertEquals(116.397, p.longitude, 0.0000001);
        assertEquals(39.908, p.latitude, 0.0000001);
    }

    @Test public void whitespaceAndNegativeZeroCoordinatesAreAllowed() {
        LabHomeCoordinates.Point p =
                TianwaitianSearchQuery.coordinatesOrNull("0  -0.001");
        assertEquals(0.0, p.longitude, 0.000001);
        assertEquals(-0.001, p.latitude, 0.000001);
    }

    @Test public void ordinarySearchIsNotMisidentifiedAsCoordinates() {
        assertNull(TianwaitianSearchQuery.coordinatesOrNull("东京"));
        assertNull(TianwaitianSearchQuery.coordinatesOrNull("New York"));
        assertNull(TianwaitianSearchQuery.coordinatesOrNull("San Francisco, USA"));
        assertNull(TianwaitianSearchQuery.coordinatesOrNull(null));
        assertFalse(TianwaitianSearchQuery.looksLikeCoordinates(""));
    }

    @Test public void outOfRangeCoordinatesAreRejectedInsteadOfGeocoded() {
        try {
            TianwaitianSearchQuery.coordinatesOrNull("181,30");
            fail("invalid longitude accepted");
        } catch (IllegalArgumentException expected) { }
        try {
            TianwaitianSearchQuery.coordinatesOrNull("98.5,91");
            fail("invalid latitude accepted");
        } catch (IllegalArgumentException expected) { }
        assertFalse(TianwaitianSearchQuery.validOnlineQuery("181,30"));
    }

    @Test public void remoteQueryNeedsDeliberateNonCoordinateText() {
        assertTrue(TianwaitianSearchQuery.validOnlineQuery("Tokyo Station"));
        assertTrue(TianwaitianSearchQuery.validOnlineQuery("东京站"));
        assertTrue(TianwaitianSearchQuery.validOnlineQuery("天安门"));
        assertFalse(TianwaitianSearchQuery.validOnlineQuery("a"));
        assertFalse(TianwaitianSearchQuery.validOnlineQuery(" "));
        assertFalse(TianwaitianSearchQuery.validOnlineQuery(null));
        assertFalse(TianwaitianSearchQuery.validOnlineQuery("98.50895,16.68914"));
        assertFalse(TianwaitianSearchQuery.validOnlineQuery(
                new String(new char[121]).replace('\0', 'a')));
    }

    @Test public void exactValidWgs84LimitsStillAccepted() {
        assertNotNull(TianwaitianSearchQuery.coordinatesOrNull("180,-90"));
        assertNotNull(TianwaitianSearchQuery.coordinatesOrNull("-180,90"));
    }
}
