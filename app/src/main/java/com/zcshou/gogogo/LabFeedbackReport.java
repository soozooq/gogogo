package com.zcshou.gogogo;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Current feedback plus a history index; never copies old experiment bodies. */
public final class LabFeedbackReport {
    public static final int MAX_CHARACTERS = 14_000;
    private static final String END = "END_GOGOGO_FEEDBACK_LOG_V2\n";
    private static final String TRUNCATED = "\n[FEEDBACK_TRUNCATED]\n";
    private static final Pattern TIMESTAMP = Pattern.compile(
            "20[0-9]{2}-[0-9]{2}-[0-9]{2}[T ][0-9]{2}:[0-9]{2}:[0-9]{2}"
                    + "(?:\\.[0-9]{1,3})?(?:[+-][0-9]{4})?");

    private LabFeedbackReport() {}

    public static String render(String metadata, String basics, String snapshot,
                                String serviceHistory, String historyIndex) {
        StringBuilder out = new StringBuilder();
        out.append("GOGOGO_FEEDBACK_LOG_V2\n")
                .append("scope=AT_EXPORT_NOT_DURING_EXTERNAL_APP; other_app_result=UNKNOWN\n")
                .append("no_experiments_started=true; historical_results_not_rerun=true\n")
                .append("说明：以下为复制时的本应用状态，不证明微信／抖音采用了模拟位置。\n")
                .append("旧实验只列存档索引，完整记录保留；无需重跑实验。\n")
                .append(metadata == null ? "METADATA_UNAVAILABLE\n" : metadata);
        section(out, "CURRENT_BASICS", basics);
        section(out, "CURRENT_CACHE_AND_NETWORK_AT_EXPORT", snapshot);
        section(out, "SERVICE_EVENT_HISTORY_NOT_LIVE_STATUS", serviceHistory);
        section(out, "SAVED_HISTORY_INDEX_NOT_NEW_RESULTS", historyIndex);
        if (out.length() + END.length() > MAX_CHARACTERS) {
            int keep = MAX_CHARACTERS - END.length() - TRUNCATED.length();
            if (Character.isHighSurrogate(out.charAt(keep - 1))) keep--;
            out.setLength(keep);
            out.append(TRUNCATED);
        }
        return out.append(END).toString();
    }

    private static void section(StringBuilder out, String name, String body) {
        out.append("\n--- ").append(name).append(" ---\n")
                .append(body == null || body.isEmpty() ? "UNAVAILABLE" : body).append('\n');
    }

    /** Dates are merely text found in an existing record, never a freshness claim. */
    public static String historyIndex(String name, String record) {
        String prefix = name + ": ";
        if (record == null || record.trim().isEmpty() || record.trim().equals("NO_RECORD")) {
            return prefix + "saved=NO\n";
        }
        if (record.startsWith("READ_ERROR_") || record.startsWith("LOG_TOO_LARGE")) {
            return prefix + "saved=UNKNOWN; read=UNAVAILABLE\n";
        }
        // Inspect a bounded tail without copying notes, coordinates, SDK text or old advice.
        String tail = record.substring(Math.max(0, record.length() - 125_000));
        Matcher dates = TIMESTAMP.matcher(tail);
        String last = "UNKNOWN";
        while (dates.find()) last = dates.group();
        return prefix + "saved=YES; stored_chars=" + record.length()
                + "; last_timestamp_text=" + last + "; time_scope=STORED_TEXT_ONLY\n";
    }
}
