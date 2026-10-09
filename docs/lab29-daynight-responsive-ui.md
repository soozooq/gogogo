# Lab 29 — System DayNight, semantic colors, vector icons and small-screen layouts

## Implementation

- Both Android MaterialComponents themes now inherit from `Theme.MaterialComponents.DayNight.*`, **following the system mode automatically**, not adding a duplicated in-app switch. A themed MaterialComponents popup overlay uses the night variant on dark configuration.
- Light (`values/`) and dark (`values-night/`) share the same **16 app-specific color tokens** including `gogogo_on_primary`. The dark primary is bright teal with dark foreground; no white-on-bright-teal.
- Status and navigation bars use `@bool/gogogo_system_bars_light` with day/night resource overrides. Framework-generated dialogs are theme-aware; the map's remote MapLibre style remains unchanged.
- Navigation components in `GoGoUi` use tintable XML vector icons for Back / Chevron / Place / Lab, not platform-dependent emoji glyphs. Home map and Lab entries receive distinct contextual icons; other existing navigation tiles keep a default Lab icon.
- Buttons in the shared UI kit support up to three text lines and remove unneeded min-width, avoiding unexpected truncation with larger fonts.
- A pure Java `LabResponsiveUiPolicy` determines when the **home** main actions and diagnostics row should become vertical stacks: width <=360dp or user font scale >=1.30. Unit tests cover boundary, wider/default, larger-font and defensive unknown width behavior.
- Map bottom deck places `模拟这里` on a full-width primary action, then groups route/roam, follow/heading and motion/diagnostics in 2-column rows. No underlying mock or motion actions were modified.
- Home status copy differentiates `已请求启动` / `已请求停止` from an actual completed Provider/GMS acknowledgement. Shared semantic colors distinguish info, warning, error, neutral and success. This does **not** claim that every target consumer accepted a location.

## Palette checks

Calculated WCAG contrast ratios for the proposed static token combinations (not a screenshot QA):

| Pair | Light | Dark |
| --- | ---: | ---: |
| Primary action foreground/background | 5.47:1 | 9.04:1 |
| Main text on surface | 17.85:1 | 14.62:1 |
| Muted text on surface | 4.76:1 | 9.31:1 |
| Danger text on surface | 4.83:1 | 5.79:1 |

All pairs above exceed 4.5:1 for normal text. Other third-party SDK views and rendered map tiles are outside the token contrast check.

## Out of scope / preserved

No change to ServiceGo provider state machines, route simulation, MockLocation/AppOps access, permission grants, Shizuku, Tencent keys, OpenFreeMap/MapLibre style, package ID or signing identity. Real Android 13 rendering, small-screen device screenshots and third-party consumer behavior are **not** inferred from Gradle success.

## Acceptance gates

- [ ] Build Check including Lint and JUnit passes on Lab 29 HEAD.
- [ ] Merged `main` debug APK artifact and full checks both pass at the merge SHA.
- [ ] On target phone, system light/dark changes colors and status/navigation bar icon contrast without black-on-black TextViews or dialog text.
- [ ] On <=360dp or >=1.30 font scale, home Start/Stop and refresh/settings stack vertically, with no unintended mock start.
- [ ] On map, all seven actions remain operable after reorganizing rows; no map-covering or clipped buttons on the target 13+ phone (also test landscape).
- [ ] In diagnostics and Lab Hub, vector icons remain crisp and visually legible in both modes, and report data remains selectable.
- [ ] Check remote tile attribution, third-party screens and dialogs on actual device.
- [ ] WeChat/Tencent/Provider compatibility remains separately pending as recorded by Lab 25.

The outcome here is code-level visual polish and automated build validation, **not** completed real-device UX certification.
