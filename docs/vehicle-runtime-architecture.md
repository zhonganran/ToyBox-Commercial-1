# Vehicle Runtime Architecture

This document records the production runtime boundaries introduced by the vehicle gateway
refactor. New vehicle features should follow these ownership rules instead of reading static
service fields or issuing ad-hoc ADB commands.

## Read Path

1. `HelperBootstrap` uses on-device ADB only to start or replace the shell-uid helper process. A
   versioned ping upgrades a stale helper once after APK replacement without treating an empty VIN
   or a transient vehicle read as a restart signal.
2. `HelperProcessGate` prevents helper replacement from racing active Binder transactions.
3. `VehicleGateway` uses direct Helper Binder batch reads as the only production vehicle-read
   transport. A failed batch remains a failed sample and is retried by the polling loop; it never
   triggers a second ADB vehicle-read path.
4. `NativeParsReader` decodes full and action snapshots.
5. `SharedAdaptiveLoop` is the only polling owner and publishes snapshots.
6. `VehicleStateRepository` is the canonical process-level vehicle state consumed by UI and
   business features.

The normal and action streams have one polling owner. Direct reads are limited to bounded,
explicit exceptions:

- the tiered LED signal loops run only while LED control is enabled, connected and initialized;
- the home screen owns one serialized on-demand loop: ADAS every 2 seconds and tire
  temperature/status every 10 seconds; leaving home cancels it;
- the vehicle-data screen reads the eight powertrain diagnostic FIDs every 2 seconds and cancels
  the profile immediately when the page leaves composition;
- charging catch-up performs event-driven battery/charging reads at startup, retry and gun edges;
- battery health and local insight screens may perform a one-shot refresh when no shared snapshot
  is available.

Do not add feature-owned periodic vehicle-read loops. New live consumers subscribe to
`VehicleStateRepository`; a genuinely faster hardware signal must be added as an explicit polling
profile with a documented enable/disable condition.

ADB remains available for narrow runtime jobs that actually require shell privileges, including
helper bootstrap, accessibility/appops setup and media-session diagnostics. It must not be used to
read vehicle FIDs.

## Cadence

- Action snapshot: 300 ms while driving/parked, 1 second while charging/idle.
- Driving full snapshot: 1 second.
- Charging and parked full snapshot: 3 seconds.
- Idle full snapshot: 30 seconds.
- The selectable production catalog contains 117 FID addresses for 115 semantic fields. The first successful regular full snapshot
  reads only 73 so every dashboard/trip consumer receives a complete initial state, including SOH, without
  paying for the 44 feature-specific fields that are on-demand.
- Later full snapshots refresh the 17 lifecycle-critical core FIDs at the regular cadence. The
  other 56 detail FIDs are eligible for refresh every 3 seconds and are merged from the reader's
  raw-value cache between refreshes. In 30-second idle mode they refresh on the next regular tick.
- A failed detail refresh clears the cached detail values to unavailable instead of presenting
  stale data indefinitely. A failed core batch fails the whole sample and enters normal backoff.
- An action snapshot that observes a cadence-state transition forces an immediate full snapshot,
  so ignition or charging wake-up never waits for the previous 30-second idle timer.
- The adaptive action batch always contains only four wake fields: speed, gear, charge-gun state
  and power state. Parameter fields are added from currently enabled automation rules; automatic
  parking status is added only while its sound feature or an automation rule needs it. LED linkage
  no longer injects door or ADAS fields into this 300 ms shared batch.
- All 44 AC/seat/tire/light/driving/ADAS/powertrain reads are absent from both regular core and
  detail profiles. Most enter the action batch only while an enabled rule references them. Home
  serializes an eight-FID ADAS profile every 2 seconds and an eight-FID tire temperature/status
  profile every 10 seconds. The vehicle-data page owns the eight-FID powertrain profile at 2 seconds.
  LED reads are split by response requirement and run only while linkage is enabled, connected and
  initialized: stable turn enum + gear at 100 ms, brake depth + actual brake lamp at 200 ms, four
  doors + automatic parking at 500 ms, and six assisted-driving state fields at 1 second. This caps
  the dedicated LED budget at roughly 46 FID reads per second instead of the previous 80. Turn
  scenes use no blinking-lamp fallback; reverse parking is driven by reverse gear or active
  automatic parking. ACC mode uses raw states 3/4/6/7, LCC/NCA and MNOA use raw state 2, and
  execution speed remains diagnostic telemetry rather than an independent proof that automation is
  active. Active automatic parking owns the reverse-parking scene and suppresses the ordinary
  reverse-gear branch until parking finishes.
