package com.zcshou.gogogo;

import static org.junit.Assert.*;

import org.junit.Test;
import java.util.HashSet;
import java.util.Set;

public class LabLocationPresetsTest {
    @Test public void defaultStillMyawaddy() {
        LabLocationPresets.Preset p = LabLocationPresets.defaultPreset();
        assertEquals("缅甸 · 妙瓦底（默认）", p.name);
        assertEquals(98.50895, p.longitude, 0.00000001);
        assertEquals(16.68914, p.latitude, 0.00000001);
        assertEquals(p, LabLocationPresets.get(0));
    }

    @Test public void allPresetsAreValidWgs84AndNamesUnique() {
        assertTrue(LabLocationPresets.size() >= 12);
        Set<String> names = new HashSet<>();
        Set<String> coordinates = new HashSet<>();
        for (int i = 0; i < LabLocationPresets.size(); i++) {
            LabLocationPresets.Preset p = LabLocationPresets.get(i);
            assertNotNull(p.name);
            assertTrue(p.latitude >= -90.0 && p.latitude <= 90.0);
            assertTrue(p.longitude >= -180.0 && p.longitude <= 180.0);
            assertTrue(Double.isFinite(p.longitude) && Double.isFinite(p.latitude));
            assertTrue("Repeated name: " + p.name, names.add(p.name));
            assertTrue("Repeated coordinates: " + p.name,
                    coordinates.add(p.longitude + "," + p.latitude));
            assertEquals(p.name, LabLocationPresets.labels()[i]);
        }
    }

    @Test public void includesBothEastAndWestAndSouthernHemisphere() {
        boolean west = false, east = false, south = false;
        for (int i = 0; i < LabLocationPresets.size(); i++) {
            LabLocationPresets.Preset p = LabLocationPresets.get(i);
            west |= p.longitude < 0;
            east |= p.longitude > 0;
            south |= p.latitude < 0;
        }
        assertTrue(west && east && south);
    }

    @Test public void returnLabelsAreSafeCopies() {
        String[] first = LabLocationPresets.labels();
        first[0] = "changed";
        assertEquals("缅甸 · 妙瓦底（默认）", LabLocationPresets.labels()[0]);
    }
}
