# Codebase Modernization Baseline

This document defines the rules for organizing SuperToyBox from the `v2.1.20` baseline. The goal
is to make feature ownership, runtime lifecycles, dependencies, and verification explicit without
changing vehicle behavior during structural work.

## Non-negotiable Rules

1. Every refactor must preserve observable behavior unless the change is documented separately.
2. Vehicle reads continue to follow the ownership rules in `vehicle-runtime-architecture.md`.
3. UI code does not issue vehicle reads, Binder calls, ADB commands, or database writes directly.
4. Runtime controllers own one lifecycle and expose idempotent `start` and `stop` operations.
5. A screen has one navigation destination. Mutually exclusive pages are not represented by
   independent Boolean flags.
6. New feature state is immutable and exposed through `StateFlow`; mutable state remains private.
7. Cross-feature operations use an interface or coordinator instead of referencing another
   feature's Android component or mutable static state.
8. Refactors add or retain tests for state transitions, sanitization, persistence, and failure
   behavior.

## Ownership Map

| Area | Owns | Must not own |
| --- | --- | --- |
| `domain` | Pure calculations, state machines, policies | Android services, windows, persistence |
| `data` | Repositories, persistence, network and vehicle gateways | Compose UI and navigation |
| `service` | Android lifecycle orchestration | Feature business rules |
| `ui` | Rendering, user input and screen state | Binder, ADB and direct database access |
| `helper` | Shell-uid helper process and Binder protocol | Product UI state |
| Feature runtime packages | One feature's runtime coordination | Unrelated settings and navigation |
| `server/platform` | Commercial API and administration | Vehicle runtime behavior |

## Modernization Order

### Phase 1: State and navigation foundations

- Replace mutually exclusive page flags with typed destinations.
- Separate navigation rules from Compose rendering and cover them with unit tests.
- Keep all existing routes and back behavior stable.

### Phase 2: Settings ownership

- Split authorization/payment, update/diagnostics, vehicle-event audio, energy pricing, appearance,
  and backup operations into focused coordinators.
- Keep `SettingsViewModel` as a screen-level facade while UI call sites migrate.
- Split `SettingsUiState` into domain groups only when call sites can consume them independently.

### Phase 3: Runtime controllers

- Separate Android window ownership from command execution and data collection.
- Standardize lifecycle state and coroutine ownership for desktop cards, cluster projection,
  lyrics, and LED runtimes.
- Make cleanup idempotent and test transitions without a vehicle.

### Phase 4: Build boundaries

- Introduce Gradle modules only after package dependencies are clean enough to enforce them.
- Prefer capability modules such as vehicle runtime, automation, and platform client over one module
  per screen.
- Keep BYD and hidden-API integrations behind Android implementation boundaries.

## Completion Gates

A phase is complete only when:

- `./gradlew verifySourceHealth` passes;
- `./gradlew testDebugUnitTest` passes;
- `python3 -m unittest test_security` passes from `server/platform`;
- `git diff --check` passes;
- no behavior-changing cleanup is hidden inside a structural commit;
- new ownership decisions are reflected in this document or the runtime architecture document.

The source-health task is a ratchet, not a claim that every existing hotspot is already resolved.
Limits should only move downward as large classes are split. Raising a limit requires a documented
architecture decision and should not be bundled into an unrelated feature change.

## Completed Structural Passes

Completed structural passes:

- `DashboardLiveDataCoordinator` now owns dashboard repository Flow composition.
- `DashboardLiveStateMapper` now owns live vehicle snapshot to `DashboardUiState` mapping.
- `DesktopQuickCardCommandExecutor` now owns generic automation and guarded auto-door execution;
  the overlay controller keeps only interaction, lifecycle, and feature-specific toggles.
- `DesktopQuickCardWindowController` now owns the desktop card `WindowManager` view, Compose
  overlay lifecycle, drag position, and expand/collapse resizing.
- `DesktopQuickCardMediaController` now owns desktop-card sound playback broadcasts, sound
  lifecycle cleanup, file validation, channel selection, and reverse-gear playback state.