- LED temporary-stop latches after a powered non-P to P transition and clears only after leaving P.
  Exit-vehicle latches when any of the four doors opens in P or when power transitions to OFF; it
  clears after leaving P or on the next power-up instead of expiring on a fixed timer.
- Every first connection or reconnection resets the LED arbiter and sends the default smile before
  current vehicle conditions may select another scene. When no scene condition remains, the same
  default smile is always restored.
- Charging current/power, BMS maximum charge/discharge power, insulation resistance and stop SOC
  belong to a separate UI-owned telemetry path. The ordinary catch-up snapshot does not read them;
  the charging page first reads its base metadata and adds the six diagnostic FIDs only after AC/DC
  gun or BMS charging state is active. Leaving the page cancels the poller immediately.
- Decoded BYD enums remain in their official encoding through `DiParsData`. Shared predicates in
  `BydSignalSemantics` own power, charging, cover, lock and trunk interpretation; UI and state
  machines must not create local remapped 0/1/2 variants.

## Runtime State

`TrackingStateRepository` owns service, range, session, trip-distance, location and camera state.
Consumers must not depend on `TrackingService` companion fields.

`TrackingService` is an Android lifecycle orchestrator. Business state machines live in:

- `EnergyFlowStatsStore` / `EnergyFlowAccumulator` for one-second recovery classification,
  in-memory integration and batched 30-second persistence.

- `DriveSessionCoordinator`
- `DrivingMetricsCoordinator`
- `ChargingCatchUpCoordinator`
- `AutomationConfigRepository`

Android runtime integrations live in `service/runtime`:

- `TrackingLocationCoordinator`
- `TrackingNotificationController`
- `CameraRuntimeCoordinator`
- `SteeringWheelRuntimeCoordinator`

## Startup Ownership

`BootReceiver`, `MainActivity`, alarm revival and the WorkManager fallback may request
`KeepAliveService`; they do not start individual feature services. `KeepAliveService` is the only
app-side watchdog and wake-lock owner, while `DaemonStartupManager` owns the ordered startup of
tracking and desktop overlays. Lock/unlock/sentry monitoring remains inside the shell-uid helper;
the app synchronizes its configuration on startup, setting changes and low-frequency health checks.
WorkManager is an exception path used only when the direct foreground-service request throws.
Health checks ping the Helper Binder first. On-device ADB is opened only when `HelperBootstrap`
must spawn or replace the Helper, never on every watchdog interval.

## Write Path

All vehicle writes continue through `VehicleApi`, `WriteAllowlist` and Helper Binder. UI,
automation and overlays must not call autoservice or shell commands directly.

## Performance Rules

- Do not query Room from the adaptive action path.
- Do not persist unchanged state on every vehicle tick.
- `last_state` is a recovery checkpoint. Meaningful SOC, ignition, storage-mode and vehicle-value
  changes persist immediately; otherwise writes are coalesced behind a short heartbeat.
- Keep action consumers sequential so a slow operation cannot create an unbounded job queue.
- Add new FIDs to the appropriate snapshot map and consume them from the repository snapshot.
- Keep optional vehicle-model and Android-platform fallbacks outside the production vehicle-read path.

## Process Initialization

- osmdroid is initialized on the first map view, not from `Application.onCreate`. Background-only
  vehicle sessions therefore do not load map preferences or touch the tile cache.
- Replaceable asynchronous operations must keep one explicit latest owner. The network reachability
  monitor uses generation ownership so completion of an old cancelled probe cannot clear the state
  of a newer probe.

## UI and Feature Boundaries

- Large Compose pages are split by feature section. Page entry files own navigation, observable
  state and side-effect callbacks; extracted panels render state and emit actions without starting
  services or interpreting persistence keys.
- `SettingsScreen.kt` composes display, shortcut, authorization, vehicle-sound and app-maintenance
  sections. `SettingsViewModel` delegates logcat lifecycle to `SettingsLogRecorder`.
