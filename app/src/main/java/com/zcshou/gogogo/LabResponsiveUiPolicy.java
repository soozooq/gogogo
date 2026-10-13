package com.zcshou.gogogo;

/** Pure sizing decision used by code-driven home controls. */
public final class LabResponsiveUiPolicy {
    private LabResponsiveUiPolicy() {}

    /**
     * Two weighted buttons stop being reliably readable on narrow phones
     * or with large user font scaling. Prefer full-width stacked actions.
     */
    public static boolean stackHomeActions(int screenWidthDp, float fontScale) {
        return screenWidthDp <= 360 || fontScale >= 1.30f;
    }

    /** Preserve a 108dp right-hand map-control rail and 16dp left gutter. */
    public static int tianwaitianHomeCardWidthDp(int screenWidthDp) {
        return Math.min(356, Math.max(120, screenWidthDp - 124));
    }

    /** A landscape/short phone must expose the map instead of filling it with cards. */
    public static boolean tianwaitianHomeDetailsDefaultExpanded(int screenHeightDp) {
        return screenHeightDp >= 600;
    }

    /**
     * Maximum expanded details height. Keep a meaningful portion of a short
     * screen free for the Tencent map and original floating mock button.
     * The caller only clamps when content actually exceeds this limit.
     */
    public static int tianwaitianHomeDetailsMaxHeightDp(int screenHeightDp) {
        return Math.max(100, Math.min(300, screenHeightDp - 260));
    }

    /** Remove nonessential explanatory blocks on landscape/short picker screens. */
    public static boolean tianwaitianCompactPlacePicker(int screenHeightDp) {
        return screenHeightDp < 600;
    }

    /** Stack secondary controls when a clipped label or large font is likely. */
    public static boolean stackTianwaitianHomeActions(int cardWidthDp, float fontScale) {
        return cardWidthDp < 230 || fontScale >= 1.30f;
    }
}
