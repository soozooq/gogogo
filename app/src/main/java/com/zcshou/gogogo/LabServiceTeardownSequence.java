package com.zcshou.gogogo;

/**
 * Runs best-effort ServiceGo shutdown steps independently.
 * An unexpected RuntimeException from one cleanup stage must never suppress
 * later cleanup stages. This does not certify that providers were removed.
 */
public final class LabServiceTeardownSequence {
    private LabServiceTeardownSequence() {}

    public static int run(Runnable... steps) {
        if (steps == null) return 0;
        int failures = 0;
        for (Runnable step : steps) {
            if (step == null) continue;
            try {
                step.run();
            } catch (RuntimeException exception) {
                failures++;
            }
        }
        return failures;
    }
}
