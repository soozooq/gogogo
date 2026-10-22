# V3.1 external demo APK

This permission-free Android application is compiled independently of the host
and `containerFixture`. It has its own package, DEX, XML layout, strings and
colors. Neither its APK nor its classes are embedded into Tianwaitian.

Build with `./gradlew :externalDemo:testDebugUnitTest :externalDemo:assembleDebug`.
The separate output is `externalDemo/build/outputs/apk/debug/externalDemo-debug.apk`.
For the V3.1 experiment, download that APK as a file on the phone and select it
through Tianwaitian's Android file picker. **Do not install the demo APK.**

`DemoEntry(Context, Resources, int)` is a deliberate reflection contract:
`createView`, `onHostLifecycle(String)`, `getClickCount`, `resourcesVerified`,
`getResourceEvidence`, `snapshot`, `dispose`, and `isDisposed`.
The reusable session adds `saveState(): Bundle`, `restoreState(Bundle)` before
the first `onCreate`, and `handleBack(): boolean` to return from its own content
page. The Bundle has exactly four primitive fields: `schema` (int, 1), `clicks`
(int, 0–100000), `note` (String, at most 500 UTF-16 code units), and `page`
(String, `home` or `details`). Wrong types, unknown versions and extra fields
are rejected. A null restore starts a new session.
Its detached resource context reuses the validated framework-only V1 pattern.
`DemoState` is per-instance and preserves count, editable note and content-page
navigation across explicit state export. It records only real forwarded
lifecycle callbacks and rejects edits after disposal. Diagnostics export note
length only, never the note text. Actual notes remain inside the Host's private
state storage when the Host chooses to persist the returned Bundle.
`getInteractionCount()` reports only operations in this entry instance: actual
clicks, changed note text and consumed page navigation. Restore, rendering,
lifecycle and state export do not increment it; it is never saved as old data.

The optional `DemoActivity` is an ordinary Android launcher if the APK is installed
separately by a developer. V3.1 does not use it: Android starts only the registered
host proxy Activity, and the proxy displays this APK's View under the host UID.
This sample establishes neither a separate Android sandbox nor compatibility
with arbitrary applications.
