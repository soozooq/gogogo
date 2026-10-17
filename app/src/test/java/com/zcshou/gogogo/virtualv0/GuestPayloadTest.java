package com.zcshou.gogogo.virtualv0;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;

public class GuestPayloadTest {
    @Rule public TemporaryFolder folder = new TemporaryFolder();

    @Test public void separateTestSlotFiles() throws Exception {
        File a = folder.newFolder("A");
        File b = folder.newFolder("B");
        GuestPayload.run("A", a.getAbsolutePath(), "first");
        GuestPayload.run("B", b.getAbsolutePath(), "second");
        GuestPayload.run("A", a.getAbsolutePath(), "last");
        assertEquals("slot=A\nmarker=last\n",
                new String(Files.readAllBytes(new File(a, "slot.txt").toPath()),
                        StandardCharsets.UTF_8));
        assertEquals("slot=B\nmarker=second\n",
                new String(Files.readAllBytes(new File(b, "slot.txt").toPath()),
                        StandardCharsets.UTF_8));
    }

    @Test(expected = IllegalArgumentException.class)
    public void invalidSlotIsRejected() throws Exception {
        GuestPayload.run("C", folder.newFolder().getAbsolutePath(), "ok");
    }

    @Test(expected = IllegalArgumentException.class)
    public void invalidMarkerIsRejected() throws Exception {
        GuestPayload.run("A", folder.newFolder().getAbsolutePath(), "../bad");
    }
}
