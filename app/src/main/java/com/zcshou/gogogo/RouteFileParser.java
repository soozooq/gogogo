package com.zcshou.gogogo;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class RouteFileParser {
    public static final int MAX_POINTS = 5000;

    private RouteFileParser() {}

    public static final class RoutePoint {
        public final double longitude;
        public final double latitude;
        public final double altitude;

        public RoutePoint(double longitude, double latitude, double altitude) {
            this.longitude = longitude;
            this.latitude = latitude;
            this.altitude = altitude;
        }
    }

    public static List<RoutePoint> parse(String fileName, InputStream input) throws Exception {
        if (input == null) throw new IllegalArgumentException("input == null");
        String lower = fileName == null ? "" : fileName.toLowerCase(Locale.US);

        XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
        factory.setNamespaceAware(false);
        XmlPullParser parser = factory.newPullParser();
        try {
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL, false);
        } catch (Throwable ignored) {
        }
        parser.setInput(new InputStreamReader(input, StandardCharsets.UTF_8));

        List<RoutePoint> gpxPoints = new ArrayList<>();
        List<RoutePoint> waypointFallback = new ArrayList<>();
        List<RoutePoint> kmlPoints = new ArrayList<>();

        int event = parser.getEventType();
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                String tag = parser.getName();
                if ("trkpt".equalsIgnoreCase(tag) || "rtept".equalsIgnoreCase(tag)
                        || "wpt".equalsIgnoreCase(tag)) {
                    Double lat = parseDouble(parser.getAttributeValue(null, "lat"));
                    Double lon = parseDouble(parser.getAttributeValue(null, "lon"));
                    if (lat != null && lon != null && valid(lon, lat)) {
                        RoutePoint p = new RoutePoint(lon, lat, 55.0);
                        if ("wpt".equalsIgnoreCase(tag)) {
                            if (waypointFallback.size() < MAX_POINTS) waypointFallback.add(p);
                        } else {
                            if (gpxPoints.size() < MAX_POINTS) gpxPoints.add(p);
                        }
                    }
                } else if ("coordinates".equalsIgnoreCase(tag)) {
                    String text = parser.nextText();
                    if (text != null) {
                        String[] chunks = text.trim().split("\\s+");
                        for (String chunk : chunks) {
                            if (chunk.isEmpty() || kmlPoints.size() >= MAX_POINTS) break;
                            String[] parts = chunk.split(",");
                            if (parts.length < 2) continue;
                            Double lon = parseDouble(parts[0]);
                            Double lat = parseDouble(parts[1]);
                            Double alt = parts.length >= 3 ? parseDouble(parts[2]) : null;
                            if (lon != null && lat != null && valid(lon, lat)) {
                                kmlPoints.add(new RoutePoint(lon, lat, alt == null ? 55.0 : alt));
                            }
                        }
                    }
                }
            }
            event = parser.next();
        }

        if (!kmlPoints.isEmpty() || lower.endsWith(".kml")) {
            return kmlPoints;
        }
        if (!gpxPoints.isEmpty()) return gpxPoints;
        return waypointFallback;
    }

    private static Double parseDouble(String raw) {
        try {
            return raw == null ? null : Double.parseDouble(raw.trim());
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean valid(double lon, double lat) {
        return lon >= -180.0 && lon <= 180.0 && lat >= -90.0 && lat <= 90.0;
    }
}
