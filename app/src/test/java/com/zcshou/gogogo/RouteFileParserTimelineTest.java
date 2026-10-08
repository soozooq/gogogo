package com.zcshou.gogogo;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class RouteFileParserTimelineTest {

    @Test
    public void parsesGpxElevationAndTime() throws Exception {
        String gpx = "<?xml version=\"1.0\"?><gpx version=\"1.1\">"
                + "<trk><trkseg>"
                + "<trkpt lat=\"35.0\" lon=\"139.0\">"
                + "<ele>12.5</ele><time>2026-10-08T00:00:00Z</time></trkpt>"
                + "<trkpt lat=\"35.001\" lon=\"139.001\">"
                + "<ele>18.0</ele><time>2026-10-08T00:00:10Z</time></trkpt>"
                + "</trkseg></trk></gpx>";

        List<RouteFileParser.RoutePoint> points = RouteFileParser.parse(
                "trace.gpx",
                new ByteArrayInputStream(gpx.getBytes(StandardCharsets.UTF_8)));

        assertEquals(2, points.size());
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
