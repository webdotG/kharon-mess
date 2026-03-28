# Security Policy

## Reporting a vulnerability

**Do NOT open a public GitHub issue for security vulnerabilities.**

Send details to: open an issue marked `[security]` with limited details, then we will contact you privately.

Or contact via Kharon Messenger itself — add the maintainer's public key (listed in releases).

## What we consider a security issue

- Cryptographic weaknesses in E2E implementation
- Server-side vulnerabilities that could expose metadata
- Certificate pinning bypass
- Key extraction from Android Keystore
- Any issue that could de-anonymize users

## What we do NOT consider a security issue

- The server operator can see connection frequency and IP addresses — this is by design and documented
- Physical device access — if someone has your unlocked phone, no messenger protects you
- Root detection bypass — root detection is a warning, not a hard block

## Supported versions

Only the latest release receives security fixes.

## Disclosure policy

We aim to acknowledge reports within 48 hours and release a fix within 14 days for critical issues.
