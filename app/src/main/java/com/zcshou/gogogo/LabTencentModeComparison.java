package com.zcshou.gogogo;

/**
 * Local, bounded, coordinate-free comparison of four Tencent SDK SINGLE
 * request configurations. This records observations, not SDK cache internals.
 *
 * Do not use the result to infer Tencent / WeChat's internal location source.
 * Only a real Tencent SDK callback can move a request into FIX_* state.
 */
public final class LabTencentModeComparison {
    public enum State {
        NOT_RUN, REQUESTING, ACCEPTED, REJECTED, FIX_VALID,
        FIX_SUSPECT, EMPTY, ERROR, TIMEOUT
    }

    private final Observation[] slots = new Observation[4];

    public LabTencentModeComparison() {
        clear();
    }

    public synchronized void clear() {
        for (int i = 0; i < slots.length; i++) {
            slots[i] = new Observation();
        }
    }

    private static int index(boolean poi, boolean allowCache) {
        return (poi ? 2 : 0) + (allowCache ? 1 : 0);
    }

    /** The request has begun, but SDK has not accepted it yet. */
    public synchronized void begin(boolean poi, boolean allowCache) {
        Observation o = slots[index(poi, allowCache)] = new Observation();
        o.state = State.REQUESTING;
    }

    /** SDK returned an acceptance code, not a location fix. */
    public synchronized void requestReturn(boolean poi, boolean allowCache, int code) {
        Observation o = slots[index(poi, allowCache)];
        o.sdkCode = code;
        o.state = code == 0 ? State.ACCEPTED : State.REJECTED;
    }

    public synchronized void callbackError(boolean poi, boolean allowCache, int code,
                                           long latencyMs) {
        Observation o = slots[index(poi, allowCache)];
        o.state = State.ERROR;
        o.sdkCode = code;
        o.latencyMs = safeLatency(latencyMs);
    }

    public synchronized void callbackEmpty(boolean poi, boolean allowCache,
                                           long latencyMs) {
        Observation o = slots[index(poi, allowCache)];
        o.state = State.EMPTY;
        o.latencyMs = safeLatency(latencyMs);
    }

    /**
     * Wall clock age is only a tentative estimate. -1 means UNKNOWN. The
     * caller must validate coordinates independently; raw values are discarded.
     */
    public synchronized void callbackFix(boolean poi, boolean allowCache,
                                         boolean valid, boolean zeroPair,
                                         long wallAgeMs, long latencyMs,
                                         int poiCount) {
        Observation o = slots[index(poi, allowCache)];
        o.state = valid && !zeroPair ? State.FIX_VALID : State.FIX_SUSPECT;
        o.ageClass = ageClass(wallAgeMs);
        o.latencyMs = safeLatency(latencyMs);
        o.poiCount = poi ? (poiCount < 0 ? -1 : Math.min(999, poiCount)) : -2;
    }

    public synchronized void timeout(boolean poi, boolean allowCache) {
        Observation o = slots[index(poi, allowCache)];
        if (o.state == State.ACCEPTED || o.state == State.REQUESTING) {
            o.state = State.TIMEOUT;
        }
    }

    public synchronized State getState(boolean poi, boolean allowCache) {
        return slots[index(poi, allowCache)].state;
    }

    public synchronized String render(boolean sdkKeyConfigured) {
        StringBuilder out = new StringBuilder();
        out.append("Lab 51 · 腾讯单次定位四组对照\n");
        out.append("请求级别 / 是否允许缓存 | SDK 结果 | 响应耗时 | 样本年龄 | POI\n");
        String[] labels = {"GEO / OFF", "GEO / ON", "POI / OFF", "POI / ON"};
        for (int i = 0; i < slots.length; i++) {
            Observation o = slots[i];
            out.append(labels[i]).append(" | ").append(o.state.name())
                    .append(" | ").append(o.latencyMs < 0L ? "UNKNOWN" : o.latencyMs + "ms")
                    .append(" | ").append(o.ageClass)
                    .append(" | ").append(o.poiCount == -2 ? "NOT_REQUESTED"
                            : o.poiCount < 0 ? "UNKNOWN" : Integer.toString(o.poiCount))
                    .append('\n');
        }
        out.append("SDK_KEY=").append(sdkKeyConfigured
                ? "PRESENT_NOT_VALIDATED" : "UNAVAILABLE").append('\n');
        out.append("注意：这是本应用单次请求的最后一次观测；allowCache 是请求偏好，"
                + "非缓存命中证明；墙上时间年龄是估算，不能与 Android 单调时钟直接比较。"
                + "GEO/POI 请求及 SDK 返回的地点详情可能不同，"
                + "结果不能证明微信采用相同定位。");
        return out.toString();
    }

    private static String ageClass(long wallAgeMs) {
        if (wallAgeMs < 0L) return "WALL_UNKNOWN";
        if (wallAgeMs <= 30000L) return "WALL_EST_LE_30S";
        return "WALL_EST_GT_30S";
    }

    private static long safeLatency(long latencyMs) {
        return latencyMs < 0L ? -1L : latencyMs;
    }

    private static final class Observation {
        State state = State.NOT_RUN;
        long latencyMs = -1L;
        int sdkCode = Integer.MIN_VALUE;
        int poiCount = -1;
        String ageClass = "WALL_UNKNOWN";
    }
}
