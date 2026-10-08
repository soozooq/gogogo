package com.zcshou.gogogo;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
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
        public final long timestampMillis;

        public RoutePoint(double longitude, double latitude, double altitude) {
            this(longitude, latitude, altitude, -1L);
        }

        public RoutePoint(
                double longitude,
                double latitude,
                double altitude,
                long timestampMillis) {
            this.longitude = longitude;
            this.latitude = latitude;
            this.altitude = altitude;
            this.timestampMillis = timestampMillis;
        }

        public boolean hasTimestamp() {
            return timestampMillis > 0L;
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

        String pointTag = null;
        Double pointLat = null;
        Double pointLon = null;
        double pointAlt = 55.0;
        long pointTime = -1L;

        int event = parser.getEventType();
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                String tag = parser.getName();

                if ("trkpt".equalsIgnoreCase(tag)
                        || "rtept".equalsIgnoreCase(tag)
                        || "wpt".equalsIgnoreCase(tag)) {
                    pointTag = tag.toLowerCase(Locale.US);
                    pointLat = parseDouble(parser.getAttributeValue(null, "lat"));
                    pointLon = parseDouble(parser.getAttributeValue(null, "lon"));
                    pointAlt = 55.0;
                    pointTime = -1L;
                } else if (pointTag != null && "ele".equalsIgnoreCase(tag)) {
                    Double alt = parseDouble(parser.nextText());
                    if (alt != null && Double.isFinite(alt)) {
                        pointAlt = alt;
                    }
                } else if (pointTag != null && "time".equalsIgnoreCase(tag)) {
                    pointTime = parseTimestamp(parser.nextText());
                } else if (pointTag == null && "coordinates".equalsIgnoreCase(tag)) {
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
                                kmlPoints.add(new RoutePoint(
                                        lon,
                                        lat,
                                        alt == null || !Double.isFinite(alt) ? 55.0 : alt,
                                        -1L));
                            }
                        }
                    }
                }
            } else if (event == XmlPullParser.END_TAG && pointTag != null) {
                String tag = parser.getName();
                if (pointTag.equalsIgnoreCase(tag)) {
                    if (pointLat != null
                            && pointLon != null
                            && valid(pointLon, pointLat)) {
                        RoutePoint point = new RoutePoint(
                                pointLon,
                                pointLat,
                                pointAlt,
                                pointTime);
                        if ("wpt".equals(pointTag)) {
                            if (waypointFallback.size() < MAX_POINTS) {
                                waypointFallback.add(point);
                            }
                        } else if (gpxPoints.size() < MAX_POINTS) {
                            gpxPoints.add(point);
                        }
                    }

                    pointTag = null;
                    pointLat = null;
                    pointLon = null;
                    pointAlt = 55.0;
                    pointTime = -1L;
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

    public static boolean hasUsableTimeline(List<RoutePoint> points) {
        if (points == null || points.size() < 2) return false;

        long first = points.get(0).timestampMillis;
        if (first <= 0L) return false;

        long previous = first;
        boolean advanced = false;
        for (int i = 1; i < points.size(); i++) {
            long time = points.get(i).timestampMillis;
            if (time <= 0L || time < previous) return false;
            if (time > previous) advanced = true;
            previous = time;
        }
        return advanced;
    }

    public static long timelineDurationMillis(List<RoutePoint> points) {
        if (!hasUsableTimeline(points)) return -1L;
        return Math.max(
                0L,
                points.get(points.size() - 1).timestampMillis
                        - points.get(0).timestampMillis);
    }

    static long parseTimestamp(String raw) {
        if (raw == null) return -1L;
        String clean = raw.trim();
        if (clean.isEmpty()) return -1L;

        try {
            return Instant.parse(clean).toEpochMilli();
        } catch (Throwable ignored) {
        }

        try {
            return OffsetDateTime.parse(clean).toInstant().toEpochMilli();
        } catch (Throwable ignored) {
            return -1L;
        }
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
