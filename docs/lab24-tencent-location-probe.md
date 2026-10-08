# Lab 24 — Tencent Location Consumer Probe (independent process)

> This is a **diagnostic consumer**, not a spoofing / WeChat hooking feature. It runs in GoGoGo's existing `:consumer` process. No other app's process or data is read or altered.

## What is new

- Uses published Maven dependency `com.tencent.map.geolocation:TencentLocationSdk-openplatform:7.6.1.12`. SDK version is pinned for reproducibility.
- UI: 「地图实验室 → 实验 → Tencent Location Probe (Lab 24)」.
- Tencent LBS SDK `requestSingleFreshLocation` vs `requestLocationUpdates`.
- REQUEST_LEVEL_GEO vs REQUEST_LEVEL_POI; `setAllowCache(true/false)`.
- Displays Tencent SDK `onLocationChanged` error code, reason, response latency, GCJ-02 coordinates, accuracy and age. Does **not** treat request return-code alone as a valid location.
- In POI mode, displays TencentPoi name/coordinates/`getDistance()` and locally computed great-circle distance, both in meters. Local distance is an independent *comparison*, not evidence of the SDK's original input.
- Shows LocationManager GPS, NETWORK, framework fused and GMS FLP last-known location alongside SDK results. Android coordinates shown as WGS84; Tencent as GCJ-02. Their raw numeric values must **not** be compared directly at meter precision without coordinate conversion.
- Labels `(0,0)` as `ZERO_PAIR_SUSPECT` instead of claiming invalid (it is physically a valid coordinate, but often a sentinel). Handles null, invalid coordinates, stale age, future time, missing location permissions.
- Removes Tencent request callbacks on stop/destroy. No polling in background.
- Request callbacks now carry immutable request-generation tokens and capture GEO/POI mode on launch; cancelled, timed-out, superseded or completed single-request callbacks are ignored, rather than being attributed to the next request.
- Nonzero SDK registration returns are classified immediately as request rejection, not as valid location and not as an eventual timeout.
- Android/GMS refresh callbacks are also generation-scoped, so a delayed GMS result cannot overwrite a newer snapshot.
- Explicit local fine/coarse permission guards protect standard location calls and resolve Android Lint MissingPermission errors without disabling lint.
- Runtime privacy opt-in dialog is shown before SDK request. Location data may be transmitted to Tencent as part of SDK operation.
- Never changes the device's Mock Location setting, never hides isMock, never reads WeChat internal SDK state.

## Key and privacy: important

The build workflow uses `TENCENT_MAP_KEY=DUMMY` as before. **The public CI APK can compile and display Android/GMS**, but will show `SDK_UNAVAILABLE` for Tencent until the developer configures a valid Tencent LBS Key bound to their **own Android application ID and signing SHA-1**.

A real key should be configured by the app owner in a **controlled/private build environment** as `TENCENT_MAP_KEY` in `local.properties`, per the existing manifest placeholder. Do **not** post it in chats, add it to Git or upload key-bearing APKs in public Actions artifacts. We do not ship somebody else's key, and do not claim Tencent returned any fix when Key is unavailable.

Android SDK / GMS may show Mock Location flags in diagnostics. These are not suppressed.

## Device test

1. Keep GoGoGo publishing the public test point (e.g. Beijing Tiananmen) as in the previous investigation.
2. Open Lab 24; refresh Android/GMS. If using a public CI build, it should explicitly say Tencent SDK key is not configured.
3. On a private build with a valid key, consent to Tencent SDK privacy processing and request GEO/no-cache single fresh, then GEO/cache allowed, then POI/no-cache. Save error code/reason and return coordinates and freshness, including failures.
4. Compare with Consumer Matrix and observe WeChat UI externally. WeChat's internal query path and data are **unknown**; do not interpret this as observing WeChat.
5. Avoid sharing the manifest key, true location, Wi-Fi BSSIDs, SIM information, or Tencent SDK raw diagnostic logs containing private addresses in public issue reports.

## Testing

- JUnit `TencentProbeMathTest`: zero-distance, Beijing/Fujian order-of-magnitude distance, zero pair detection, invalid coordinates, freshness flags.
- JUnit `TencentProbeSessionStateTest`: active token, cancellation, one-shot completion and stale completion (4 cases). Lab 23 lifecycle regressions were also integrated into Lab 24.
- Gradle `build` and `assembleDebug` via GitHub Actions on `feat/gogogo-tencent-probe-lab24`.
- On-device Tencent SDK execution requires real valid Key and consent; CI success alone does not establish SDK compatibility with the phone or WeChat.

Sources:
- https://github.com/TencentLBS/tencent-mapsdk-samples-for-android
- https://central.sonatype.com/artifact/com.tencent.map.geolocation/TencentLocationSdk-openplatform/7.6.1.12
- https://github.com/tencentlocation/TencentLocationDemo
- docs/research/2026-10-08-wechat-location-compatibility-round2.md (separate research branch)

## CI maintenance

Lab 24 Build Check initially failed at `TencentLocationProbeActivity` with Android Lint `MissingPermission` for framework/GMS last-location reads. Local permission checking has now been made explicit; await the new full CI result before calling this resolved. The two push workflows now cancel superseded commits on the same branch instead of queueing every intermediate iteration.