- `DesktopQuickCardFeatureController` now owns lyric and dual cluster-card toggles plus their
  preference/Flow observation; `DesktopQuickCardHailingController` owns realtime-hailing
  permission checks, service commands, broadcasts, and cleanup.
- `ClusterProjectionManager` is now the projection session orchestrator; it retains serialized mode
  transitions, task movement, failure cooldown, and automatic recovery.
- `ClusterProjectionPlanner` owns independent-window geometry and duplicate-mode crop-fill plans as
  pure Kotlin logic covered by unit tests.
- `ClusterProjectionDisplayGateway` owns helper, app `DisplayManager`, and `MediaProjection` display
  creation/release plus projection permission grants.
- `ClusterProjectionOverlayController` owns the cluster `WindowManager` view, `SurfaceView`
  lifecycle, make-before-break replacement, and unexpected surface-loss notification.
- `AutomationRuleRepository` and `AutomationJournalRepository` now own automation persistence;
  `AutomationViewModel`, preset seeding, `AutomationConfigRepository`, and `AutomationEngine` no
  longer import Room DAOs directly. `verifySourceHealth` enforces this boundary.
- `AutomationActionPicker` now owns the grouped add-action menu, overlay-permission prompt, and
  default delay/volume action creation; `AutomationActionEditor` remains focused on editing an
  existing action row.
- `AutomationTriggerEditor` now owns trigger-row dispatch and compact parameter/event controls;
  `AutomationScheduleTriggerEditor` owns time and weekday editing, while `AutomationTriggerPicker`
  owns trigger creation and the grouped add-condition menu.
- `AutomationSystemActionEditor` now contains only notification and app-launch controls. Call,
  navigation, and URL forms live in dedicated action editor files with independent size ratchets.
- `OfflineLicenseStore` now owns offline-license cache files and preference persistence, while
  `OfflineLicenseVerifier` owns canonical payload construction, device matching, expiry checks, and
  RS256 signature verification. `ActivationManager` remains the public authorization facade and no
  longer contains cryptographic or license-file implementation details.
- `ActivationDeviceIdentityProvider` now owns helper-backed VIN reads, short retry/success caching,
  VIN extraction and redacted diagnostics, candidate normalization, and device-alias persistence.
  Server status and offline-license restore both update identity through the same component.
- `ActivationApiClient` now owns activation HTTP transport, authenticated device payloads, status
  and activation requests, and payment order/status parsing. Credential recovery proof construction
  lives in `ActivationCredentialRecoveryProofProvider`, where the sole `ANDROID_ID` read and local
  `HardwareIds` suppression are enforced by `verifySourceHealth` and documented in
  `activation-device-identity-security.md`. `ActivationManager` only coordinates those results with
  entitlement state.
- `ActivationEntitlementStore` now owns trial initialization, refresh eligibility, entitlement
  snapshots, device credentials, server-status persistence, activation-code masking, and offline
  entitlement restoration. `ActivationManager` no longer reads or writes entitlement keys directly,
  and `verifySourceHealth` enforces that boundary.
- Payment checkout now has explicit `hosted` and `qrcode` modes. Custom payment schemes are rendered
  as native ZXing QR codes; only HTTPS hosted checkout enters the payment-only WebView. That WebView
  has no JavaScript bridge, blocks file/content access, mixed content, third-party cookies, pop-ups,
  downloads, SSL errors, unsafe browsing, HTTP downgrade, and cross-origin top-level navigation.
  Required JavaScript and DOM storage capabilities are documented in `payment-webview-security.md`.
- `SettingsActivationCoordinator` now owns activation snapshot loading and the payment event stream,
  including order creation, polling retries, and terminal-state detection.
  `SettingsActivationUiController` owns request generations, stale-request cancellation, payment
  event mapping, and activation-related vehicle-event-sound refreshes; `SettingsViewModel` no longer
  calls `ActivationManager` or interprets payment events directly.
  The authorization card, payment dialog, checkout renderer, and capability summary now live in
  separate files with independent size ratchets. Coordinator tests cover terminal polling and
  transient retry behavior, while existing ViewModel tests retain stale-request cancellation
  coverage. `verifySourceHealth` prevents the direct dependency and legacy flat payment fields from
  returning.
