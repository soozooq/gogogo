package com.zcshou.gogogo;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Lab 17 bounded compatibility-session timeline.
 *
 * Stores only GoGoGo consumer-side events and standard location observations.
 */
public final class LabCompatibilitySessionRecorder {
    private static final int MAX_EVENTS = 12000;

    private final ArrayDeque<Event> events = new ArrayDeque<>();
    private long sessionStartElapsedMs = -1L;
    private boolean running = false;

    public synchronized void start(long elapsedMs) {
        events.clear();
        sessionStartElapsedMs = elapsedMs;
        running = true;
        addMarkerLocked(elapsedMs, "SESSION_START");
    }

    public synchronized void stop(long elapsedMs) {
        if (!running) return;
        addMarkerLocked(elapsedMs, "SESSION_STOP");
        running = false;
    }

    public synchronized void clear() {
        events.clear();
        sessionStartElapsedMs = -1L;
        running = false;
    }

    public synchronized boolean isRunning() {
        return running;
    }

    public synchronized int size() {
        return events.size();
    }

    public synchronized long durationMs(long nowElapsedMs) {
        if (sessionStartElapsedMs < 0L) return 0L;
        long end = nowElapsedMs;
        if (!running && !events.isEmpty()) {
            end = events.peekLast().elapsedRealtimeMs;
        }
        return Math.max(0L, end - sessionStartElapsedMs);
    }

    public synchronized void addMarker(long elapsedMs, String marker) {
        if (!running) return;
        addMarkerLocked(elapsedMs, marker);
    }

    private void addMarkerLocked(long elapsedMs, String marker) {
        addEventLocked(new Event(
                relative(elapsedMs),
                elapsedMs,
                "MARKER",
                marker == null ? "UNKNOWN" : marker,
                false,
                0.0,
                0.0,
                0f,
                0f,
                0f,
                false));
    }

    public synchronized void addLocation(
            long elapsedMs,
            String channel,
            double latitude,
            double longitude,
            float accuracy,
            float speed,
            float bearing,
            boolean mockMarked) {
        if (!running) return;
        addEventLocked(new Event(
                relative(elapsedMs),
                elapsedMs,
                "LOCATION",
                channel == null ? "UNKNOWN" : channel,
                true,
                latitude,
                longitude,
                accuracy,
                speed,
                bearing,
                mockMarked));
    }

    public synchronized void addError(long elapsedMs, String channel, String error) {
        if (!running) return;
        addEventLocked(new Event(
                relative(elapsedMs),
                elapsedMs,
                "ERROR",
                (channel == null ? "UNKNOWN" : channel)
                        + ":" + (error == null ? "UNKNOWN" : error),
                false,
                0.0,
                0.0,
                0f,
                0f,
                0f,
                false));
    }

    private long relative(long elapsedMs) {
        if (sessionStartElapsedMs < 0L) return 0L;
        return Math.max(0L, elapsedMs - sessionStartElapsedMs);
    }

    private void addEventLocked(Event event) {
        while (events.size() >= MAX_EVENTS) {
            events.removeFirst();
        }
        events.addLast(event);
    }

    public synchronized Summary summarize() {
        StreamStats gps = new StreamStats("GPS");
        StreamStats network = new StreamStats("NETWORK");
        StreamStats gms = new StreamStats("GMS");
        int backgroundMarkers = 0;
        int foregroundMarkers = 0;
        int errorCount = 0;
        int mockMarkedLocations = 0;

        for (Event event : events) {
            if ("MARKER".equals(event.kind)) {
                if ("UI_BACKGROUND".equals(event.label)) backgroundMarkers++;
                if ("UI_FOREGROUND".equals(event.label)) foregroundMarkers++;
                continue;
            }
            if ("ERROR".equals(event.kind)) {
                errorCount++;
                continue;
            }
            if (!"LOCATION".equals(event.kind)) continue;

            if (event.mockMarked) mockMarkedLocations++;

            if ("GPS".equals(event.label)) {
                gps.accept(event);
            } else if ("NETWORK".equals(event.label)) {
                network.accept(event);
            } else if ("GMS_UPDATES".equals(event.label)) {
                gms.accept(event);
            }
        }

        double maxSeparation = maxLastSeparation(gps.last, network.last, gms.last);

        return new Summary(
                gps.freeze(),
                network.freeze(),
                gms.freeze(),
                backgroundMarkers,
                foregroundMarkers,
                errorCount,
                mockMarkedLocations,
                maxSeparation,
                events.size());
    }

    private static double maxLastSeparation(Event... lastEvents) {
        double max = 0.0;
        for (int i = 0; i < lastEvents.length; i++) {
            Event a = lastEvents[i];
            if (a == null) continue;
            for (int j = i + 1; j < lastEvents.length; j++) {
                Event b = lastEvents[j];
                if (b == null) continue;
                max = Math.max(max, distanceMeters(
                        a.latitude, a.longitude,
                        b.latitude, b.longitude));
            }
        }
        return max;
    }

