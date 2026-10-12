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

    @Test public void vpnKnownExitMatchesExactCoordinateInScreenshot() {
        String known = LabHomePlaceLabel.vpnLabelForCoordinates(
                "-121.8162185", "38.0177934",
                -121.8162185, 38.0177934, "United States", "Antioch-Pittsburg");
        assertNotNull(known);
        assertTrue(known.contains("美国"));
        assertTrue(known.contains("Antioch-Pittsburg"));
        assertTrue(known.contains("VPN 出口 IP 估算"));
        assertFalse(known.contains("系统无法识别"));
    }

    @Test public void vpnExitNeverAppliesToOtherManualCoordinates() {
        assertNull(LabHomePlaceLabel.vpnLabelForCoordinates(
                "-121.800000", "38.0177934",
                -121.8162185, 38.0177934, "United States", "Antioch-Pittsburg"));
        assertNull(LabHomePlaceLabel.vpnLabelForCoordinates(
                "98.50895", "16.68914",
                -121.8162185, 38.0177934, "United States", "Antioch-Pittsburg"));
        assertNull(LabHomePlaceLabel.vpnLabelForCoordinates(
                "NaN", "38.0177934",
                -121.8162185, 38.0177934, "United States", "Antioch-Pittsburg"));
        assertNull(LabHomePlaceLabel.vpnLabelForCoordinates(
                "-121.8162185", "38.0177934",
                -121.8162185, 38.0177934, "", "Antioch-Pittsburg"));
    }

    @Test public void unknownCountryCodeMustNotBeInvented() {
        String label = LabHomePlaceLabel.vpnLabelForCoordinates(
                "1.22", "3.44", 1.22, 3.44, "Some Unknown Country", "City");
        assertNotNull(label);
        assertTrue(label.contains("Some Unknown Country"));
        assertTrue(label.contains("VPN 出口 IP 估算"));
    }

    @Test public void invalidCoordinateDoesNotPretendCountryKnown() {
        assertTrue(LabHomePlaceLabel.forCoordinates("NaN", "39.9")
                .contains("尚未有效"));
        assertTrue(LabHomePlaceLabel.forCoordinates("200", "39.9")
                .contains("尚未有效"));
    }
}
