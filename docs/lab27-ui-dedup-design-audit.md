# GoGoGo Lab 27 — UI hierarchy, deduplication and visual design audit

## Scope and baseline

Based on the actual `main` files in commit `37fcb7a43cd35fd4b87248227691b298f387d594`.
This is the **first implementation pass**, not a claim that every historical Lab screen has
been visually remastered or tested on the target phone. No mock location
provider, route physics, map tile source or Shizuku functionality is changed.

## Before → After

| Area | Existing source / problem | Lab 27 |
| --- | --- | --- |
| Launch screen | `SimpleMockActivity`: separate Experimental Center and Diagnostics/Tools cards, large diagnostic dumps shown immediately | Keep WGS-84, Myawaddy, 17 Lab 26 city presets, start/stop at top. Add two discoverable navigation tiles: **地图选点与路线**, **实验与诊断**. Self-test details start collapsed, remain expandable and selectable |
| Location state | Home initialized `● 未启动` even when returning from a running service | `onResume()` synchronizes the displayed summary with `ServiceGo.sRunning` (not a guarantee of provider status) |
| Map | `LabMapActivity.showExperimentPanel()` mixed 10 independent Activity entrances with map-only provenance actions | Bottom **诊断** opens Lab Hub. Preserve **查看位置来源链** and **清空位置来源链** inside map tools, together with original map-specific actions |
| Lab access | Users had to discover pages inside a map experiment menu | New internal, non-exported `LabHubActivity`, with three clear groups and 12 existing screens. Opening the hub does not start mocks or request Shizuku privileges |
| Diagnostics UI | `LabDiagnosticsActivity` used unrelated plain Buttons, TextViews and a blank background | Use shared GoGoUi palette, heading, subtitle, stroked read-only status panels and Material secondary buttons |
| Duplicate controls | Diagnostics had **刷新服务取证** plus **立即刷新**, both calling `refreshViews()`, and a bottom return in addition to ordinary back navigation | One refresh action only; one top return. Remove redundant direct navigation to Policy / Sandbox; both remain in Hub |

## Preserved paths (no functionality deleted)

- **Home**: country/city picker; editable longitude and latitude; start/stop
  action (only explicit clicks); Refresh self-check; system-setting tools;
  selectable detailed diagnostics under an expander.
- **Map**: MapLibre / OpenFreeMap style switching, map click, presets,
  favorites/history, route import/editor/replay, speed/roaming/motion,
  provenance timeline & clear; simulated location still only after
  **模拟这里** / explicit motion actions.
- **Lab Hub — location & compatibility**: LabDiagnosticsActivity,
  ConsumerLocationProbeActivity, TencentLocationProbeActivity,
  LocationCompatibilityActivity, CompatibilitySessionActivity.
- **Lab Hub — motion / route research**: ScenarioLabActivity,
  MotionAuditActivity, HeadingLabActivity.
- **Lab Hub — systems**: PolicyLabActivity, SandboxLabActivity,
  CrossProfileObservatoryActivity, IsolatedCapsuleLabActivity.
- **Diagnostics**: service lifecycle, Provider cleanup evidence, network,
  sensors, system snapshots, privileged Shizuku commands and Device Policy probes.

## Design rules for subsequent screens

- Background `gogogo_bg`, `gogogo_surface` cards, shared radius 14–18dp,
  text hierarchy 28 / 16 / 14 / 13sp through `GoGoUi`; dark text and muted
  secondary text. Native Android text scaling should remain effective.
- One dominant primary action per task. Danger state reserved for destructive
  stop/clear; UI entry navigation gets a neutral navigation tile.
- Entry points may exist in context where they serve **different tasks**
  (home preset fills a coordinate, map preset moves a viewport); do not
  delete such entries merely because the names overlap.
- Do not move map-only actions into global diagnostics. Do not force Shizuku,
  Android permissions or start/stop actions on a navigation click.
- Keep long diagnostic reports copyable and accessible, but hide them by
  default on the **home page only**. The dedicated diagnostics page displays
  full information.
- This pass uses code-driven Android views; visual appearance and text
  overflow still require tests on a phone with narrow width and large fonts.

## Acceptance

- [ ] Build Check (`./gradlew build` including Lint + JUnit) passes for PR HEAD.
- [ ] Full `main` Build Check / Debug APK after merge (or preview branch
  manually dispatched) both pass.
- [ ] Launch on Android 13: start/stop Mock still work; Myawaddy stays default;
  coordinate presets retain WGS-84 order and map opens correct position.
- [ ] Navigation from home → Lab Hub → all 12 Activities works. Back to home
  or Map correctly, with no accidental service start.
- [ ] Navigation from Map → Lab Hub works; map-only provenance actions work
  in map tools (with confirmation for clear action as before).
- [ ] Diagnostics Refresh, Shizuku requests, snapshots, Provider/GMS evidence
  still behave identically to baseline. User-facing duplicate refresh is gone.
- [ ] Home diagnostics expander reveals selectable full text and can collapse;
  its network status refresh still updates while collapsed.
- [ ] Small phone / large font / screen rotation / dark theme contrast
  reviewed with screenshots on actual device.
- [ ] No claims about Tencent/WeChat consumer compatibility or a stable release
  without independently collected device evidence.

## Follow-ups intentionally not claimed complete

- Full visual remaster of all 12 historical Lab Activities; this pass establishes
  a shared UI/navigation direction.
- Evaluation of iconography/illustrations, night theme and high contrast on
  a real device; do not label as tested based on Gradle results alone.
- GMS Task acknowledgement metric and real Tencent Key tests from Lab 25.

**Branch strategy:** PR into `main` after automated checks, keep physical
device acceptance separate. Do not delete unrelated services or alter signing.
