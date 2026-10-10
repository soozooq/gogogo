package com.zcshou.gogogo;

import java.util.List;

/** Pure index hit testing for a vertically scrollable A-Z rail. */
public final class LabAlphabetRailTouch {
    private LabAlphabetRailTouch() {}

    /**
     * Translate screen-space finger position to a label. The rail may have
     * scrolled, so the current scrollY is part of the hit-test.
     *
     * Dragging slightly outside the viewport clamps to the nearest letter.
     */
    public static String letterAt(List<String> letters, float rawY, int screenTop,
                                  int scrollY, int cellHeightPx) {
        if (letters == null || letters.isEmpty() || !Float.isFinite(rawY)
                || cellHeightPx <= 0) return null;
        double inContent = (double) rawY - screenTop + scrollY;
        int index;
        if (inContent <= 0) index = 0;
        else {
            double ordinal = Math.floor(inContent / cellHeightPx);
            index = ordinal >= letters.size() ? letters.size() - 1 : (int) ordinal;
        }
        return letters.get(index);
    }

    /** Scroll gently near either edge while scrubbing, without adding buttons. */
    public static int edgeScrollDirection(float rawY, int screenTop,
                                          int viewportHeightPx, int edgePx) {
        if (!Float.isFinite(rawY) || viewportHeightPx <= 0 || edgePx <= 0) return 0;
        float local = rawY - screenTop;
        if (local < edgePx) return -1;
        if (local >= viewportHeightPx - edgePx) return 1;
        return 0;
    }
}
