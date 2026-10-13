package com.zcshou.gogogo;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

/**
 * Device-only regression for the real SharedPreferences persistence contract.
 * Does not touch the mock service. Restores prior selected target afterwards.
 */
@RunWith(AndroidJUnit4.class)
public class TianwaitianSelectedTargetInstrumentedTest {
    @Test public void choosingBeijingSurvivesAnotherScreenAndInvalidInput() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        SharedPreferences prefs = context.getSharedPreferences(
                "twt_selected_target_v1", Context.MODE_PRIVATE);
        boolean hasLon = prefs.contains("longitude");
        boolean hasLat = prefs.contains("latitude");
        boolean hasLabel = prefs.contains("label");
        boolean hasSeq = prefs.contains("sequence");
        String originalLon = prefs.getString("longitude", "");
        String originalLat = prefs.getString("latitude", "");
        String originalLabel = prefs.getString("label", "");
        long originalSequence = prefs.getLong("sequence", 0L);
        try {
            TianwaitianSelectedTarget.save(context, 116.39747, 39.90549,
                    "中国 · 北京（天安门广场）");
            TianwaitianSelectedTarget.Target restored =
                    TianwaitianSelectedTarget.read(context);
            assertNotNull(restored);
            assertEquals(116.39747, restored.longitude, 0.00000001);
            assertEquals(39.90549, restored.latitude, 0.00000001);
            assertTrue(restored.label.contains("北京"));
            long revision = restored.sequence;

            TianwaitianSelectedTarget.save(context, 98.50895, 16.68914,
                    "缅甸 · 妙瓦底（默认）");
            TianwaitianSelectedTarget.Target next =
                    TianwaitianSelectedTarget.read(context);
            assertEquals(98.50895, next.longitude, 0.00000001);
            assertTrue(next.sequence > revision);

            try {
                TianwaitianSelectedTarget.save(context, 116.3, 94.0, "错误坐标");
                fail("Invalid coordinates must be rejected");
            } catch (IllegalArgumentException expected) {
                // The previously selected position must remain unchanged.
            }
            assertEquals(98.50895, TianwaitianSelectedTarget.read(context).longitude,
                    0.00000001);
        } finally {
            // Restore only keys modified by this test: never clear user prefs.
            SharedPreferences.Editor editor = prefs.edit();
            if (hasLon) editor.putString("longitude", originalLon);
            else editor.remove("longitude");
            if (hasLat) editor.putString("latitude", originalLat);
            else editor.remove("latitude");
            if (hasLabel) editor.putString("label", originalLabel);
            else editor.remove("label");
            if (hasSeq) editor.putLong("sequence", originalSequence);
            else editor.remove("sequence");
            editor.commit();
        }
    }
}
