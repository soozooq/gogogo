# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

GoGoGo (影梭) is an Android mock location app for Android 8.0+ that does not require root access. It uses Android's debug API (`LocationManager.addTestProvider`) combined with Tencent Maps SDK to simulate GPS and network locations. The app also includes a floating joystick overlay for controlling simulated movement.

- **Package**: `com.zcshou.gogogo`
- **License**: GPL-3.0-only
- **Min SDK**: 27 (Android 8.0), **Target/Compile SDK**: 32
- **Build Tool**: Gradle 8.12.1, Java 11 source/target, JDK 17 for CI

## Build Commands

All commands should be run from the project root (`GoGoGo/`):

```bash
# Build the project (debug + release)
./gradlew build

# Build debug APK only
./gradlew assembleDebug

# Build release APK only
./gradlew assembleRelease

# Clean build outputs
./gradlew clean
```

Output APKs are named: `Go_${versionName}_arm64-v8a_debug.apk` / `_release.apk` and placed in `app/build/outputs/apk/`.

## Required Setup

Before building, create `local.properties` in the project root with Tencent Maps key:

```properties
TENCENT_MAP_KEY=<your_tencent_maps_key>
MAPS_API_KEY=<your_baidu_maps_api_key>
```

- `TENCENT_MAP_KEY` is required for the Tencent Maps SDK (vector map rendering).
- `MAPS_API_KEY` is an optional Baidu Maps API key used for Baidu reverse geocoding in the history feature and InfoWindow display.

The `secrets-gradle-plugin` injects `TENCENT_MAP_KEY` into `AndroidManifest.xml` as `${TENCENT_MAP_KEY}` and exposes `BuildConfig.MAPS_API_KEY` from `MAPS_API_KEY`. Without `TENCENT_MAP_KEY`, the map will not render at runtime.

The signing keystore is at `keystore/GoGoGo.jks` with hardcoded passwords in `app/build.gradle` (used for both debug and release builds).

## Architecture

### Core Mock Location Flow

The app mocks location through a foreground service that periodically injects fake locations into Android's location framework:

