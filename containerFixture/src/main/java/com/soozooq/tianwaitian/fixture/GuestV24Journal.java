package com.soozooq.tianwaitian.fixture;

/**
 * Pure Java, fixed-width, test-only crash recovery journal protocol in the
 * INDEPENDENT fixture APK. Caller persists strings with Android AtomicFile.
 * Demonstrates cancellation of an old synthetic request and idempotent
 * completion of a new synthetic request, NOT general transactional exactly-once.
 */
public final class GuestV24Journal {
    private static final String VERSION = "TWT_V24_1";

    private GuestV24Journal() {}

    public static String prepare(String oldToken, String sha256, int oldPid) {
        checkToken(oldToken);
        checkSha(sha256);
        if (oldPid <= 0) throw new IllegalArgumentException("invalid worker PID");
        return VERSION + "|PENDING|" + oldToken + "|" + sha256 + "|"
                + oldPid + "|0|0|0|NONE";
    }

    public static String recover(String journal, String oldToken, String newToken,
            String sha256, int currentPid) {
        String[] f = decode(journal);
        checkToken(oldToken);
        checkToken(newToken);
        checkSha(sha256);
        if (oldToken.equals(newToken) || currentPid <= 0) {
            throw new IllegalArgumentException("invalid new worker identity");
        }
        if (!oldToken.equals(f[2]) || !sha256.equals(f[3])
                || currentPid == Integer.parseInt(f[4])) {
            throw new IllegalStateException("old token/hash or worker PID mismatch");
        }
        if ("RECOVERED".equals(f[1])) {
            // Duplicate recovery must preserve the already committed record.
            if (newToken.equals(f[7]) && Integer.parseInt(f[7]) == currentPid) {
                return journal;
            }
            throw new IllegalStateException("stale or foreign recovery is rejected");
        }
        if (!"PENDING".equals(f[1]) || !"0".equals(f[5]) || !"0".equals(f[6]) || !"0".equals(f[7])
                || !"NONE".equals(f[7])) {
            throw new IllegalStateException("unexpected pre-crash journal state");
        }
        // Old pending synthetic request is ABORTED, not replayed. A NEW request
        // is executed exactly once as a fixture-only deterministic calculation.
        String output = GuestV2Route.handle(GuestV2Route.ACTION, "NEW_V24");
        if (!"GUEST_V2_RESULT:42V_WEN".equals(output)) {
            throw new IllegalStateException("trusted guest route returned unexpected value");
        }
        return VERSION + "|RECOVERED|" + oldToken + "|" + sha256 + "|"
                + f[4] + "|0|1|" + currentPid + "|" + newToken;
    }

    public static boolean isPending(String encoded, String token, String sha, int pid) {
        try {
            String[] f = decode(encoded);
            return "PENDING".equals(f[1]) && f[2].equals(token)
                    && f[3].equals(sha) && Integer.parseInt(f[4]) == pid;
        } catch (RuntimeException failure) {
            return false;
        }
    }

    public static boolean isRecovered(String encoded, String oldToken, String newToken,
            String sha, int oldPid, int newPid) {
        try {
            String[] f = decode(encoded);
            return "RECOVERED".equals(f[1])
                    && f[2].equals(oldToken) && f[3].equals(sha)
                    && Integer.parseInt(f[4]) == oldPid
                    && Integer.parseInt(f[5]) == 0
                    && Integer.parseInt(f[6]) == 1
                    && Integer.parseInt(f[7]) == newPid
                    && f[8].equals(newToken) && oldPid != newPid;
        } catch (RuntimeException failure) {
            return false;
        }
    }

    /** This is a synthetic old-result rejection, not a real network callback. */
    public static boolean acceptOldResult(String encoded, String oldToken) {
        String[] f = decode(encoded);
        checkToken(oldToken);
        return "PENDING".equals(f[1]) && oldToken.equals(f[2]);
    }

    public static String state(String journal) {
        return decode(journal)[1];
    }

    private static String[] decode(String journal) {
        if (journal == null || journal.length() > 300) {
            throw new IllegalArgumentException("journal size invalid");
        }
        String[] f = journal.split("\\|", -1);
        if (f.length != 9 || !VERSION.equals(f[0])
                || !("PENDING".equals(f[1]) || "RECOVERED".equals(f[1]))) {
            throw new IllegalArgumentException("invalid or unknown journal version");
        }
        checkToken(f[2]);
        checkSha(f[3]);
        try {
            if (Integer.parseInt(f[4]) <= 0) throw new IllegalArgumentException("old PID");
            int newPid = Integer.parseInt(f[7]);
            if ("PENDING".equals(f[1]) && newPid != 0) {
                throw new IllegalArgumentException("pending new PID invalid");
            }
            if ("RECOVERED".equals(f[1]) && newPid <= 0) {
                throw new IllegalArgumentException("recovered PID invalid");
            }
        } catch (NumberFormatException failure) {
            throw new IllegalArgumentException("malformed PID", failure);
        }
        if (!("PENDING".equals(f[1]) ? "0".equals(f[6]) : "1".equals(f[6]))) {
            throw new IllegalArgumentException("new completion count incorrect");
        }
        if (!"0".equals(f[5])) throw new IllegalArgumentException("old completions must be zero");
        if ("PENDING".equals(f[1]) ? !"NONE".equals(f[8]) : !validToken(f[8])) {
            throw new IllegalArgumentException("invalid new token");
        }
        return f;
    }

    private static void checkToken(String token) {
        if (!validToken(token)) throw new IllegalArgumentException("invalid token");
    }

    private static boolean validToken(String value) {
        return value != null && value.matches("[a-f0-9-]{36}");
    }

    private static void checkSha(String value) {
        if (value == null || !value.matches("[a-f0-9]{64}")) {
            throw new IllegalArgumentException("invalid APK digest");
        }
    }
}