- `SettingsEnergyCoordinator` now owns vehicle energy settings loading, capacity normalization and
  atomic persistence, tariff persistence, missing-cost backfill, full trip cost recalculation, and
  consumption-threshold storage. `SettingsEnergyUiController` owns editable energy drafts, save and
  recalculation status lifetimes, plus theme, wallpaper, and trip-GPS UI updates. Related screen state
  is grouped under `SettingsEnergyUiState` and `SettingsAppPreferencesUiState`.
  `SettingsViewModel` no longer imports `TripRepository` or `HistoryImporter`, maps these states, or
  accesses energy preference keys. Coordinator tests cover normalized atomic capacity saves,
  combined electric/fuel trip recalculation, and missing historical cost backfill.
- `SettingsMaintenanceCoordinator` now owns boot diagnostics formatting, update discovery and cached
  download targets, download progress/error events, log upload result mapping, and backup export/
  restore result mapping. Maintenance state is grouped under `SettingsMaintenanceUiState`; the dead
  `updateStatus` and `chainLog` fields were removed. `SettingsMaintenanceUiController` owns boot-info
  loading, update-download cancellation, and all maintenance UI mapping. `verifySourceHealth`
  prevents `SettingsViewModel` from importing low-level update, backup, or boot-diagnostics services
  and from reclaiming the cancellable download lifecycle.
- `SettingsPreferenceCoordinator` now owns theme, wallpaper, and trip-GPS preference persistence,
  while `SettingsVehicleControlCoordinator` owns controller-series detection and headlight commands.
  Their screen state is grouped under `SettingsAppPreferencesUiState` and
  `SettingsVehicleControlUiState`. `SettingsViewModel` no longer imports the settings repository,
  headlight controller, or car-profile detector directly, and source-health checks keep those
  boundaries in place.
- `SettingsServiceQrCoordinator` now owns service-QR loading, cache fallback, status messages, and
  image update streams. `ServiceQrRepository` enforces HTTPS same-origin image URLs, redirect checks,
  metadata/image size and pixel ceilings, validated payloads, and hashed URL-bound cache names. Compose no
  longer opens network streams or writes cache files directly. The former 499-line service and
  authorization screen is split into authorization content and a dedicated QR surface, both covered
  by size and dependency ratchets.
- Vehicle-event sound state is now grouped under `SettingsVehicleEventSoundUiState`, including the
  persisted config, local choices, helper-sync progress, and structured error status. File scans,
  selection validation, import, deletion, and snapshot construction run on the IO dispatcher inside
  `VehicleEventSoundCoordinator`; `SettingsVehicleUiController` owns sound-state merging, helper sync,
  controller-series loading, and headlight command/readback state. The Compose section no longer
  depends on the whole settings state for each event row. Coordinator tests cover activation denial,
  default local selection, shared-file cleanup, helper failure, and status preservation.
- The 20 FA firmware preset previews remain GIF after a pixel- and timing-exact lossless Animated
  WebP comparison increased their total size by 13%. `app/lint.xml` suppresses `GifUsage` only for
  the exact `ipixel_fa_preset_01..20.gif` pattern. Catalog tests and `verifySourceHealth` enforce the
  one-to-one preset mapping, 96x16 dimensions, complete asset set, total size ceiling, and prohibition
  on unrelated GIF resources. The decision is documented in `fa-led-preview-assets.md`.
- `LyricDisplayViewModel` now owns desktop/instrument lyric settings state, activation checks, panel
  persistence, and external preference synchronization. The Compose screen and desktop-component
  parent no longer instantiate `LyricSettings` or resolve activation dependencies directly. Runtime
  overlay targeting is a pure policy, and transient ADB/helper permission bootstrap failures use a
  bounded retry schedule instead of disabling retries for the rest of the process lifetime.
