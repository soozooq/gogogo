# GoGoGo Lab 13 — Motion Audit

Lab 13 adds an inspectable quality layer on top of Lab 12's deterministic scenario and kinematic telemetry.

## Goals

The audit answers a narrow engineering question:

> Is the track produced by this GoGoGo test/replay internally consistent and useful for debugging?

It is **not** an anti-detection score and does not attempt to model another application's abuse controls.

## Signals

For the track currently recorded by `ServiceGo`, Lab 13 checks:

- monotonic timestamps
- sampling gaps
- segment distance and speed
- high-speed discontinuities
- abrupt acceleration changes
- abrupt turn-rate changes
- near-duplicate segments
- median sampling interval

## Robust statistics

Threshold checks are complemented by two robust diagnostics.

### Median Absolute Deviation (MAD)

Segment speeds are summarized with a median and MAD. A robust-z threshold highlights isolated speed outliers without allowing one extreme sample to redefine the whole baseline.

### Two-sided CUSUM

A lightweight two-sided CUSUM reports sustained changes in the speed regime. This is useful when a replay changes phase, for example from stationary to walking or from walking to vehicle motion.

MAD outliers and CUSUM change points are **diagnostic signals only**. They do not automatically lower the quality score because legitimate scenario transitions can be statistically unusual.

## Audit score

The 0–100 score only deducts for data-integrity / continuity problems such as:

- non-monotonic timestamps
- very large sampling gaps
- extremely high segment speed
- extreme acceleration discontinuity
- extreme turn-rate discontinuity
- severely sparse sampling

Grades: `A+`, `A`, `B`, `C`, `D`, `F`.

## Integrity

Every audit result gets a SHA-256 digest derived from canonicalized metrics. The exported JSON also includes the Lab 12 kinematic replay-ledger SHA-256 value, making it possible to keep both:

1. the per-frame replay chain, and
2. the aggregate track-quality digest.

These hashes are integrity aids, not digital signatures.

## UI

Open:

`Lab Map → 🧪 Motion Audit`

The screen refreshes while `ServiceGo` is connected and shows:

- score / grade
- point count, duration and distance
- average / maximum / median segment speed
- MAD
- robust outlier count
- CUSUM change-point count
- current acceleration / jerk / turn rate
- bearing arbitration source
- replay-ledger prefix
- audit-digest prefix
- issue summary

The screen can export a JSON audit report through Android's document picker.

## Main files

- `LabMotionQualityEngine.java`
- `LabRobustMotionStats.java`
- `MotionAuditActivity.java`
- `LabMotionQualityEngineTest.java`
- `LabRobustMotionStatsTest.java`

Branch: `feat/gogogo-lab-phase13`
