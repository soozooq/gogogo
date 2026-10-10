package com.zcshou.gogogo;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

public class LabAlphabetRailTouchTest {
    private final List<String> alphabet = Arrays.asList("A", "B", "C", "F", "M", "R", "Z");

    @Test public void topOfUnscrolledRailSelectsFirstLetter() {
        assertEquals("A", LabAlphabetRailTouch.letterAt(alphabet, 100, 100, 0, 24));
    }

    @Test public void tappingIndividualRowSelectsCorrectLetter() {
        assertEquals("C", LabAlphabetRailTouch.letterAt(alphabet, 160, 100, 0, 24));
        assertEquals("F", LabAlphabetRailTouch.letterAt(alphabet, 172, 100, 0, 24));
    }

    @Test public void fingerScrubChangesLettersAcrossRows() {
        assertEquals("A", LabAlphabetRailTouch.letterAt(alphabet, 100, 100, 0, 24));
        assertEquals("M", LabAlphabetRailTouch.letterAt(alphabet, 204, 100, 0, 24));
        assertEquals("Z", LabAlphabetRailTouch.letterAt(alphabet, 250, 100, 0, 24));
    }

    @Test public void scrollOffsetIsIncludedWhenScrubbing() {
        assertEquals("C", LabAlphabetRailTouch.letterAt(alphabet, 100, 100, 48, 24));
        assertEquals("R", LabAlphabetRailTouch.letterAt(alphabet, 105, 100, 120, 24));
    }

    @Test public void outsideViewportClampsToNearestAvailableLetter() {
        assertEquals("A", LabAlphabetRailTouch.letterAt(alphabet, -900, 100, 0, 24));
        assertEquals("Z", LabAlphabetRailTouch.letterAt(alphabet, 20000, 100, 0, 24));
    }

    @Test public void emptyOrInvalidFingerCannotSelectArbitraryLetter() {
        assertNull(LabAlphabetRailTouch.letterAt(Collections.emptyList(), 120, 100, 0, 24));
        assertNull(LabAlphabetRailTouch.letterAt(alphabet, Float.NaN, 100, 0, 24));
        assertNull(LabAlphabetRailTouch.letterAt(alphabet, Float.POSITIVE_INFINITY,
                100, 0, 24));
        assertNull(LabAlphabetRailTouch.letterAt(alphabet, 120, 100, 0, 0));
    }

    @Test public void edgeScrollingTriggersOnlyNearEdges() {
        assertEquals(-1, LabAlphabetRailTouch.edgeScrollDirection(100, 100, 300, 28));
        assertEquals(-1, LabAlphabetRailTouch.edgeScrollDirection(125, 100, 300, 28));
        assertEquals(0, LabAlphabetRailTouch.edgeScrollDirection(128, 100, 300, 28));
        assertEquals(0, LabAlphabetRailTouch.edgeScrollDirection(371, 100, 300, 28));
        assertEquals(1, LabAlphabetRailTouch.edgeScrollDirection(372, 100, 300, 28));
        assertEquals(1, LabAlphabetRailTouch.edgeScrollDirection(420, 100, 300, 28));
    }

    @Test public void invalidEdgeInputNeverAutoScrolls() {
        assertEquals(0, LabAlphabetRailTouch.edgeScrollDirection(Float.NaN, 100, 300, 28));
        assertEquals(0, LabAlphabetRailTouch.edgeScrollDirection(120, 100, 0, 28));
        assertEquals(0, LabAlphabetRailTouch.edgeScrollDirection(120, 100, 300, 0));
    }

    @Test public void letterIndexesRemainInBoundsWithHugeScrollOffsets() {
        assertEquals("Z", LabAlphabetRailTouch.letterAt(alphabet, 0, 100,
                Integer.MAX_VALUE, 24));
        assertEquals("A", LabAlphabetRailTouch.letterAt(alphabet, 0, 100,
                -Integer.MAX_VALUE, 24));
    }
}