- FA and Mafu LED runtimes now share `LedVehicleSignalAccumulator` and one tiered sampling-loop
  implementation for core, motion, brake, body, and ADAS signal semantics. Both protocols retain
  their own BLE lifecycle, initialization order, linkage engine, command queue, and program-list
  recovery, while freshness windows and field merging can no longer drift between implementations.
- The external `HelperDaemon` app_process entry is now a 68-line lifecycle shell. Caller
  verification, transaction routing, autoservice reads/writes, projection commands, system
  diagnostics, lock-sound/headlight operations, process locking and lock-sound policies have
  independent owners and size ratchets. Router tests cover every published transaction plus the
  reserved-code fallback, and the virtual-display registry has explicit idempotent-release tests.
- Dashboard presentation models and calendar ranges now have dedicated owners. `DashboardViewModel`
  only coordinates repositories, polling and state updates; deterministic range tests lock the
  existing today, rolling-week, rolling-month, rolling-year and all-history semantics.
- Charging history models, localized month/day grouping and calendar range selection now live
  outside `ChargesViewModel`. The ViewModel no longer owns Android `Context` or date formatting,
  and tests cover both grouping behavior and exact local-calendar boundaries.
- Lyrics are organized as repository orchestration, local-file source, online-source priority,
  provider transport, candidate matching and pure parsing. The public repository API remains
  stable, empty network results remain uncached, and source-health checks keep file/network/JSON
  implementation details out of the repository facade.
- Music playback ownership is split into immutable snapshot/source models, metadata identity rules,
  primary-versus-ADB store arbitration, and the supported-package/provider-ID catalog. Existing
  tests preserve title/artist normalization, duration and URI identity boundaries, primary-source
  freshness, fallback position merging, metadata retention, and Qishui track-ID extraction.
- Home detail screens and the vehicle hero are split into page composition, reusable detail cards,
  status formatting, gauges, vehicle stage and powertrain components. Layout dimensions and
  interactions remain unchanged while each visual owner now has an independent size ratchet.
- Electric sliding-door control is split into orchestration, fail-closed safety policies, verified
  FID/command catalog, helper-backed hardware access, diagnostics, and public result models. The
  controller no longer owns Binder bootstrap, sentinel decoding, raw reads/writes, build metadata,
  or log formatting. Catalog and policy tests lock the two retained doors, command values, vehicle
  FIDs, speed limits, display decisions, and safety-check order; source-health guards prevent those
  responsibilities from returning to the controller.
- BYD energy history now has separate owners for source discovery and processed-file fingerprints,
  availability caching, immutable query plans and cursor mapping, read-only SQLite execution,
  diagnostics, and reader orchestration. Incremental imports retain a strict `start_timestamp > ?`
  watermark and chronological ordering; full imports retain deleted-row filtering and newest-first
  ordering. Tests cover source priority, arbitrary `.db`/`.sqlite` discovery, missing tables,
  schema/count inspection, zero-kilometer records, deletion filtering, and incremental ordering.
- On-device ADB is split into a narrow public capability interface, command whitelist/helper script
  catalog, connection and reconnect manager, authenticated session state machine, socket transport,
  wire codec, RSA-AUTH serializer, and persistent key store. No generic shell execution is exposed
  outside the package, vehicle data still travels through Helper Binder, and tests cover concurrent
  connects, reconnect invalidation, helper spawn safety, command injection rejection, packet framing,
  wire constants, signatures, and daemon-compatible public keys.
- Helper Binder access now separates operation-specific Parcel encoding from
  `HelperBinderTransport`, which exclusively owns service lookup, binder caching, dead-object retry,
  request timeouts, and independent read/command mutex lanes. The public `HelperClient` stays free of
  Android Binder types, while existing fake-Binder tests lock wire values, batch validation,
  projection commands, headlight readback, and retry behavior.
- `VehicleApiImpl` is now a small command facade. `VehicleWriteExecutor` owns allowlist validation,
  helper-status interpretation, bounded CAN readback retries, and error classification;
  `VehicleWriteAuditLogger` owns fail-soft attempt/outcome persistence; and
  `VehicleSeatCommandResolver` owns pure seat alias/level mapping. Existing write/fail-soft tests plus
  direct resolver tests preserve composite command fan-out, audit status values, sentinel handling,
  validated-failure reporting, and adaptive seat-channel behavior.
