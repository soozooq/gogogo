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
