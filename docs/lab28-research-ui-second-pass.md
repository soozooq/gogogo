# Lab 28 · Sandbox / Cross Profile / Isolated Capsule UI second pass

## Scope

Continues the Lab 27 `GoGoUi` and Lab Hub consolidation. Changes **only presentation and navigation**, with the same experiment services, Android framework interfaces, Binder calls and confirmations.

### Components

- `GoGoUi.addLabHeader(...)`: common back arrow, compact section label, heading and subtitle. Back goes to the actual caller (Lab Hub or Sandbox), not an assumed parent.
- `GoGoUi.reportPanel(...)`: selectable 13sp diagnostic data in a surface card with consistent border, padding and line height.
- `GoGoUi.actionStack(...)`: vertical full-width Material actions to replace horizontal-scrolling button rows on narrow screens; text may wrap under large font settings.

### Screens

| Screen | Before | After |
| --- | --- | --- |
| Sandbox Lab | Mixed unstyled buttons, many equally prominent controls, redundant Scenario/Hub navigation | Clear device ability, current profiles, settings, separately labeled **managed-profile creation** and related isolation tools. Explicit confirmation remains mandatory; Scenario/Replay stays reachable through Lab Hub. |
| CrossProfile Observatory | Horizontal 3-button strip, misleading “back to Sandbox” text when launched from Hub, raw reports | Separated local snapshot, Android cross-profile consent, import/comparison and clear workflow; reports remain selectable. Sensitive exported environment data warning. |
| Isolated Capsule | Raw long report text mixed with horizontally hidden Binder controls | Explicit connection, resource probes and benchmark groups; 100 / 1000 iteration buttons remain visible on narrow phones; broker/isolated UID logic unchanged. |

## No behavior changes

- `SandboxLabActivity.confirmProvisionManagedProfile()` still enforces system `isProvisioningAllowed` and explicit app + Android system confirmation. This UI work never creates a profile without user consent.
- `CrossProfileObservatoryActivity` export/import/compare, `allowlistSelf()`, `requestCrossProfileConsent()`, and `switchToOtherProfile()` are unchanged.
- `IsolatedCapsuleLabActivity` bind/stop lifecycle, `runDirectProbe()`, `requestBrokerSnapshot()` and Binder latency benchmark methods unchanged.
- No mock-provider, location injection, Shizuku, root, external app hooking or API key changes.
- Lab Hub already has direct routes to Sandbox, Cross Profile, Isolated Capsule and Scenario/Replay.

## Acceptance

- [ ] CI full Build Check passes on this PR HEAD.
- [ ] After merge, `main` Gradle Build Check and debug APK pass at merge HEAD.
- [ ] On-device: all three pages load from Lab Hub, no hidden horizontal controls at width around 320–360dp or large font size.
- [ ] Back arrow returns to correct calling screen whether opened via Hub or Sandbox.
- [ ] Sandbox abilities refresh, Android settings buttons open if supported, and system provisioning still requires explicit confirmation.
- [ ] Cross Profile export/import, consent/allowlist and comparison produce the same output as before. Check and redact sensitive fields prior to sharing JSON.
- [ ] Capsule binding, identity, reference monitor, Broker snapshot, 100/1000 Binder iterations all work and show readable selectable reports.
- [ ] No unintended change to running simulated location (none of these screens should start it).

This is a **code/visual organization pass**, not real-device QA or proof of third-party location acceptance.
