package com.zcshou.gogogo.virtualv0;

/** Whitelist for primitive-only local application state; never a code/URI grant. */
public final class ContainerV32SessionPolicy {
    public static final int SCHEMA = 1;
    public static final int MAX_CLICKS = 100000;
    public static final int MAX_NOTE_LENGTH = 500;

    private ContainerV32SessionPolicy() {}

    public static boolean isValid(Object schema, Object clicks, Object note, Object page) {
        return schema instanceof Integer && ((Integer) schema) == SCHEMA
                && clicks instanceof Integer && ((Integer) clicks) >= 0
                && ((Integer) clicks) <= MAX_CLICKS
                && note instanceof String && ((String) note).length() <= MAX_NOTE_LENGTH
                && ("home".equals(page) || "details".equals(page));
    }
}