- `LyricDisplayScreen.kt` owns the desktop and instrument-cluster lyric state plus service
  callbacks. `LyricDisplaySettingsPanels.kt` contains lyric settings UI, while
  `LyricDisplayControls.kt` owns lyric previews and shared controls.
- `SoundScreen.kt` is the page shell. Local-library and online-catalog behavior live in
  `SoundLocalTab.kt` and `SoundOnlineTab.kt`.
- Android Services remain lifecycle shells. Realtime hailing capture/playback lives in
  `RealtimeHailingEngine`, while sample conversion and framing live in `HailingAudioProcessor`.

## Preference and Diagnostic Ownership

- `BackupPreferencesRegistry.FILE_POLICIES` is the canonical inventory of every preference file,
  its single schema owner, backup policy and credential/device-protected storage scope. New files
  must be registered there before release.
- UI code must use typed owners such as `ClusterProjectionPreferences`,
  `LyricDisplayPanelPreferences`, `AutomationPreferences` and `ServiceQrCache`; it must not copy
  preference keys or defaults.
- `BootDiagnosticsStore` is the only owner of the device-protected `boot_log` schema. BootReceiver
  writes through it, ChainLog performs synchronized bounded appends through it, and Settings reads
  the same storage context through it.
- `SettingsLogRecorder` is the only owner of the diagnostic logcat process and pipe. It stops on
  explicit user action, ViewModel disposal, the two-hour duration limit or the file-size limit.

## Android Platform Floor

The production APK has `minSdk = 29`. Code must use the Android 10 notification, overlay,
device-protected storage and audio-focus APIs directly instead of carrying unreachable pre-29
branches. This does not authorize replacing BYD private APIs or fixed vehicle storage paths.

## Dependency Injection Boundaries

- `DatabaseModule` owns Room construction and DAO exposure.
- `AppBindingsModule` owns interface-to-implementation bindings.
- `AppModule` is limited to process scope and runtime objects that require explicit factories or
  lambda configuration.
- Framework-created Hilt services must inject process singletons instead of constructing a second
  instance manually.
- SharedPreferences schemas with multiple runtime consumers use a single typed owner. Cluster
  projection reads one `ClusterProjectionPreferenceSnapshot`, and grouped geometry writes are
  committed through `ClusterProjectionPreferences` rather than duplicating keys and defaults.
- The helper Binder entry stays in `HelperDaemon.kt`; shell execution, task/display reflection,
  VIN platform access and helper-side file logging live in `HelperDaemonPlatform.kt`. Protocol
  changes must not be hidden inside platform utility code.
- Compressed lock-sound decoding and PCM sample-format conversion live in `HelperPcmDecoder`.
  Decoder utilities must not acquire vehicle or audio-route state.
- `HelperLockSoundRuntime.kt` owns configuration plus lock, unlock and sentry detection. It decides
  when an event should start or stop, but does not own Android playback objects.
- `HelperLockSoundPlaybackEngine.kt` owns playback tokens, PCM/MediaPlayer fallback and the
  AudioTrack/MediaPlayer lifecycle. Monitoring code must call this engine instead of manipulating
  audio resources directly.
- `ExteriorRoutePlan.kt` is the process-neutral source of truth for channel A/B arm and disarm FID
  order. It contains no lock, unlock or sentry event logic. `BydExteriorAudioRouter` executes this
  plan through `HelperClient`, while `HelperExteriorRouteController.kt` executes the same plan
  through the helper Binder transport and owns helper-side speaker acquisition/release.
- `BydLockSoundPlaybackState.kt` is the single BYD audio-manager reflection adapter used by both
  route executors. Reflection class candidates must not be duplicated in either executor.
- `HelperLockSoundSignalReader.kt` owns helper-side vehicle FID candidates, BYD SDK reflection,
  sentry system properties and sentry log cursors. The event state machine consumes normalized
  lock, door, flash, power and sentry readings instead of carrying platform fallbacks itself.
- `HelperLockSoundWatchdog.kt` owns enable/disable markers, watchdog script versioning and helper
  restart installation. Runtime configuration code must not manage watchdog files or processes
  directly.
- `HelperDaemon.kt` remains the app_process/Binder entry only. Keeping the entry class small
  protects the externally referenced `com.byd.wjx.helper.HelperDaemon` launch name from feature
  churn.
