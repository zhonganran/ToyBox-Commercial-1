# Activation VIN Identity Boundary

## Scope

Permanent activation has one identity key: the 17-character vehicle VIN.

- An activation code or verified payment binds a permanent entitlement to one VIN.
- `POST /api/v1/license/status` restores that entitlement whenever the server finds an enabled
  license bound to the submitted VIN.
- Reinstalling the APK, clearing application data, or restoring the vehicle system does not require
  a device token, recovery token, local license file, or signed device proof.
- The client sends VIN, application version, and package name only. The server returns activation
  and trial state, not a reusable activation credential.

The order-specific payment `pollToken` remains in place. It authorizes polling one payment order and
cannot activate another VIN or restore a license.

## Accepted Security Boundary

VIN-only recovery intentionally treats possession of a readable VIN as sufficient to retrieve that
VIN's activation state. The status API returns entitlement state but does not expose the activation
code, payment credentials, or another vehicle's records. Server-side activation-code binding,
payment verification, request throttling, and license enable/disable state remain authoritative.

Client-supplied VIN alias arrays are ignored. The server extracts one valid VIN from `deviceCode` or
`rawDeviceCode`, and all activation, trial, payment, device-registration, and log records use that
single normalized identity.

## ADB Identity Is Separate

The local ADB key restoration mechanism is not an activation credential. It exists only to avoid
repeating the Android debugging authorization prompt on the same vehicle system and signed app.

`AppScopedDeviceBindingProvider` derives a local, purpose-separated binding from the app-scoped
Android ID. `AdbIdentityBackupStore` combines it with the installed signing-certificate digest to
encrypt the ADB RSA identity with AES-GCM before storing the backup in a protected system setting.
The raw Android ID, derived binding, and ADB private key are never submitted to activation APIs.

Factory reset, vendor-setting cleanup, or a signing-certificate change may require ADB authorization
again, but permanent application activation still restores independently from the VIN.

## Enforcement

- Activation transport tests assert that credential, proof, and authorization fields are absent.
- Server schema tests assert that device-token columns do not exist.
- Server workflow tests cover direct VIN restoration after a fresh installation.
- `verifySourceHealth` rejects the removed offline-license store and legacy credential providers.
