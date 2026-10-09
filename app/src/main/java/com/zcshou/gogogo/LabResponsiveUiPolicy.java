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
}
