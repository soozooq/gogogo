package com.zcshou.gogogo;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Pure, testable input classifier. Input coordinates use longitude,latitude (WGS-84). */
public final class TianwaitianSearchQuery {
    private static final Pattern PAIR = Pattern.compile(
            "^\\s*([+-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+))\\s*[,，;；\\s]+\\s*"
                    + "([+-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+))\\s*$");
    private static final Pattern LABELED = Pattern.compile(
            "^\\s*经度\\s*[:：=]?\\s*([+-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+))"
                    + "\\s*[,，;；\\s]+\\s*纬度\\s*[:：=]?\\s*"
                    + "([+-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+))\\s*$");

    private TianwaitianSearchQuery() {}

    /** Does the text resemble coordinates rather than a geocoding query? */
    public static boolean looksLikeCoordinates(String input) {
        if (input == null) return false;
        String text = input.trim();
        return PAIR.matcher(text).matches() || LABELED.matcher(text).matches();
    }

    /**
     * Returns a parsed WGS-84 point when the input looks like coordinates,
     * or null for ordinary addresses. Throws on out-of-range coordinates.
     */
    public static LabHomeCoordinates.Point coordinatesOrNull(String input) {
        if (input == null) return null;
        Matcher matcher = PAIR.matcher(input);
        if (!matcher.matches()) matcher = LABELED.matcher(input);
        if (!matcher.matches()) return null;
        return LabHomeCoordinates.parse(matcher.group(1), matcher.group(2));
    }

    /** Avoid sending extremely long/empty requests to a shared public geocoder. */
    public static boolean validOnlineQuery(String input) {
        if (input == null) return false;
        int count = input.trim().codePointCount(0, input.trim().length());
        return count >= 2 && count <= 120 && !looksLikeCoordinates(input);
    }
}