- Vehicle history synchronization now has a small serialized facade, independent EnergyData and
  DiPlus importers, pure EnergyData mapping/session-enrichment policies, and a post-import cost/GPS
  maintenance owner. EnergyData remains the preferred source, DiPlus remains the fallback, source
  fingerprints advance only after successful imports, and EnergyData IDs plus bounded timestamp
  windows retain the existing deduplication rules. Tests lock failed-watermark retries, successful
  empty imports, SOC containment tolerance, zero-kilometer idle drains, metrics, fuel enrichment,
  and preservation of existing fuel data during dedup merges.
- The fresh-install-only database contract now keeps only exported schema `21.json`; obsolete schema
  20 was removed because it still described the deleted `places` table and no upgrade/migration path
  is supported for this new-project release. Source health also rejects the removed phone-casting,
  A950, electronic-mirror, saved-place, and online-sound packages/tokens across app, tests, schemas,
  Gradle, and server code, and locks the cluster card to its fixed high-contrast font palette.

## Remaining Large-File Disposition

Every Kotlin source at 300 lines or more now has an explicit `verifySourceHealth` ceiling. The
remaining large files fall into bounded, reviewable ownership groups rather than untracked hotspots:

- Protocol/lifecycle state machines: FA and Mafu BLE clients/runtimes, lock-sound PCM/runtime,
  steering-key and notification services, projection/window controllers, the Helper Binder
  operation codec, and the music notification listener. These stay together while their transitions
  or wire surface share one lock, connection, callback, or Binder contract; adding an unrelated
  transport or feature requires extraction instead of raising the ceiling.
- Cohesive Compose surfaces: automation editors, local-sound cards, cluster/desktop-card settings,
  lyric settings, charge editing/statistics, and usage-guide rendering. These are bounded complete
  forms or repeated-item surfaces; new dialogs, catalogs, or reusable controls must live in separate
  files.
- Declarative/security catalogs: automation action definitions/catalog, usage-guide catalog,
  desktop-card models, and the vehicle write allowlist. Keeping each catalog centralized makes
  completeness and security review easier; runtime I/O, mutable lifecycle state, or UI rendering is
  forbidden from moving into these files.

`SettingsViewModel` is now a 166-line stable screen facade over four UI controllers and the service-QR
coordinator. It owns the shared immutable state container, existing public method names, and the
small QR stream bridge only. Activation/payment jobs, energy and preference drafts, maintenance jobs,
vehicle-event sounds, and headlight readback all live behind focused controllers. Source-health gates
cap every controller independently and reject controller-specific state mapping or lifecycle details
if they return to the ViewModel.

## Completion Audit

The commercial-foundation cleanup was fully re-verified on 2026-08-26:

- `verifySourceHealth`: passed, including automatic ceilings for every Kotlin file at 300 lines or
  more and ownership guards for the extracted controllers, transports, executors, and repositories;
- Android JVM tests: 1,178 passed, 0 failed, 0 skipped;
- server security tests: 39 passed;
- fresh-install instrumentation: 1 passed on the API 34 emulator, including schema 21 creation and
  confirmation that the deleted `places` table is absent;
- `lintDebug`, `assembleDebug`, and `assembleRelease`: passed;
- Release R8/resource shrinking: passed;
- APK identity: `com.byd.wjx`, version `2.1.20` (`versionCode 60`);
- APK signature: v2 verified, one `CN=Super Toy Box` signer, RSA 4096;
- Release APK SHA-256:
  `3b01b94fe8d497da2341380adf830c49a1cdbd51b1443b54b05d3a1fd626b596`;
- `git diff --check`: passed.

Future feature work must preserve these ownership boundaries and lower ceilings when a cohesive
extraction becomes possible. A new feature may add focused files and tests, but must not enlarge a
facade, restore a removed feature, or bypass the vehicle, Binder, persistence, and UI boundaries.
