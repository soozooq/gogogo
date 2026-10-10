package com.zcshou.gogogo;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class LabHomeCoordinatesTest {
    @Test public void acceptsNormalLongitudeLatitudeWgs84() {
        LabHomeCoordinates.Point point = LabHomeCoordinates.parse("98.50895", "16.68914");
        assertEquals(98.50895, point.longitude, 0d);
        assertEquals(16.68914, point.latitude, 0d);
    }

    @Test public void trimsWhitespaceAndAcceptsNegativeValues() {
        LabHomeCoordinates.Point point = LabHomeCoordinates.parse(" -73.98 ", " 40.75 ");
        assertEquals(-73.98d, point.longitude, 0d);
        assertEquals(40.75d, point.latitude, 0d);
    }

    @Test public void acceptsBothBoundaryPairs() {
        LabHomeCoordinates.Point lower = LabHomeCoordinates.parse("-180", "-90");
        LabHomeCoordinates.Point upper = LabHomeCoordinates.parse("180", "90");
        assertEquals(-180d, lower.longitude, 0d);
        assertEquals(-90d, lower.latitude, 0d);
        assertEquals(180d, upper.longitude, 0d);
        assertEquals(90d, upper.latitude, 0d);
    }

    @Test public void rejectsLongitudeOutsideWgs84() {
        assertInvalid("180.00001", "0", "超出范围");
        assertInvalid("-180.00001", "0", "超出范围");
    }

    @Test public void rejectsLatitudeOutsideWgs84() {
        assertInvalid("0", "90.00001", "超出范围");
        assertInvalid("0", "-90.00001", "超出范围");
    }

    @Test public void rejectsNaNLongitudeThatPreviouslyBypassedRangeChecks() {
        assertInvalid("NaN", "16", "有效数字");
    }

    @Test public void rejectsNaNLatitudeThatPreviouslyBypassedRangeChecks() {
        assertInvalid("99", "NaN", "有效数字");
    }

    @Test public void rejectsInfinityCoordinates() {
        assertInvalid("Infinity", "0", "有效数字");
        assertInvalid("0", "-Infinity", "有效数字");
        assertInvalid("1e999", "0", "有效数字");
    }

    @Test public void rejectsMissingOrNonnumericInput() {
        assertInvalid("", "0", "格式");
        assertInvalid(null, "0", "格式");
        assertInvalid("98.5", "north", "格式");
    }

    @Test public void rejectsCommaDecimalAndUnparseableSymbols() {
        assertInvalid("98,5", "16", "格式");
        assertInvalid("九十八", "16", "格式");
    }

    private static void assertInvalid(String longitude, String latitude,
                                      String expectedMessagePart) {
        try {
            LabHomeCoordinates.parse(longitude, latitude);
            fail("Expected invalid coordinate input to be rejected");
        } catch (IllegalArgumentException invalid) {
            assertTrue(invalid.getMessage().contains(expectedMessagePart));
        }
    }
}
