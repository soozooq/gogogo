package com.zcshou.gogogo;

import org.junit.Test;


import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class RouteFileParserTimelineTest {

    @Test
    public void parsesIsoTimestampAndBuildsTimelineMetadata() {
        long first = RouteFileParser.parseTimestamp("2026-10-08T00:00:00Z");
        long second = RouteFileParser.parseTimestamp("2026-10-08T00:00:10Z");

        java.util.ArrayList<RouteFileParser.RoutePoint> points =
                new java.util.ArrayList<>();
        points.add(new RouteFileParser.RoutePoint(139.0, 35.0, 12.5, first));
        points.add(new RouteFileParser.RoutePoint(139.001, 35.001, 18.0, second));

        assertEquals(12.5, points.get(0).altitude, 0.0001);
        assertEquals(18.0, points.get(1).altitude, 0.0001);
        assertTrue(points.get(0).hasTimestamp());
        assertTrue(RouteFileParser.hasUsableTimeline(points));
        assertEquals(10_000L, RouteFileParser.timelineDurationMillis(points));
    }

    @Test
    public void manualPointsDoNotPretendToHaveTimeline() {
        java.util.ArrayList<RouteFileParser.RoutePoint> points =
                new java.util.ArrayList<>();
        points.add(new RouteFileParser.RoutePoint(139.0, 35.0, 10.0));
        points.add(new RouteFileParser.RoutePoint(139.001, 35.001, 11.0));

        assertTrue(!RouteFileParser.hasUsableTimeline(points));
        assertEquals(-1L, RouteFileParser.timelineDurationMillis(points));
    }
}
