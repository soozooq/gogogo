# GoGoGo Open-Source Location Research Notes

Last updated: 2026-10-08

## Purpose

This document records public open-source ideas that can improve GoGoGo as a legitimate Android location simulation, QA, diagnostics, and reliability tool.

Scope:
- mock-location testing through Android-supported APIs
- route simulation and replay
- provider lifecycle reliability
- process-death recovery
- QA scenario automation
- GNSS/location diagnostics
- background survival diagnostics

Out of scope:
- hiding or falsifying Android's mock-location status
- bypassing app integrity / anti-abuse / anti-fraud controls
- injecting or modifying third-party app processes

## High-value findings

### 1. URnetwork/android — provider lifecycle and orphan recovery

Key ideas:
- Treat GPS and NETWORK as base test providers.
- Add platform FUSED on API 31+.
- Never mock PASSIVE.
- Remove/reclaim existing test-provider state before registering on Android versions where re-registration is not idempotent.
- Track an explicit ORPHANED state when provider cleanup cannot complete after mock-location AppOps is revoked.
- Run a cold-start cleanup sweep because system_server test-provider state may survive the app process.
- Watch mock-location AppOps and retry deferred cleanup after permission is restored.
- Keep Google Play Services FLP mock mode as a separate optional enhancement path, not as the sole source of truth.
- Always leave GMS mock mode on teardown paths because it is device-global.

GoGoGo candidate:
- ProviderReclaimer
- MockProviderLifecycleState { CLEAN, ACTIVE, CLEANUP_PENDING, ORPHANED }
- startup sweep + deferred cleanup
- AppOps restoration watcher

Priority: S

### 2. Kestrel — process-death-resistant route playback

Key ideas:
- Persist route progress periodically rather than relying on onDestroy().
- Persist progressMeters and PingPong direction.
- Clamp restored progress to [0, totalDistance].
- Restore route state after START_STICKY process recovery.
- Flush state on pause/resume/stop/finish/onDestroy as best-effort supplements to periodic checkpointing.
- Avoid stop-then-start races when replacing an active mock session; prefer atomic replace actions.

GoGoGo candidate:
- RouteCheckpointStore
- crash/freeze-resistant replay resume
- atomic replace semantics
- bounded rollback window based on checkpoint cadence

Priority: S

### 3. gps-locator — QA scenario harness

Key ideas:
- scenario and suite model
- device readiness / commandReady state
- multi-device execution
- holdSeconds and stopAfter controls
- environment capture before test
- environment restoration after suite
- JSON summary + JUnit report
- do not hide unavailable devices from a multi-device run

GoGoGo candidate:
Scenario -> Preflight -> Execute -> Observe -> Score -> Restore -> Report

Priority: A

### 4. microG UnifiedNlp API — pluggable backend architecture

Key ideas:
- lifecycle hooks: onOpen / update / report(Location) / onClose
- backend implementation separated from consumers
- helper abstractions for different location sources

GoGoGo candidate backends:
- FixedBackend
- RouteBackend
- ReplayBackend
- RandomWalkBackend
- ScenarioBackend
- RecordedTraceBackend

All backends emit a common LocationSample model.
Provider publication should not care how the sample was generated.

Priority: A

### 5. Google gps-measurement-tools / GNSSLogger — raw GNSS observability

Key ideas:
- centralized MeasurementProvider dispatch
- GnssMeasurementsEvent
- GnssStatus
- NavigationMessage
- NMEA
- normal Location events

GoGoGo candidate:
GNSS Reality Observatory showing, side by side:
- published simulated location
- Android LocationManager consumer result
- GMS Fused consumer result
- raw hardware GNSS status/measurements

This is diagnostic only; do not attempt to falsify raw GNSS.

Priority: A

### 6. FakeTraveler / network-location backends — competing source diagnostics

Key ideas:
- Wi-Fi scanning and Bluetooth scanning can introduce alternate location sources.
- network-location backends show that Wi-Fi/cell positioning is a separate location world from GNSS.

GoGoGo candidate:
Environment Conflict Monitor:
- Wi-Fi scanning status
- Bluetooth scanning status
- Network provider status
- GNSS status
- Fused status
- Google Location Accuracy / related settings where observable

Priority: B

## Current proposed roadmap

### Lab 20 — Reliability Core

Implement:
1. Provider Reclaimer
2. ORPHANED provider state
3. cold-start cleanup sweep
4. deferred AppOps cleanup retry
5. route checkpoint persistence
6. process-death resume
7. atomic session replace

### After Lab 20

- Backend abstraction
- Scenario Runner
- GNSS Reality Observatory
- Environment Conflict Monitor

