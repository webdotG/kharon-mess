# Changelog

All notable changes to this project will be documented in this file.
Format based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

## [1.0.0] - 2026-03-28

### Added
- E2E encryption: X25519 key exchange + XSalsa20-Poly1305
- Node.js WSS relay server with Docker + Nginx
- Android client (Kotlin + Jetpack Compose, min SDK 31)
- Certificate pinning in OkHttp
- Token bucket rate limiting (server + client)
- 4 UI themes: Default, Terminal Dark, Terminal Light, Princess
- Font size selector (Small / Medium / Large)
- Contact management via public key exchange or QR code
- SQLCipher encrypted local database
- Android Keystore for key storage
- FLAG_SECURE (no screenshots, hidden in task switcher)
- Root detection warning
- Replay attack protection (message ID deduplication)
- F-Droid repository at https://webdotg.github.io/kharon-fdroid/repo
- Self-hosted deployment via Docker Compose

### Security
- Messages stored in RAM only, TTL 2 minutes
- Server never sees plaintext
- No accounts, no phone numbers, no persistent history
