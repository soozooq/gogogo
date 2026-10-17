package com.zcshou.gogogo.virtualv0;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/** Only trusted code included in Tianwaitian's own APK is used in V0. */
public final class GuestPayload {
    private GuestPayload() {}

    public static String run(String slot, String directory, String marker) throws IOException {
        if (!"A".equals(slot) && !"B".equals(slot)) throw new IllegalArgumentException("slot");
        if (marker == null || !marker.matches("[a-zA-Z0-9_-]{1,80}")) {
            throw new IllegalArgumentException("marker");
        }
        File root = new File(directory).getCanonicalFile();
        if (!root.isDirectory()) throw new IOException("sandbox missing");
        String record = "slot=" + slot + "\nmarker=" + marker + "\n";
        Files.write(new File(root, "slot.txt").toPath(), record.getBytes(StandardCharsets.UTF_8));
        return record;
    }
}
