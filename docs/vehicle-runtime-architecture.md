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

ADB remains available for narrow runtime jobs that actually require shell privileges, including
helper bootstrap, accessibility/appops setup and media-session diagnostics. It must not be used to
read vehicle FIDs.

## Cadence

- Action snapshot: 300 ms.
- Driving full snapshot: 1 second.
- Charging and parked full snapshot: 3 seconds.
- Idle full snapshot: 30 seconds.
- An action snapshot that observes a cadence-state transition forces an immediate full snapshot,
  so ignition or charging wake-up never waits for the previous 30-second idle timer.
- The 300 ms batch always contains only the cadence/safety core fields. Parameter fields are added
  from the currently enabled automation rules, so an unused temperature, tire or window trigger
  does not consume autoservice bandwidth on every tick.

## Runtime State

`TrackingStateRepository` owns service, range, session, trip-distance, location and camera state.
Consumers must not depend on `TrackingService` companion fields.

`TrackingService` is an Android lifecycle orchestrator. Business state machines live in:

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

- Do not query Room from the 300 ms action path.
- Do not persist unchanged state on every vehicle tick.
- Keep action consumers sequential so a slow operation cannot create an unbounded job queue.
- Add new FIDs to the appropriate snapshot map and consume them from the repository snapshot.
- Keep optional vehicle-model and Android-platform fallbacks outside the production vehicle-read path.
