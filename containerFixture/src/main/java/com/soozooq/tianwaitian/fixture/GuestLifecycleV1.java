package com.soozooq.tianwaitian.fixture;

/**
 * A self-authored guest lifecycle receiver INSIDE the separate fixture APK.
 * Not an Android Activity and does not request host Context or permissions.
 * The host proxy forwards real lifecycle callbacks to this trusted object.
 */
public final class GuestLifecycleV1 {
    private String state = "NEW";
    private int events;
    private int resumes;
    private final StringBuilder trace = new StringBuilder();

    public String onEvent(String event) {
        if (event == null) throw new IllegalArgumentException("null lifecycle event");
        switch (event) {
            case "onCreate":
                require("NEW"); state = "CREATED"; break;
            case "onStart":
                require("CREATED", "STOPPED"); state = "STARTED"; break;
            case "onResume":
                require("STARTED", "PAUSED"); state = "RESUMED"; resumes++; break;
            case "onPause":
                require("RESUMED"); state = "PAUSED"; break;
            case "onStop":
                require("PAUSED", "STARTED"); state = "STOPPED"; break;
            case "onDestroy":
                require("CREATED", "STARTED", "PAUSED", "STOPPED"); state = "DESTROYED"; break;
            case "onRestart":
                require("STOPPED"); state = "CREATED"; break;
            default:
                throw new IllegalArgumentException("unknown lifecycle event: " + event);
        }
        events++;
        String line = "#" + events + " " + event + " → " + state;
        if (events <= 48) trace.append(line).append('\n');
        return line;
    }

    public String snapshot() {
        return "guestState=" + state + "；events=" + events + "；resumes=" + resumes
                + "\n" + trace;
    }

    private void require(String... allowed) {
        for (String expected : allowed) {
            if (state.equals(expected)) return;
        }
        throw new IllegalStateException("event forbidden from " + state);
    }
}
