package com.soozooq.tianwaitian.externaldemo;

/** State belonging to one demo entry instance, with no static shared session. */
public final class DemoState {
    public static final int STATE_SCHEMA = 1;
    public static final int MAX_CLICKS = 100000;
    public static final int MAX_NOTE_LENGTH = 500;
    public static final String PAGE_HOME = "home";
    public static final String PAGE_DETAILS = "details";
    private final StringBuilder trace = new StringBuilder();
    private int clicks;
    private int interactionCount;
    private String note = "";
    private String page = PAGE_HOME;
    private String lifecycle = "NEW";
    private boolean disposed;
    private boolean restored;

    /** Primitive-only snapshot; deliberately contains no Android objects or code paths. */
    public static final class SavedState {
        public final int schema;
        public final int clicks;
        public final String note;
        public final String page;

        public SavedState(int schema, int clicks, String note, String page) {
            if (schema != STATE_SCHEMA || clicks < 0 || clicks > MAX_CLICKS
                    || note == null || note.length() > MAX_NOTE_LENGTH
                    || !(PAGE_HOME.equals(page) || PAGE_DETAILS.equals(page))) {
                throw new IllegalArgumentException("invalid demo saved state");
            }
            this.schema = schema;
            this.clicks = clicks;
            this.note = note;
            this.page = page;
        }
    }

    public DemoState(int restoredClicks) {
        if (restoredClicks < 0 || restoredClicks > MAX_CLICKS) {
            throw new IllegalArgumentException("invalid restored click count");
        }
        clicks = restoredClicks;
    }

    public int click() {
        requireAlive();
        recordInteraction();
        if (clicks < MAX_CLICKS) clicks++;
        append("CLICK " + clicks);
        return clicks;
    }

    /** Events are forwarded by the actual host; this object starts no Activity. */
    public String onLifecycle(String event) {
        requireAlive();
        boolean allowed;
        if ("onCreate".equals(event)) allowed = "NEW".equals(lifecycle);
        else if ("onStart".equals(event)) {
            allowed = "onCreate".equals(lifecycle) || "onStop".equals(lifecycle);
        } else if ("onRestart".equals(event)) {
            allowed = "onStop".equals(lifecycle);
            if (allowed) {
                append("LIFECYCLE " + event);
                return event + " clicks=" + clicks;
            }
        } else if ("onResume".equals(event)) {
            allowed = "onStart".equals(lifecycle) || "onPause".equals(lifecycle);
        } else if ("onPause".equals(event)) allowed = "onResume".equals(lifecycle);
        else if ("onStop".equals(event)) {
            allowed = "onPause".equals(lifecycle) || "onStart".equals(lifecycle);
        } else if ("onDestroy".equals(event)) {
            allowed = !"NEW".equals(lifecycle) && !"onDestroy".equals(lifecycle);
        } else if ("onSaveInstanceState".equals(event)) {
            allowed = !"NEW".equals(lifecycle) && !"onDestroy".equals(lifecycle);
            if (allowed) {
                append("LIFECYCLE " + event);
                return event + " clicks=" + clicks;
            }
        } else allowed = false;
        if (!allowed) {
            throw new IllegalStateException("invalid lifecycle " + lifecycle + " -> " + event);
        }
        lifecycle = event;
        append("LIFECYCLE " + event);
        return event + " clicks=" + clicks;
    }

    public int getClickCount() { return clicks; }
    public int getInteractionCount() { return interactionCount; }
    public String getNote() { return note; }
    public String getPage() { return page; }
    public boolean isDisposed() { return disposed; }

    public void setNote(String value) {
        requireAlive();
        if (value == null || value.length() > MAX_NOTE_LENGTH) {
            throw new IllegalArgumentException("invalid note length");
        }
        if (!note.equals(value)) {
            note = value;
            recordInteraction();
        }
    }

    public void openDetails() {
        requireAlive();
        if (!PAGE_DETAILS.equals(page)) {
            page = PAGE_DETAILS;
            recordInteraction();
            append("PAGE details");
        }
    }

    public boolean handleBack() {
        requireAlive();
        if (PAGE_HOME.equals(page)) return false;
        page = PAGE_HOME;
        recordInteraction();
        append("PAGE home");
        return true;
    }

    public SavedState save() {
        return new SavedState(STATE_SCHEMA, clicks, note, page);
    }

    public void restore(SavedState saved) {
        requireAlive();
        if (!"NEW".equals(lifecycle) || saved == null) {
            throw new IllegalStateException("restore must precede onCreate");
        }
        // SavedState validates all fields before any live state is changed.
        clicks = saved.clicks;
        note = saved.note;
        page = saved.page;
        // A restored count, note or page is historical data, never proof of a
        // real operation in the current entry instance.
        interactionCount = 0;
        restored = true;
        append("RESTORED schema=" + saved.schema + " clicks=" + clicks
                + " noteLength=" + note.length() + " page=" + page);
    }

    public void dispose() {
        if (disposed) return;
        disposed = true;
        append("DISPOSE");
    }

    public String snapshot() {
        return "clicks=" + clicks + "; lifecycle=" + lifecycle
                + "; interactions=" + interactionCount
                + "; stateSchema=" + STATE_SCHEMA + "; restored=" + restored
                + "; noteLength=" + note.length() + "; page=" + page
                + "; disposed=" + disposed + "\n" + trace;
    }

    private void requireAlive() {
        if (disposed || "onDestroy".equals(lifecycle)) {
            throw new IllegalStateException("demo session already destroyed/disposed");
        }
    }

    private void recordInteraction() {
        if (interactionCount < Integer.MAX_VALUE) interactionCount++;
    }

    private void append(String event) {
        if (trace.length() < 4096) trace.append(event).append('\n');
    }
}
