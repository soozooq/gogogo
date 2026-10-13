package com.zcshou.utils;

import org.junit.Test;
import static org.junit.Assert.*;

/** Coordinate regression checks without any Android runtime or Tencent Map API. */
public class ChinaGcjRegionTest {
    private static void same(double[] a, double longitude, double latitude) {
        assertEquals(longitude, a[0], 1e-10);
        assertEquals(latitude, a[1], 1e-10);
    }

    @Test public void publishedMainlandLocationsAreInsideApproximation() {
        assertTrue(ChinaGcjRegion.contains(116.39747, 39.90549)); // Beijing
        assertTrue(ChinaGcjRegion.contains(114.0579, 22.5431)); // Shenzhen
        assertTrue(ChinaGcjRegion.contains(102.72, 25.04)); // Kunming
        assertTrue(ChinaGcjRegion.contains(110.32, 20.01)); // Hainan
        assertTrue(ChinaGcjRegion.contains(91.11, 29.65)); // Lhasa
        assertTrue(ChinaGcjRegion.contains(87.61, 43.82)); // Urumqi
    }

    @Test public void internationalLocationsDoNotGetArtificialGcjOffsets() {
        double[][] outside = {
            {98.5, 16.69},   // Myawaddy, Myanmar (home preset!)
            {96.2, 16.86},   // Yangon, Myanmar
            {100.5, 13.75}, // Bangkok, Thailand
            {85.32, 27.72}, // Kathmandu, Nepal
            {139.69, 35.68},// Tokyo, Japan
            {-122.42, 37.77}, // San Francisco, US
            {121.56, 25.03} // Taipei
        };
        for (double[] p : outside) {
            assertFalse("outside boundary: " + p[0] + "," + p[1],
                    ChinaGcjRegion.contains(p[0], p[1]));
            same(MapUtils.gcj02towgs84(p[0], p[1]), p[0], p[1]);
            same(MapUtils.wgs2gcj02(p[0], p[1]), p[0], p[1]);
        }
    }

    @Test public void mainlandStillAppliesOffsetAndHasStableRoundTrip() {
        double lng = 116.39747, lat = 39.90549;
        double[] wgs = MapUtils.gcj02towgs84(lng, lat);
        assertTrue(Math.abs(wgs[0] - lng) > 0.001);
        assertTrue(Math.abs(wgs[1] - lat) > 0.001);
        double[] gcj = MapUtils.wgs2gcj02(wgs[0], wgs[1]);
        assertEquals(lng, gcj[0], 0.00004);
        assertEquals(lat, gcj[1], 0.00004);
    }

    @Test public void invalidCoordinatesCannotBeInside() {
        assertFalse(ChinaGcjRegion.contains(Double.NaN, 20.0));
        assertFalse(ChinaGcjRegion.contains(Double.POSITIVE_INFINITY, 20.0));
        assertFalse(ChinaGcjRegion.contains(100.0, Double.NEGATIVE_INFINITY));
    }
}