1. **User selects a position** on the Tencent Map in `MainActivity` (or via search/history). The map uses GCJ02 coordinates (Tencent's default).
2. **Tapping the FAB** starts `ServiceGo` as a foreground service, passing the selected WGS84 coordinates (converted from GCJ02 via `MapUtils.gcj02towgs84`)
3. **`ServiceGo`** registers test providers for both `GPS_PROVIDER` and `NETWORK_PROVIDER`, then on a `HandlerThread` loop (every ~100ms), injects `Location` objects via `LocationManager.setTestProviderLocation()`
4. The service runs continuously until explicitly stopped; a notification provides show/hide controls for the joystick

### Floating Joystick (`JoyStick`)

`JoyStick` is a `WindowManager` overlay (`TYPE_APPLICATION_OVERLAY`) that provides three switchable floating windows:

- **Joystick view**: A directional pad (rocker or button style, configurable in settings) that sends movement vectors to `ServiceGo` via a listener callback. Supports walk/run/bike speeds.
- **Mini-map view**: A small Tencent Map for selecting a new location without returning to the main app.
- **History view**: A searchable list of previously used locations pulled from the SQLite database.

The joystick communicates back to `ServiceGo` through `JoyStickClickListener` callbacks (`onMoveInfo` for continuous movement, `onPositionInfo` for teleporting).

### Coordinate Systems

The app deals with three coordinate systems:

- **GCJ02** (Mars/Tencent): Primary coordinate system used for all Tencent Map SDK interactions (display, markers, search). Also used by WeChat mini-programs.
- **WGS84**: Used when injecting locations into Android's test providers (GPS/Network)
- **BD09** (Baidu): Retained for Baidu reverse geocoding API calls (InfoWindow comparison display and history address lookup)

All conversions are handled in `MapUtils` (`gcj02towgs84`, `wgs2gcj02`, `gcj02tobd09`, `bd09togcj02`, `bd2wgs`, `wgs2bd09`). When a user taps the map, GCJ02 coordinates are converted to WGS84 before being passed to `ServiceGo`.

### Database Layer

Two SQLite databases managed via `SQLiteOpenHelper`:

- **`DataBaseHistoryLocation`** (`HistoryLocation.db`): Stores saved locations with both WGS84 and GCJ02 coordinates (in `DB_COLUMN_LONGITUDE_CUSTOM` / `DB_COLUMN_LATITUDE_CUSTOM`), plus a human-readable address fetched via Baidu or Tencent reverse geocoding API.
- **`DataBaseHistorySearch`** (`HistorySearch.db`): Stores search queries and selected POI results.

Both tables use `DB_COLUMN_TIMESTAMP` for ordering (newest first). The location history is deduplicated on insert by WGS84 coordinates.

**Note on migration**: The database schema was not changed when switching from Baidu to Tencent maps. Old history records stored BD09 coordinates in the `CUSTOM` columns; these will be interpreted as GCJ02 after the app update, which may cause slight location drift for old records.

### Key Classes

| Class | Purpose |
|-------|---------|
| `GoApplication` | Application init: XLog, Tencent SDK (`TencentMapInitializer`), privacy consent flags |
| `MainActivity` | Main map UI, search, drawer navigation, update checker, binds to `ServiceGo` |
| `ServiceGo` | Foreground service injecting fake GPS/Network locations every 100ms |
| `JoyStick` | Floating overlay with joystick, mini-map, and history views |
| `MapUtils` | Coordinate conversion between WGS84/GCJ02/BD09 |
| `GoUtils` | Permission checks, network/GPS state, dialog helpers, `TimeCount` timer |
| `DataBaseHistoryLocation` / `DataBaseHistorySearch` | SQLite helpers for history |

### Important Implementation Details

- The app requires **Developer Options** → "Select mock location app" to be set. This is checked via `GoUtils.isAllowMockLocation()` which attempts to add a test provider.
- **WiFi interferes with mock location stability** - the app warns users to disable WiFi (even disconnected) because Android may fall back to WiFi-based positioning.
- `ServiceGo` uses `HandlerThread` with `Process.THREAD_PRIORITY_FOREGROUND` for the location injection loop.
- API level branching exists for `Build.VERSION_CODES.S` (Android 12+) in both `ServiceGo` and `GoUtils` due to `ProviderProperties` replacing `Criteria` in `addTestProvider`.
- Tencent Maps SDK privacy compliance requires explicitly calling `TencentMapInitializer.setAgreePrivacy(true)` before initialization (done in `GoApplication`).
- Location is obtained via system `LocationManager` (GPS + Network providers) instead of a fused location SDK. WGS84 coordinates from the system are converted to GCJ02 for map display.
- Search suggestions use Tencent Place Suggestion Web API (`apis.map.qq.com/ws/place/v1/suggestion`) instead of an SDK-native search API.
- The app is single-APK `arm64-v8a` only (`abiFilters`).

### Tencent SDK Dependencies

Tencent SDKs are managed via Gradle remote dependencies:

```gradle
// 核心地图绘制
implementation 'com.tencent.map:tencent-map-vector-sdk:6.11.0.260507.ab2310c2.209828299'
// LatLng / MapPoi / CameraPosition 等基础类型 (6.x 起从主包独立出来)
implementation 'com.tencent.openmap:foundation:0.9.0.e5ea763-pro'
// 工具组件 (小车平移、点聚合等)
implementation 'com.tencent.map:sdk-utilities:1.0.10'
```

注意点：

- **`foundation` 必须显式声明**:6.x SDK 把 `LatLng`/`MapPoi` 等 model 类拆到了 `com.tencent.openmap:foundation`,主包的 pom 又没有声明这个传递依赖,漏了就会在编译期报 "找不到符号 LatLng"。
- **`TencentMapInitializer.setAgreePrivacy()` 签名变化**:5.x 是 `setAgreePrivacy(boolean)`,6.x 起改为 `setAgreePrivacy(Context, boolean)`。
- **POI 点击 Listener**:`TencentMap.OnMapPoiClickListener` 是独立于 `OnMapClickListener` 的接口,方法名为 `onClicked(MapPoi)` (不是 `onMapPoiClick`)。
- AAR 自动捆绑所需 `.so`,无需手动管理。
- 地图 Key 在 `local.properties` 的 `TENCENT_MAP_KEY` 中配置,通过 `secrets-gradle-plugin` 注入到 `AndroidManifest.xml`。

## CI/CD

GitHub Actions workflows:

- **`.github/workflows/build-check.yml`**: Runs on push/PR to `master`. Creates `local.properties` from secrets and runs `./gradlew build`.
- **`.github/workflows/build-release.yml`**: Triggered on tag push. Builds release APK, signs it with `r0adkll/sign-android-release`, generates changelog with `ardalanamini/auto-changelog`, and creates a GitHub release.

Required repository secrets for CI: `TENCENT_MAP_KEY`, `MAPS_API_KEY`, `SIGNING_KEY`, `ALIAS`, `KEY_STORE_PASSWORD`, `KEY_PASSWORD`.
