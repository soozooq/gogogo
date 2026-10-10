package com.zcshou.gogogo;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class LabMapSelectionRestoreTest {
    @Test public void freshlyOpenedMapUsesLaunchPoint() {
        LabHomeCoordinates.Point p = LabMapSelectionRestore.choose(
                98.50895, 16.68914, false, Double.NaN, Double.NaN);
        assertEquals(98.50895, p.longitude, 0.0);
        assertEquals(16.68914, p.latitude, 0.0);
    }

    @Test public void rotatedMapPrefersUserSelectionOverLaunchPoint() {
        LabHomeCoordinates.Point p = LabMapSelectionRestore.choose(
                98.50895, 16.68914, true, 116.3974, 39.9093);
        assertEquals(116.3974, p.longitude, 0.0);
        assertEquals(39.9093, p.latitude, 0.0);
    }

    @Test public void savedZeroZeroIsARealLocation() {
        LabHomeCoordinates.Point p = LabMapSelectionRestore.choose(
                98.50895, 16.68914, true, 0.0, 0.0);
        assertEquals(0.0, p.longitude, 0.0);
        assertEquals(0.0, p.latitude, 0.0);
    }

    @Test public void corruptNaNDoesNotOverwriteValidLaunchPoint() {
        LabHomeCoordinates.Point p = LabMapSelectionRestore.choose(
                98.50895, 16.68914, true, Double.NaN, 22.0);
        assertEquals(98.50895, p.longitude, 0.0);
        assertEquals(16.68914, p.latitude, 0.0);
    }

    @Test public void savedOutOfRangeDoesNotOverwriteLaunchPoint() {
        LabHomeCoordinates.Point p = LabMapSelectionRestore.choose(
                98.50895, 16.68914, true, 200.0, 10.0);
        assertEquals(98.50895, p.longitude, 0.0);
        assertEquals(16.68914, p.latitude, 0.0);
    }

    @Test public void validSavedBoundaryCoordinatesRestored() {
        LabHomeCoordinates.Point p = LabMapSelectionRestore.choose(
                98.50895, 16.68914, true, -180.0, 90.0);
        assertEquals(-180.0, p.longitude, 0.0);
        assertEquals(90.0, p.latitude, 0.0);
    }
}
