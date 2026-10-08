package com.zcshou.gogogo;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Lab 20 route checkpoint store.
 *
 * Route geometry is written only when a route is replaced. Small progress metadata
 * is then checkpointed periodically, so a process death does not require rewriting
 * thousands of route points every few seconds.
 */
public final class LabRouteCheckpointStore {
    private static final String PREFS = "lab20_route_checkpoint";
    private static final String ROUTE_FILE = "lab20_route_geometry.bin";
    private static final String ROUTE_FILE_TMP = "lab20_route_geometry.bin.tmp";
    private static final int MAGIC = 0x474F3230; // "GO20"
    private static final int VERSION = 1;
    private static final int MAX_POINTS = 5000;

    private final Context context;
    private final SharedPreferences prefs;

    public LabRouteCheckpointStore(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = this.context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public synchronized void replaceRoute(
            double[] lats,
            double[] lngs,
            Progress progress) throws IOException {
        if (lats == null || lngs == null
                || lats.length < 2
                || lats.length != lngs.length
                || lats.length > MAX_POINTS) {
            throw new IllegalArgumentException("invalid route geometry");
        }

        writeGeometryAtomically(lats, lngs);
        saveProgress(progress);
    }

    public synchronized void saveProgress(Progress progress) {
        if (progress == null) return;

        prefs.edit()
                .putBoolean("active", true)
                .putInt("index", progress.routeIndex)
                .putInt("direction", progress.routeDirection)
                .putInt("mode", progress.routeMode)
                .putInt("motion_profile", progress.motionProfile)
                .putLong("speed_bits", Double.doubleToRawLongBits(progress.routeSpeedMps))
                .putBoolean("paused", progress.paused)
                .putLong("multiplier_bits", Double.doubleToRawLongBits(progress.multiplier))
                .putLong("lat_bits", Double.doubleToRawLongBits(progress.latitude))
                .putLong("lng_bits", Double.doubleToRawLongBits(progress.longitude))
                .putLong("alt_bits", Double.doubleToRawLongBits(progress.altitude))
                .putInt("bearing_bits", Float.floatToRawIntBits(progress.bearingDegrees))
                .putLong("saved_at_wall_ms", System.currentTimeMillis())
                .apply();
    }

    public synchronized Checkpoint load() {
        if (!prefs.getBoolean("active", false)) return null;

        double[][] geometry;
        try {
            geometry = readGeometry();
        } catch (Throwable t) {
            clear();
            return null;
        }
        if (geometry == null || geometry.length != 2 || geometry[0].length < 2) {
            clear();
            return null;
        }

        Progress progress = new Progress(
                prefs.getInt("index", 0),
                prefs.getInt("direction", 1),
                prefs.getInt("mode", 0),
                prefs.getInt("motion_profile", 1),
                Double.longBitsToDouble(
                        prefs.getLong("speed_bits", Double.doubleToRawLongBits(1.4))),
                prefs.getBoolean("paused", false),
                Double.longBitsToDouble(
                        prefs.getLong("multiplier_bits", Double.doubleToRawLongBits(1.0))),
                Double.longBitsToDouble(
                        prefs.getLong("lat_bits", Double.doubleToRawLongBits(geometry[0][0]))),
                Double.longBitsToDouble(
                        prefs.getLong("lng_bits", Double.doubleToRawLongBits(geometry[1][0]))),
                Double.longBitsToDouble(
                        prefs.getLong("alt_bits", Double.doubleToRawLongBits(55.0))),
                Float.intBitsToFloat(
                        prefs.getInt("bearing_bits", Float.floatToRawIntBits(0f))));

        return new Checkpoint(
                geometry[0],
                geometry[1],
                progress,
                prefs.getLong("saved_at_wall_ms", 0L));
    }

    public synchronized void clear() {
        prefs.edit().clear().apply();
        File file = new File(context.getFilesDir(), ROUTE_FILE);
        File tmp = new File(context.getFilesDir(), ROUTE_FILE_TMP);
        if (file.exists()) {
            //noinspection ResultOfMethodCallIgnored
            file.delete();
        }
        if (tmp.exists()) {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
        }
    }

    private void writeGeometryAtomically(double[] lats, double[] lngs) throws IOException {
        File tmp = new File(context.getFilesDir(), ROUTE_FILE_TMP);
        File dest = new File(context.getFilesDir(), ROUTE_FILE);

        try (FileOutputStream fos = new FileOutputStream(tmp);
             BufferedOutputStream bos = new BufferedOutputStream(fos);
             DataOutputStream out = new DataOutputStream(bos)) {
            out.writeInt(MAGIC);
            out.writeInt(VERSION);
            out.writeInt(lats.length);
            for (int i = 0; i < lats.length; i++) {
                out.writeDouble(lats[i]);
                out.writeDouble(lngs[i]);
            }
            out.flush();
            fos.getFD().sync();
        }

        if (dest.exists() && !dest.delete()) {
            throw new IOException("failed to replace old route geometry");
        }
        if (!tmp.renameTo(dest)) {
            throw new IOException("failed to commit route geometry");
        }
    }

    private double[][] readGeometry() throws IOException {
        File file = new File(context.getFilesDir(), ROUTE_FILE);
        if (!file.exists()) return null;

        try (DataInputStream in = new DataInputStream(
                new BufferedInputStream(new FileInputStream(file)))) {
            if (in.readInt() != MAGIC) throw new IOException("bad route magic");
            if (in.readInt() != VERSION) throw new IOException("bad route version");
            int count = in.readInt();
            if (count < 2 || count > MAX_POINTS) {
                throw new IOException("bad route point count");
            }

            double[] lats = new double[count];
            double[] lngs = new double[count];
            for (int i = 0; i < count; i++) {
                lats[i] = in.readDouble();
                lngs[i] = in.readDouble();
                if (!Double.isFinite(lats[i]) || !Double.isFinite(lngs[i])) {
                    throw new IOException("non-finite route coordinate");
                }
            }
            return new double[][]{lats, lngs};
        }
    }

    public static final class Progress {
        public final int routeIndex;
        public final int routeDirection;
        public final int routeMode;
        public final int motionProfile;
        public final double routeSpeedMps;
        public final boolean paused;
        public final double multiplier;
        public final double latitude;
        public final double longitude;
        public final double altitude;
        public final float bearingDegrees;

        public Progress(
                int routeIndex,
                int routeDirection,
                int routeMode,
                double routeSpeedMps,
                boolean paused,
                double multiplier,
                double latitude,
                double longitude,
                double altitude,
                float bearingDegrees) {
            this(
                    routeIndex,
                    routeDirection,
                    routeMode,
                    1,
                    routeSpeedMps,
                    paused,
                    multiplier,
                    latitude,
                    longitude,
                    altitude,
                    bearingDegrees);
        }

        public Progress(
                int routeIndex,
                int routeDirection,
                int routeMode,
                int motionProfile,
                double routeSpeedMps,
                boolean paused,
                double multiplier,
                double latitude,
                double longitude,
                double altitude,
                float bearingDegrees) {
            this.routeIndex = routeIndex;
            this.routeDirection = routeDirection;
            this.routeMode = routeMode;
            this.motionProfile = motionProfile;
            this.routeSpeedMps = routeSpeedMps;
            this.paused = paused;
            this.multiplier = multiplier;
            this.latitude = latitude;
            this.longitude = longitude;
            this.altitude = altitude;
            this.bearingDegrees = bearingDegrees;
        }
    }

    public static final class Checkpoint {
        public final double[] routeLats;
        public final double[] routeLngs;
        public final Progress progress;
        public final long savedAtWallMs;

        Checkpoint(
                double[] routeLats,
                double[] routeLngs,
                Progress progress,
                long savedAtWallMs) {
            this.routeLats = routeLats;
            this.routeLngs = routeLngs;
            this.progress = progress;
            this.savedAtWallMs = savedAtWallMs;
        }
    }
}
