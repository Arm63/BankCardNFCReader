# Security Policy

## Reporting a vulnerability

Email **Armenasatryan1996@gmail.com** with subject line `SECURITY: BankCardNFCReader`.

Do **not** open public GitHub issues for security reports.

Include:

- Affected library version (e.g. `1.1.4`)
- Android version and device model used to reproduce
- Reproduction steps
- Impact assessment (data exposure, RCE, denial of service, etc.)
- Suggested fix if you have one

## Response SLA

- Acknowledgement: within 7 days of report
- Triage and severity assessment: within 14 days
- Patch released: depends on severity, target 30 days for high/critical

## Supported versions

| Version | Supported |
|---|---|
| `1.1.x` | yes |
| `1.0.x` | no |
| older | no |

## Disclosure

Coordinated disclosure preferred. Please give the maintainer reasonable time to release a fix before publishing details. Public credit will be given to the reporter unless they request anonymity.

## Scope

In scope:

- The `android-bank-card-reader` Gradle module
- Published artifact `com.github.Arm63:BankCardNFCReader`

Out of scope:

- The sample `app` module (demo only)
- Third-party dependencies (report upstream: AndroidX, kotlinx.coroutines)
- Issues that require physical device access plus a victim card AND custom-built malicious card emulator (acknowledged but lower priority)

## Hardening notes for integrators

- The library is read-only and never persists card data. Whatever your application does with the returned PAN/DPAN is your PCI-DSS scope.
- Do not log PAN or cardholder name in production builds.
- DPAN values returned from digital wallets (Google Wallet, Apple Pay, Samsung Pay) are device-bound tokens, not the underlying card number, but they should still be treated as sensitive.
