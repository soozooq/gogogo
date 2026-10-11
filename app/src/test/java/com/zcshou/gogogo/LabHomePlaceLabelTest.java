package com.zcshou.gogogo;

import org.junit.Test;
import static org.junit.Assert.*;

public class LabHomePlaceLabelTest {
    @Test public void defaultCountryIsMyanmar() {
        String label = LabHomePlaceLabel.forCoordinates("98.50895", "16.68914");
        assertTrue(label.contains("缅甸"));
        assertTrue(label.contains("妙瓦底"));
        assertTrue(label.contains("离线预设"));
    }

    @Test public void selectsBeijingWithoutSwitchingLatitudeAndLongitude() {
        String label = LabHomePlaceLabel.forCoordinates("116.39747", "39.90549");
        assertTrue(label.contains("中国"));
        assertTrue(label.contains("北京"));
    }

    @Test public void customPointMustNotInheritNearestPresetsCountry() {
        assertTrue(LabHomePlaceLabel.forCoordinates("116.40000", "39.90549")
                .contains("国家待识别"));
    }

    @Test public void invalidCoordinateDoesNotPretendCountryKnown() {
        assertTrue(LabHomePlaceLabel.forCoordinates("NaN", "39.9")
                .contains("尚未有效"));
        assertTrue(LabHomePlaceLabel.forCoordinates("200", "39.9")
                .contains("尚未有效"));
    }
}