    private static double distanceMeters(
            double lat1, double lon1, double lat2, double lon2) {
        final double earth = 6371000.0;
        double p1 = Math.toRadians(lat1);
        double p2 = Math.toRadians(lat2);
        double dp = Math.toRadians(lat2 - lat1);
        double dl = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dp / 2.0) * Math.sin(dp / 2.0)
                + Math.cos(p1) * Math.cos(p2)
                * Math.sin(dl / 2.0) * Math.sin(dl / 2.0);
        return earth * 2.0
                * Math.atan2(Math.sqrt(a), Math.sqrt(Math.max(0.0, 1.0 - a)));
    }

    public synchronized String[] toCsvRows() {
        List<String> rows = new ArrayList<>(events.size() + 1);
        rows.add("relative_ms,elapsed_realtime_ms,kind,label,latitude,longitude,accuracy_m,speed_mps,bearing_deg,is_mock");
        for (Event event : events) {
            rows.add(event.toCsv());
        }
        return rows.toArray(new String[0]);
    }

    public static final class Event {
        public final long relativeMs;
        public final long elapsedRealtimeMs;
        public final String kind;
        public final String label;
        public final boolean hasLocation;
        public final double latitude;
        public final double longitude;
        public final float accuracy;
        public final float speed;
        public final float bearing;
        public final boolean mockMarked;

        Event(
                long relativeMs,
                long elapsedRealtimeMs,
                String kind,
                String label,
                boolean hasLocation,
                double latitude,
                double longitude,
                float accuracy,
                float speed,
                float bearing,
                boolean mockMarked) {
            this.relativeMs = relativeMs;
            this.elapsedRealtimeMs = elapsedRealtimeMs;
            this.kind = kind;
            this.label = label;
            this.hasLocation = hasLocation;
            this.latitude = latitude;
            this.longitude = longitude;
            this.accuracy = accuracy;
            this.speed = speed;
            this.bearing = bearing;
            this.mockMarked = mockMarked;
        }

        String toCsv() {
            return String.format(Locale.US,
                    "%d,%d,%s,%s,%s,%s,%s,%s,%s,%s",
                    relativeMs,
                    elapsedRealtimeMs,
                    esc(kind),
                    esc(label),
                    hasLocation ? String.format(Locale.US, "%.7f", latitude) : "",
                    hasLocation ? String.format(Locale.US, "%.7f", longitude) : "",
                    hasLocation ? String.format(Locale.US, "%.2f", accuracy) : "",
                    hasLocation ? String.format(Locale.US, "%.3f", speed) : "",
                    hasLocation ? String.format(Locale.US, "%.2f", bearing) : "",
                    hasLocation ? String.valueOf(mockMarked) : "");
        }

        private static String esc(String value) {
            if (value == null) return "";
            return value.replace(",", "_");
        }
    }

    private static final class StreamStats {
        final String name;
        int count;
        long firstMs = -1L;
        long lastMs = -1L;
        long maxGapMs = 0L;
        Event last;

        StreamStats(String name) {
            this.name = name;
        }

        void accept(Event event) {
            count++;
            if (firstMs < 0L) firstMs = event.relativeMs;
            if (lastMs >= 0L) {
                maxGapMs = Math.max(maxGapMs, event.relativeMs - lastMs);
            }
            lastMs = event.relativeMs;
            last = event;
        }

        StreamSummary freeze() {
            return new StreamSummary(name, count, firstMs, lastMs, maxGapMs);
        }
    }

    public static final class StreamSummary {
        public final String name;
        public final int count;
        public final long firstRelativeMs;
        public final long lastRelativeMs;
        public final long maxGapMs;

        StreamSummary(
                String name,
                int count,
                long firstRelativeMs,
                long lastRelativeMs,
                long maxGapMs) {
            this.name = name;
            this.count = count;
            this.firstRelativeMs = firstRelativeMs;
            this.lastRelativeMs = lastRelativeMs;
            this.maxGapMs = maxGapMs;
        }
    }

    public static final class Summary {
        public final StreamSummary gps;
        public final StreamSummary network;
        public final StreamSummary gms;
        public final int backgroundMarkers;
        public final int foregroundMarkers;
        public final int errorCount;
        public final int mockMarkedLocations;
        public final double maxLastSeparationMeters;
        public final int eventCount;

        Summary(
                StreamSummary gps,
                StreamSummary network,
                StreamSummary gms,
                int backgroundMarkers,
                int foregroundMarkers,
                int errorCount,
                int mockMarkedLocations,
                double maxLastSeparationMeters,
                int eventCount) {
            this.gps = gps;
            this.network = network;
            this.gms = gms;
            this.backgroundMarkers = backgroundMarkers;
            this.foregroundMarkers = foregroundMarkers;
            this.errorCount = errorCount;
            this.mockMarkedLocations = mockMarkedLocations;
            this.maxLastSeparationMeters = maxLastSeparationMeters;
            this.eventCount = eventCount;
        }

        public String grade() {
            if (gps.count > 0 && network.count > 0 && gms.count > 0
                    && gps.maxGapMs <= 1500L
                    && network.maxGapMs <= 1500L
                    && gms.maxGapMs <= 1500L
                    && maxLastSeparationMeters <= 10.0
                    && errorCount == 0) {
                return "STABLE";
            }
            if ((gps.count > 0 ? 1 : 0)
                    + (network.count > 0 ? 1 : 0)
                    + (gms.count > 0 ? 1 : 0) >= 2) {
                return "PARTIAL";
            }
            return "DEGRADED";
        }
    }
}