## Research rule

When evaluating new repositories, classify them separately:

1. Actually modifies Android mock location
2. Generates/simulates movement only
3. Consumes/observes location only
4. GNSS diagnostics only
5. Automation/QA wrapper
6. Root/Xposed/injection-oriented

For GoGoGo, prefer categories 1, 2, 3, 4, and 5 through documented/public Android mechanisms.

## Projects that actually modify Android location

This section is intentionally restricted to projects that really publish mock locations or drive location simulation, rather than GNSS-only observers.

### Strong non-root candidates

#### shortcuts/locationjoystick
- Standard Android mock-location mechanism.
- Foreground mock service.
- Floating joystick overlay.
- Saved routes + GPX import.
- Loop / reverse / return-to-start / roaming.
- Floating map/widget architecture.
- Clear modular split between core location, routes, joystick, widget, settings.
- Particularly interesting for GoGoGo: overlay control, route UX, modular service/state architecture.

#### Akylas/gps-mocker-rs
- Android self-mocking plus desktop-driven device control in one project.
- Publishes bearing, speed, altitude, accuracy.
- GPX / GeoJSON import.
- Valhalla route building and map matching.
- Believable speed model and corner easing.
- Uses route curvature and maneuver data.
- Particularly interesting for GoGoGo: route physics, desktop/ADB bridge, route annotation.

#### vincenzobpt/gps-mock-location
- Android test-provider injection to GPS / NETWORK / FUSED.
- Physically integrated route simulation.
- Acceleration / braking / corner speed limits / stops.
- Concurrency-safe transport controls with epoch invalidation.
- Mock backend hidden behind a testable port abstraction.
- Particularly interesting for GoGoGo: motion engine and transport-state correctness.

#### Lanjunyee/virtual-location
- No-root fixed point, two-point trip, multi-waypoint route playback.
- GPX import.
- GPS / NETWORK / FUSED coverage.
- Foreground service.
- WGS84 / GCJ-02 display conversion while keeping canonical data in WGS84.
- Particularly interesting for GoGoGo: China-friendly map/coordinate UX and strict GPX validation.

#### 0xfnzero/gps-locator
- No-root Android mock location.
- GPX / KML route replay.
- ADB multi-device control.
- Scenario / suite automation and machine-readable reports.
- Particularly interesting for GoGoGo: QA harness and readiness model.

#### BuriXon-code/MockGPS
- GPS + NETWORK test-provider publication.
- Foreground service.
- Optional external broadcast API and Termux control.
- Persistent state + reboot restoration.
- Optional drift.
- Particularly interesting for GoGoGo: external control and boot/session restoration.

#### gamedirty/mock-location
- Publishes GPS / NETWORK / FUSED.
- Foreground service.
- Accuracy / altitude / speed / bearing variation.
- WGS84 / GCJ-02 conversion.
- Lightweight map implementation.
- Particularly interesting for GoGoGo: compact provider engine and China-map compatibility.

### Interesting but use with caution

#### niegl/MockLocation
- Publishes GPS / NETWORK / FUSED and additional provider names.
- Foreground service + wandering mode.
- MIUI/HyperOS compatibility experiments using reflection.
- Important caveat: its provider list includes PASSIVE, while AOSP-oriented research from URnetwork says PASSIVE should not be mocked. Treat this repository as a source of OEM-compatibility clues, not as a design authority.

#### r69shabh/spoofer
- Kotlin/Compose mock-location project.
- OSRM routes and joystick movement.
- Appium/Pytest QA automation.
- Useful for test architecture and route UX.
- Any anti-detection / integrity-bypass claims are out of scope for GoGoGo.

#### 0xsimaa/Smart_Route
- Structured engine/service/storage split.
- Boot recovery and persistent session concepts.
- GPX export and route simulation.
- Useful as another architecture comparison point.

## New shortlist for source-level study

Highest priority:
1. shortcuts/locationjoystick — overlay + modular architecture
2. Akylas/gps-mocker-rs — believable route playback + desktop/device dual target
3. vincenzobpt/gps-mock-location — physics + concurrency-safe transport
4. 0xfnzero/gps-locator — automation and device readiness
5. URnetwork/android — provider lifecycle correctness
6. narumiruna/kestrel — process-death resume

Secondary:
7. Lanjunyee/virtual-location — GPX validation + WGS84/GCJ-02 UX
8. BuriXon-code/MockGPS — broadcast/Termux control + boot restore
9. gamedirty/mock-location — compact triple-provider engine
10. niegl/MockLocation — OEM compatibility clues only

