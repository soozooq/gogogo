# Lab 26 — Country/city quick presets and no-key map usage

## Changes

- Keep **Myanmar · Myawaddy** as the unchanged default home coordinates:
  longitude `98.50895`, latitude `16.68914` (WGS-84).
- Home screen: add the **🌏 快捷选择国家 / 城市（17 个）** dialog. Selecting a preset only fills longitude/latitude input fields. It **does not call `startMock()`** or modify a running service.
- Opening the MapLibre map from the home screen carries the *current* (including manually edited) WGS-84 coordinate as an optional map viewport. Invalid / missing values fall back to Myawaddy. The coordinate order in the intent is explicitly latitude/longitude, while the home location form's ServiceGo intent retains longitude/latitude fields.
- Map experiment tools: append **🌏 快捷跳转国家 / 城市** to the existing menu (without changing existing menu indices). Picking a city pans/zooms the map and selects the point, but **does not start simulated location**; users still click "模拟这里".
- Reuse one `LabLocationPresets` pure Java catalog from both activities to prevent default mismatch. JUnit verifies default exact values, valid WGS-84 ranges, uniqueness, geographically diverse signs, and safe label copies.
- Catalog includes 17 example city/landmark reference coordinates: Myanmar (Myawaddy/Yangon), China, Japan, South Korea, Singapore, Thailand, Vietnam, India, UAE, USA, Canada, UK, France, Germany, Australia, Brazil. These are approximate representative points, not precise official POI boundaries.

## Map provider details

- Existing renderer: **MapLibre Native** via `org.maplibre.gl:android-sdk-opengl`.
- Existing default hosted style (not changed by this patch): `https://tiles.openfreemap.org/styles/liberty`, supplied by **OpenFreeMap**. Their published quick-start and project pages explain that no API key / registration is required to use their public style in a MapLibre mobile app:
  - https://openfreemap.org/quick_start/
  - https://openfreemap.org/
  - https://openfreemap.org/tos/
- Internet connection is needed for online tiles. Free access is not a guarantee of perpetual availability, privacy, zero network usage or SLA. OpenFreeMap is a third-party public service. Keep map/OpenStreetMap/OpenMapTiles attribution displayed.
- Fallback style `https://demotiles.maplibre.org/style.json` remains available in Map Tools. MapLibre's demo style is not a guaranteed production tile service.
- This only removes a map rendering key requirement **for LabMapActivity's existing OpenFreeMap basemap**. Other existing app features (Tencent POI search, reverse geocoding, independent Tencent location diagnostics) may still require their own API keys and permission/consent. Do not claim all API keys in GoGoGo are unnecessary.

## Acceptance

- [ ] Build Check/Gradle unit tests pass on Lab 26 PR head.
- [ ] Preview APK builds successfully on integrated main (or on a manually dispatched branch run).
- [ ] On-device: opening homepage displays Myawaddy coordinates and 17 location presets.
- [ ] Choosing each of a negative-longitude point (New York or London), a negative-latitude point (Sydney), and positive-coordinate point (Tokyo) populates correct order and can start only after pressing the explicit simulation button.
- [ ] From home, edited/preset location opens the map centered there; reopening a fresh map without extras remains Myawaddy.
- [ ] Map tool preset moves/selects map point; user action "模拟这里" alone starts the service.
- [ ] OpenFreeMap loads normally on the user's network, attribution remains visible, and alternate MapLibre Demo style works if selected. Test offline conditions without assuming the public tile server will be reachable.
- [ ] Existing Lab 25 provider/GMS lifecycle and consumer matrix behavior remains unchanged.

Code integration is a *preview* feature; not a claim that all third-party apps accept a mock location.
