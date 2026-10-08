# Lab 25 — Test Provider cleanup evidence & AppOps observation

## Why this is a targeted follow-up (not a new mock engine)

The 2026-10-08 external open-source research report recommended a cold-start
provider sweep and an `ORPHANED` state. **Both already exist in Lab 20** in
`LabProviderReliabilityController`, so Lab 25 extends its observability
rather than replacing it.

Goal: distinguish a clean application callback, a completed Android removal
API call, an interrupted attempt, a permission denial and downstream consumer
cache behavior. These are different facts.

## Code changes

- Existing startup sweep is retained for GPS / NETWORK / Android framework
  FUSED (the last one only on API 31+); PASSIVE is never altered.
- Each startup sweep or deferred retry records its trigger, previously owned
  flag from GoGoGo's own SharedPreferences, AppOps allowed state, start/end time,
  the Android API result and a short optional disable warning.
- Before issuing calls, `PENDING` is committed synchronously. The next sweep
  retains the previous attempt outcome so an interrupted sweep stays visible.
- `REMOVE_RETURNED` means only that `removeTestProvider()` returned normally.
  `ALREADY_ABSENT` means the API indicated no registered test provider.
  Neither outcome proves that Google Fused, Tencent or another app refreshed
  its cached coordinates.
- A permission-denied removal after prior ownership is classified
  `ORPHANED`; otherwise a failed cleanup is `DEGRADED`.
- For best-effort disabling, a RuntimeException does not block the subsequent
  removal attempt. The exception class is recorded, not device locations.
- Monitor only this app's `OPSTR_MOCK_LOCATION` events while ServiceGo is
  running. On permission return, retry deferred ORPHANED cleanup only when
  no provider is ACTIVE, to avoid racing a live publication session.
- The Lab diagnostics page and saved environment snapshots display the
  persisted audit even if the mock service no longer exists.
- A pure-Java `LabProviderEvidencePolicy` and six JUnit cases cover
  cleanup outcomes, interrupted operation and guarded AppOps retry.

## How to interpret an audit snapshot

- `beforeOwned=true`: GoGoGo previously recorded that it installed a
  provider; it is **not** an authoritative query of system_server.
- `PENDING`: attempt began but never wrote its final outcome; this does not
  prove provider persistence.
- `SECURITY_DENIED`: Android denied the removal API.
- `REMOVE_RETURNED`: call returned normally; verify separately using the
  Android consumer matrix and, if available, `dumpsys location`.
- `watcher=REGISTERED` is last persisted observation of listener setup;
  after a force-stop it does **not** prove the listener is still alive.
- Logs store no location coordinates, Wi-Fi BSSIDs, SIM identifiers or user
  accounts. Environment snapshots from other diagnostics may contain
  sensitive content and should not be shared publicly unreviewed.

## Minimum acceptance steps

1. Install Lab 25 alongside the existing same-signature test installation
   without clearing the app data. Save a baseline environment snapshot.
2. Start mock service. Visit Lab Diagnostics and read Lab 25 before/after
   ownership markers, AppOps state and sweep outcomes.
3. Stop mock normally, reopen Lab Diagnostics, save another snapshot.
4. Launch the mock service again and use controlled local testing to
   terminate the app while publishing, then reopen and inspect the prior
   incomplete attempt evidence, the new sweep and Consumer Matrix result.
5. Revoke mock-location authorization, inspect `SECURITY_DENIED` or
   `DEGRADED/ORPHANED` if removal is attempted; restore authorization
   and verify the state without treating UI location as direct proof.
6. Only if appropriate test equipment is available, capture a minimal
   `dumpsys location` excerpt before and after; redact locations,
   installed apps and any private identifiers.

## Known limitations / release gate

- This audit does not read the protected true system_server test-provider
  registry; it records Android API outcomes and this app's previous marks.
- AppOps notifications may vary by device/ROM and a force-stopped app cannot
  observe callbacks while dead. Cold-start sweep is still the recovery path.
- Public GitHub Actions uses a DUMMY Tencent key: a successful build is not
  proof of Tencent SDK network positioning.
- This patch neither reads nor modifies WeChat's internals, nor suppresses
  the Android mock flag. No Root/LSPosed hooking, no stealth or anti-detection.
- Keep PR draft and Lab 24 stable until full Build Check, debug APK build,
  and actual device tests have all passed.
