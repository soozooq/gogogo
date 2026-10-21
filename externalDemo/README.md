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
Its detached resource context reuses the validated framework-only V1 pattern.
`DemoState` is per-instance, preserves click counts across explicit state export,
records only real forwarded lifecycle callbacks, and rejects use after disposal.

The optional `DemoActivity` is an ordinary Android launcher if the APK is installed
separately by a developer. V3.1 does not use it: Android starts only the registered
host proxy Activity, and the proxy displays this APK's View under the host UID.
This sample establishes neither a separate Android sandbox nor compatibility
with arbitrary applications.
