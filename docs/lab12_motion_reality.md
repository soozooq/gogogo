# GoGoGo Lab 12 — Motion Reality Engine

Lab 12 turns GoGoGo's mock-location pipeline into an observable motion experiment instead of a stream of unrelated coordinates.

## What changed

### 1. Course and handset heading are different resources

Older builds reused one `mCurBea` value for both route course and physical phone orientation. A rotation-vector event could therefore overwrite route bearing, while route/roam motion could overwrite the handset direction.

Lab 12 separates them:

- **Course bearing** — derived from route/roam geometry.
- **Device heading** — observed from Android rotation-vector sensors.
- **Published bearing** — selected by the kinematic arbiter.

Automatic arbitration uses course while moving and device heading while essentially stationary. This gives the mock `Location` a coherent bearing without modifying physical sensor values.

### 2. Kinematic observatory

`LabKinematicsEngine` derives a frame for every service tick:

- motion class: PAUSED / STATIONARY / WALKING / RUNNING / CYCLING / VEHICLE
- speed
- acceleration
- jerk
- turn rate
- course bearing
- device heading
- effective published bearing
- bearing source

Values are intentionally bounded so bad input cannot explode the dashboard.

### 3. Replay hash ledger

Each kinematic frame is canonicalized and chained:

```
H[n] = SHA-256(H[n-1] || canonical_frame[n])
```

This is an integrity/audit aid, not a digital signature. Replaying the same deterministic motion inputs after a reset reproduces the same ledger.

### 4. Compass-follow Lab map

The MapLibre Lab map now has:

- **🧭 地图朝向：开/关**
- **⬆ 地图归北**
- ~250 ms camera-follow updates instead of the former 1 s position-only follow
- live display of physical device heading, published bearing, acceleration, jerk, turn rate and ledger hash prefix

### 5. Reports

Scenario experiment JSON now records both the deterministic scenario resources and live kinematic state, including the full motion-ledger SHA-256 value.

## Scope

Lab 12 does **not** inject or replace another application's accelerometer, gyroscope, magnetometer or rotation-vector events. It observes the handset orientation available to GoGoGo and uses that information only in GoGoGo's own test pipeline and the bearing metadata of locations GoGoGo emits.

That boundary is deliberate: it keeps the feature useful for location/motion testing and reproducible experiments without pretending to be an OS-wide sensor virtualization layer.

## Main files

- `LabKinematicsEngine.java`
- `ServiceGo.java`
- `LabMapActivity.java`
- `ScenarioLabActivity.java`
- `LabKinematicsEngineTest.java`

Branch: `feat/gogogo-lab-phase12`
