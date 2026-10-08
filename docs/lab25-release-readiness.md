# GoGoGo Lab 25 — Code audit and release readiness (2026-10-09)

## Purpose and scope

This is a pre-release **known-issues and verification gate**, not an assertion that
all Android OEMs or third-party apps accept GoGoGo's mock locations. Preserve
Lab 24 as fallback; Lab 25 stays a draft until physical-device testing.

## Corrected in this review

1. **CI compilation**: initialize the AppOps listener only after the final
   `Context` field is assigned (commit `417a8ac`). The full Gradle and debug
   APK workflows both passed for that checkpoint.
2. **Provider cleanup evidence**: the existing Lab 20 cold-start sweep was
   already present. Lab 25 preserves its own `startup_` evidence separately
   from subsequent `provider_` service cleanup, so onCreate's second removal
   does not erase the cold-start outcome.
3. **Shared cleanup path**: ServiceGo GPS / NETWORK / framework FUSED removal
   now delegates to the existing provider reliability controller. Runtime
   failures during the best-effort disable call no longer prevent attempting
   `removeTestProvider`. Results distinguish `REMOVE_RETURNED`,
   `ALREADY_ABSENT`, `SECURITY_DENIED`, `PENDING`, `OTHER_FAILURE`.
4. **Safe deferred cleanup**: both the AppOps observer and the public deferred
   retry path avoid removal while *any* provider is still marked ACTIVE.
5. **Shutdown state**: `ServiceGo.onDestroy()` no longer lets a route
   checkpoint exception skip core teardown. It sets its stop flag before
   checkpointing, asks its publisher thread to stop, and waits at most
   350 ms before removing providers.
6. **GMS async lifecycle**: service stop attempts `setMockMode(false)` even
   when `setMockMode(true)` was still pending; a late enable callback triggers
   another disable. Asynchronous request/result states are persisted without
   coordinates. A DISABLE_REQUESTED record is **not** a success record.
7. **GMS cross-thread flag**: `mFusedMockEnabled` is now volatile, so worker
   and main-thread visibility is defined.
8. **Diagnostic clarity**: Lab Diagnostics shows startup/service removal
   separately, previous interrupted attempts, AppOps observations, and the
   last GMS mode request/result. Recorded watcher status is historical and
   does not prove the listener survives a process kill.

## Checks that still block release

- [ ] Latest full Gradle Build Check and Debug APK runs pass for the final
      source commit (not merely an earlier checkpoint).
- [ ] On-device normal start → GPS / NETWORK / FUSED observations → normal
      stop → clean consumer readings.
- [ ] On-device force-stop / crash / Shizuku permission revoke → cold-start
      recovery, with recorded prior PENDING/ORPHANED state and per-provider
      API outcomes; redact any dumpsys or environment data.
- [ ] GMS `setMockMode(false)` reports completed success under normal stop,
      including a case where shutdown races enable. Simulate unavailable GMS
      and denied permissions.
- [ ] Tencent consumer GEO/POI/cache on/off tested with a valid **app-owned**
      Tencent LBS key in a private build and consent. Public CI uses DUMMY.
- [ ] UI map center, automatic location result, searched POI and the distance
      label measured separately in a black-box run; no claims about WeChat's
      internals without evidence.
- [ ] Check overlay/notification/foreground service lifecycle, battery and
      OEM background execution behavior on the actual target Android phone.
- [ ] Decide production signing before distribution. This public fork
      includes `keystore/GoGoGo.jks` and a checked-in signing configuration.
      Treat the included key as **public test-only material**. A replacement
      signer impacts in-place upgrade compatibility and requires an explicit
      migration/reinstall decision. Do not publish production credentials.

## Limits of this fix set

- Android's test-provider calls do not verify every other SDK consumer's
  last-known location, nor that another app updated its own search results.
- A killed process cannot execute onDestroy or await an asynchronous Task.
  A fresh start plus system consumer checks are necessary to verify recovery.
- The 350-ms worker join is bounded by design; a stuck publisher thread may
  remain and is logged. This still requires real-device validation.
- No injection into third-party processes, no hiding `Location.isMock`, no
  disabling their integrity checks.
- Rootless mock-location APIs may not cover raw GNSS, Wi-Fi/cell positioning,
  proprietary OEM services or apps that deliberately reject mock coordinates.

## Evidence sources

- GoGoGo Lab 20 implementation: `LabProviderReliabilityController.java`
- Lab 23 lifecycle evidence: `LabServiceLifecycleJournal.java`
- Lab 24 independent Tencent probe: `TencentLocationProbeActivity.java`
- Lab 25 design: `docs/lab25-provider-cleanup-evidence.md`
- Muse open-source research report (2026-10-08), furnished by the user:
  provider persistence is a mechanism-based hypothesis, **not** a confirmed
  explanation of the WeChat 12,900 km display anomaly.

## Release decision

Do not auto-merge or replace the working APK. The Lab 25 development branch
is complete only when the final source CI and required device verification
are recorded. An unresolved risk must stay listed rather than being called
fixed without evidence.
