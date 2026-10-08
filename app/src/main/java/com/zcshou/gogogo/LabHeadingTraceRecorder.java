package com.zcshou.gogogo;

import java.util.ArrayDeque;
import java.util.Locale;

/**
 * Lab 15 in-memory heading flight recorder.
 *
 * Stores a bounded time series for GoGoGo's own diagnostics. The recorder intentionally
 * contains no cross-app hooks and is cleared when the service process dies.
 */
public final class LabHeadingTraceRecorder {
    private static final int MAX_SAMPLES = 2400;
    private static final long MIN_INTERVAL_MS = 100L;

    private final ArrayDeque<Sample> samples = new ArrayDeque<>();
    private long lastSampleElapsedMs = Long.MIN_VALUE;

    public synchronized void add(
            long elapsedMs,
            float fusedDeg,
            float absoluteDeg,
            float gameDeg,
            float attitudeDeg,
            float pitchDeg,
            float rollDeg,
            String posture,
            double magneticUt,
            double confidence,
            String state,
            String source) {
        if (lastSampleElapsedMs != Long.MIN_VALUE
                && elapsedMs - lastSampleElapsedMs < MIN_INTERVAL_MS) {
            return;
        }

        lastSampleElapsedMs = elapsedMs;

        while (samples.size() >= MAX_SAMPLES) {
            samples.removeFirst();
        }

        samples.addLast(new Sample(
                elapsedMs,
                fusedDeg,
                absoluteDeg,
                gameDeg,
                attitudeDeg,
                pitchDeg,
                rollDeg,
                posture == null ? "UNKNOWN" : posture,
                magneticUt,
                confidence,
                state == null ? "UNKNOWN" : state,
                source == null ? "UNKNOWN" : source));
    }

    public synchronized void clear() {
        samples.clear();
        lastSampleElapsedMs = Long.MIN_VALUE;
    }

    public synchronized int size() {
        return samples.size();
    }

    public synchronized String[] toCsvRows() {
        String[] rows = new String[samples.size() + 1];
        rows[0] = "elapsed_ms,fused_deg,absolute_deg,game_deg,attitude_deg,pitch_deg,roll_deg,posture,magnetic_ut,confidence,state,source";
        int i = 1;
        for (Sample s : samples) {
            rows[i++] = s.toCsv();
        }
        return rows;
    }

    public static final class Sample {
        public final long elapsedMs;
        public final float fusedDeg;
        public final float absoluteDeg;
        public final float gameDeg;
        public final float attitudeDeg;
        public final float pitchDeg;
        public final float rollDeg;
        public final String posture;
        public final double magneticUt;
        public final double confidence;
        public final String state;
        public final String source;

        Sample(
                long elapsedMs,
                float fusedDeg,
                float absoluteDeg,
                float gameDeg,
                float attitudeDeg,
                float pitchDeg,
                float rollDeg,
                String posture,
                double magneticUt,
                double confidence,
                String state,
                String source) {
            this.elapsedMs = elapsedMs;
            this.fusedDeg = fusedDeg;
            this.absoluteDeg = absoluteDeg;
            this.gameDeg = gameDeg;
            this.attitudeDeg = attitudeDeg;
            this.pitchDeg = pitchDeg;
            this.rollDeg = rollDeg;
            this.posture = posture;
            this.magneticUt = magneticUt;
            this.confidence = confidence;
            this.state = state;
            this.source = source;
        }

        String toCsv() {
            return String.format(Locale.US,
                    "%d,%s,%s,%s,%s,%.3f,%.3f,%s,%s,%.4f,%s,%s",
                    elapsedMs,
                    f(fusedDeg),
                    f(absoluteDeg),
                    f(gameDeg),
                    f(attitudeDeg),
                    pitchDeg,
                    rollDeg,
                    posture,
                    d(magneticUt),
                    confidence,
                    state,
                    source);
        }

        private static String f(float value) {
            if (Float.isNaN(value) || Float.isInfinite(value)) return "";
            return String.format(Locale.US, "%.3f", value);
        }

        private static String d(double value) {
            if (Double.isNaN(value) || Double.isInfinite(value)) return "";
            return String.format(Locale.US, "%.3f", value);
        }
    }
}
