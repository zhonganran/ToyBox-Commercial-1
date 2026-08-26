# Activation Device Identity Security

## Scope

Activation uses three separate identity and authentication layers:

- The vehicle VIN is the license subject and determines which vehicle owns an entitlement.
- The random server-issued device token is the primary API credential after registration.
- The credential recovery proof is a reinstall recovery mechanism, not an advertising identifier,
  analytics identifier, account identifier, or replacement for VIN authorization.

## Recovery proof construction

`ActivationCredentialRecoveryProofProvider` is the only production source allowed to read
`Settings.Secure.ANDROID_ID`. It constructs:

```text
SHA-256("wjx-device-credential-v1|<package name>|<ANDROID_ID>")
```

The purpose prefix prevents reuse as a general Android ID hash. Including the package name keeps
proofs separate between application IDs. A blank platform value produces no proof.

The raw Android ID is never uploaded, persisted by the app, or written to application logs. Only
the 64-character lowercase SHA-256 proof is placed in activation request bodies over HTTPS. The
server hashes that proof again with its own purpose prefix before storing
`credential_recovery_hash`; it does not store the client proof verbatim.

## Authorization boundaries

A matching recovery proof may rotate a lost device token for a previously registered,
unactivated vehicle. For an activated vehicle, recovery also requires the signed offline-license
device proof. VIN matching and the server-side entitlement remain authoritative; possession of a
recovery proof does not create or transfer a license.

The proof is stable only while Android returns the same app-scoped value. It may change after a
factory reset, for another Android user, or after an application-signing identity change. Those
events intentionally fail closed and require another supported activation or service path.

## Enforcement

- `HardwareIds` is suppressed only on the private `readAndroidId` method.
- `verifySourceHealth` rejects any other Android ID read or `HardwareIds` suppression.
- Unit tests pin the derivation, blank-value behavior, and package/device separation.
- `ActivationApiClient` accepts an injected proof provider, so transport tests never access a real
  hardware identifier.
