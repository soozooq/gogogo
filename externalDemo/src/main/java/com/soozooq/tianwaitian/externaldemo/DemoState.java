package com.soozooq.tianwaitian.externaldemo;

/** State belonging to one demo entry instance, with no static shared session. */
public final class DemoState {
    public static final int MAX_CLICKS = 100000;
    private final StringBuilder trace = new StringBuilder();
    private int clicks;
    private String lifecycle = "NEW";
    private boolean disposed;

    public DemoState(int restoredClicks) {
        if (restoredClicks < 0 || restoredClicks > MAX_CLICKS) {
            throw new IllegalArgumentException("invalid restored click count");
        }
        clicks = restoredClicks;
    }

    public int click() {
        requireAlive();
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
    public boolean isDisposed() { return disposed; }

    public void dispose() {
        if (disposed) return;
        disposed = true;
        append("DISPOSE");
    }

    public String snapshot() {
        return "clicks=" + clicks + "; lifecycle=" + lifecycle
                + "; disposed=" + disposed + "\n" + trace;
    }

    private void requireAlive() {
        if (disposed || "onDestroy".equals(lifecycle)) {
            throw new IllegalStateException("demo session already destroyed/disposed");
        }
    }

    private void append(String event) {
        if (trace.length() < 4096) trace.append(event).append('\n');
    }
}
