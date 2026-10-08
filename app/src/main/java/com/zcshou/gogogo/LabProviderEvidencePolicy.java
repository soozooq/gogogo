package com.zcshou.gogogo;

/** Pure, conservative interpretation of Lab 25 provider cleanup evidence. */
public final class LabProviderEvidencePolicy {
    private LabProviderEvidencePolicy() {}

    public enum CleanupOutcome {
        PENDING,
        REMOVE_RETURNED,
        ALREADY_ABSENT,
        SECURITY_DENIED,
        OTHER_FAILURE
    }

    /**
     * A returned removal call is API evidence, not proof that every location
     * consumer has forgotten the last fix.
     */
    public static boolean mayReportClean(CleanupOutcome outcome) {
        return outcome == CleanupOutcome.REMOVE_RETURNED
                || outcome == CleanupOutcome.ALREADY_ABSENT;
    }

    /**
     * Never reclaim an orphan from an AppOps callback while another provider
     * is actively publishing. It could race a live mock session.
     */
    public static boolean shouldRetryAfterAppOps(boolean allowed,
                                                 boolean anyActive,
                                                 boolean anyOrphaned) {
        return allowed && !anyActive && anyOrphaned;
    }

    public static boolean isUncertain(CleanupOutcome outcome) {
        return outcome == CleanupOutcome.PENDING
                || outcome == CleanupOutcome.SECURITY_DENIED
                || outcome == CleanupOutcome.OTHER_FAILURE;
    }
}
