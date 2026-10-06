# GoGoGo Lab — Research Roadmap

GoGoGo started as a mock-location utility. The Lab branches deliberately evolve it into an
Android resource, isolation, and observability playground.

This roadmap maps research ideas to modules we can implement safely in GoGoGo's own test
environment. It is not a plan to bypass security controls or anti-abuse systems in third-party apps.

## 1. TaintDroid — provenance and observability

Paper: *TaintDroid: An Information-Flow Tracking System for Realtime Privacy Monitoring on Smartphones*
(OSDI 2010).

Research idea:
- label sensitive data at its source;
- preserve contextual provenance as data flows;
- make exits/uses observable.

GoGoGo mapping:
- Lab 7 added a lightweight provenance timeline for synthetic location data;
- source tags currently include MANUAL / ROUTE / ROAM / RESTORED;
- policy changes and motion changes are auditable.

Next:
- attach immutable event IDs;
- export provenance beside GPX tracks;
- compare provenance between personal and Work Profile instances.

## 2. MockDroid — resource shadowing

Research idea:
- applications can receive shadow or empty resource values rather than an all-or-nothing permission result.

GoGoGo mapping:
- Lab 8 Resource Broker splits RAW state from PUBLISHED state;
- location can be EXACT, COARSE_250M, COARSE_1000M, or PAUSE_PUBLISH;
- network and sensor shadow views are explicitly synthetic and do not change real OS connectivity/sensors.

Next:
- deterministic seeded shadow values for reproducible tests;
- time/battery/device-info shadow objects for GoGoGo-owned test clients;
- record every broker transformation into provenance.

## 3. MOSES / TrustDroid — multiple security domains

Research idea:
- maintain different security profiles/domains and switch policies as context changes.

GoGoGo mapping:
- PERSONAL / WORK / LAB policy domains;
- Work Profile capability and DPC provisioning experiments;
- Resource Broker policy is independent from the motion engine.

Next:
- store separate policy presets per Android user/profile;
- compare main-profile vs Work-Profile snapshots;
- optional manual context switching rules (never silent or hidden).

## 4. Boxify — application virtualization and reference monitoring

Paper: *Boxify: Full-fledged App Sandboxing for Stock Android* (USENIX Security 2015).

Research idea:
- isolate code in de-privileged processes;
- mediate resource access through a trusted reference monitor / proxy layer.

GoGoGo mapping:
- Shizuku UserService already demonstrates a separate privileged process;
- Resource Broker establishes a reference-monitor-like policy boundary for our synthetic resources.

Safe next experiments:
- build an Android isolatedProcess test service owned by GoGoGo;
- run our own small test client inside it;
- proxy only GoGoGo-defined Binder interfaces through the Resource Broker;
- measure IPC overhead and policy latency.

Out of scope:
- transparent repackaging or stealth hooking of unrelated third-party applications;
- bypassing anti-cheat, anti-fraud, DRM, or app integrity systems.

## 5. Android Virtualization Framework / pKVM / Microdroid

Modern Android direction:
- AVF provides VM isolation backed by pKVM;
- Microdroid is a minimal Android guest intended for protected workloads;
- public third-party apps generally cannot assume access to privileged VM-management APIs.

GoGoGo mapping:
- Lab 7 added AVF/pKVM/Microdroid readiness probes;
- Shizuku-side diagnostics inspect virtualization features/APEX/property state.

Next:
- classify devices as AVF unavailable / capability present / development-ready;
- if a development device has the required platform privileges, build a separate Microdroid proof-of-concept;
- keep VM payloads independent from the normal phone build.

## Proposed branch sequence

### Lab 8 — Resource Broker
- RAW vs PUBLISHED resource boundary
- PERSONAL / WORK / LAB domains
- location precision policies
- network/sensor shadow views

### Lab 9 — Cross-profile Observatory
- snapshot identity per Android profile
- main vs Work Profile comparison
- profile-local provenance export

### Lab 10 — Isolated-process Test Capsule
- GoGoGo-owned isolatedProcess test client
- Binder proxy/reference monitor
- policy latency and IPC metrics

### Lab 11 — Deterministic Resource Simulator
- seeded location noise
- synthetic sensor/time/battery resources for our own test client
- scenario files and replay

### Lab 12 — AVF Development Track
- development-device readiness
- separate Microdroid proof-of-concept when platform permissions are available
- host/guest measurements and signed experiment reports

## Design rules

1. Keep RAW state and PUBLISHED state visibly separate.
2. Never describe a shadow view as if it changed the real OS state.
3. Prefer official Android isolation mechanisms before compatibility hacks.
4. Every synthetic transformation should be auditable.
5. Experimental privileged actions should be explicit and user-initiated.
6. Keep the stable mock-location branch independent from Lab experiments.
