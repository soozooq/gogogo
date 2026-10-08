# Lab 21 — Simulation Backend + Route Physics

## Goal

Lab 21 moves GoGoGo from mode-specific movement state toward a common simulation
pipeline:

SimulationBackend -> SimulationSample -> Policy/Scenario -> Android/GMS publishers

The publisher layer remains centralized in ServiceGo. Backends never talk directly to
third-party apps and never hide Android's mock-location status.

## Canonical sample

Every active simulation source emits the same fields:

- latitude / longitude / altitude
- speed
- bearing
- accuracy
- source id
- elapsed realtime

Current first-wave backends:

- FIXED
- ROUTE
- ROAM

Replay and deterministic Scenario are intentionally left as the next migration step so
Lab 21 can validate the contract before more sources are moved.

## Route Physics Engine

The old route path used a constant configured speed. Lab 21 now maintains an actual
kinematic speed and computes a target speed for each tick.

Phases:

- IDLE
- ACCEL
- CRUISE
- CORNER_BRAKE
- END_BRAKE
- DWELL
- PAUSED
- FINISHED

Current model:

- acceleration: 1.35 m/s²
- service braking: 2.10 m/s²
- corner speed is derived from upcoming turn angle
- braking begins before the vertex according to braking distance
- turns >= 105° arm a short 550 ms dwell
- ONCE routes brake toward zero at the final waypoint
- a small minimum moving speed prevents asymptotic stopping before the final point

## Reliability interaction

Lab 20 checkpoint/recovery remains authoritative for route persistence.
After process recovery Lab 21 intentionally restarts physical speed from zero and
accelerates smoothly instead of pretending the pre-crash velocity is still exact.

## UI

Lab Map live state exposes:

Physics <phase> · <actual speed> -> <target speed> · backend=<source>

This makes acceleration/braking behavior directly observable without exporting logs.

## Next migration

After Lab 21 is stable:

1. move recorded-trace replay behind SimulationBackend
2. expose Scenario as a transform/backend stage without duplicating provider code
3. add optional GPX timestamp/elevation-derived target speed profiles
4. add configurable pedestrian / bicycle / car physics presets
