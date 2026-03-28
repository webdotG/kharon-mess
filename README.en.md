

# KHARON MESSENGER
```
 ██╗  ██╗██╗  ██╗ █████╗ ██████╗  ██████╗ ███╗  ██╗
 ██║ ██╔╝██║  ██║██╔══██╗██╔══██╗██╔═══██╗████╗ ██║
 █████╔╝ ███████║███████║██████╔╝██║   ██║██╔██╗██║
 ██╔═██╗ ██╔══██║██╔══██║██╔══██╗██║   ██║██║╚████║
 ██║  ██╗██║  ██║██║  ██║██║  ██║╚██████╔╝██║ ╚███║
 ╚═╝  ╚═╝╚═╝  ╚═╝╚═╝  ╚═╝╚═╝  ╚═╝ ╚═════╝ ╚═╝  ╚══╝
 ──────────────────── MESSENGER v1.0 ─────────────────
```

[![License: MIT](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)
[![Android](https://img.shields.io/badge/Android-12%2B-brightgreen)](android/)
[![F-Droid](https://img.shields.io/badge/F--Droid-available-blue)](https://webdotg.github.io/kharon-fdroid/repo)
[![Self-hosted](https://img.shields.io/badge/server-self--hosted-orange)](server/)

> Ephemeral. Encrypted. Zero-knowledge. Self-hosted.

**Read in other languages:** [Русский](README.md) · [中文](README.zh.md) · [العربية](README.ar.md)

---

## What is this?

Kharon is a minimalist encrypted messenger for small teams. Messages live for 2 minutes, then vanish forever. No accounts, no phone numbers, no message history, no servers you don't own.

Named after Charon — the ferryman of the dead in Greek mythology. What crosses the river does not return.

---

## Install via F-Droid

1. Install [F-Droid](https://f-droid.org)
2. Settings → Repositories → **+**
3. Add: `https://webdotg.github.io/kharon-fdroid/repo`
4. Search **Kharon Messenger** → Install

---

## Philosophy

Most messengers store your messages on someone else's server, know who you are, and can be compelled to hand over your data. Kharon is built on the opposite principle:

- The server is a **dumb relay** — it routes encrypted blobs and knows nothing else
- **You run the server** — on your own hardware, in your own home
- Messages **self-destruct** in 2 minutes — no history by design
- **No accounts** — your identity is a cryptographic key pair, nothing more
- Adding a contact = exchanging public keys directly, offline or via copy-paste

---

## How a message travels

```
[Your phone]
  1. Type message
  2. Encrypt with recipient's public key (X25519 + XSalsa20-Poly1305)
  3. Random 24-byte nonce prepended to ciphertext
  4. Send base64 blob over WSS

       ↓  TLS 1.3  ↓

[Nginx]
  5. TLS termination, rate limiting, WebSocket validation

[Node.js relay]
  6. Receives encrypted blob — NEVER sees plaintext
  7. Routes to recipient's WebSocket connection
  8. If offline: stores in RAM queue (max 2 min TTL)

       ↓  TLS 1.3  ↓

[Recipient's phone]
  9. Receives blob, decrypts with own secret key
  10. Displays plaintext — stored in RAM only, never written to disk
```

---

## Security architecture

### Cryptography

| What | Algorithm | Why |
|---|---|---|
| Key exchange | X25519 (Curve25519 DH) | Fast, secure, widely audited |
| Encryption | XSalsa20-Poly1305 | Authenticated encryption — detects tampering |
| Nonce | 24 random bytes per message | 192-bit entropy, collision probability ≈ 0 |
| Key storage | Android Keystore + EncryptedSharedPreferences | Hardware-backed, cannot be extracted |
| Contact storage | Room + SQLCipher | Database encrypted at rest |

### What the server knows

| Data | Server sees? |
|---|---|
| Message content | ❌ Never — only encrypted blob |
| Sender identity | ❌ Only a public key (44 chars base64) |
| Message history | ❌ TTL 2 min, then deleted from RAM |
| Your name | ❌ No registration |
| Your phone number | ❌ Not required |
| Your IP address | ⚠️ Yes, in connection logs |

### Defense layers

**Server:** TLS 1.3 only · rate limiting · connection limits · hello timeout · token bucket · MAX_CLIENTS cap · payload validation

**Client:** WSS only · certificate pinning · exponential backoff · FLAG_SECURE · allowBackup=false · root detection · replay attack deduplication

---

## What Kharon does NOT protect against

- **Physical device seizure** — current session is in RAM; past sessions are already gone
- **Compromised server operator** — they can see connection metadata but not message content
- **Rooted device** — Android Keystore can potentially be bypassed
- **Traffic analysis** — an observer can see that you connect to your server, but not what you say
- **Endpoint malware** — no messenger can protect against screen capture malware

---

## Server deployment

### Requirements
- Docker + docker-compose
- Domain or static IP
- TLS certificate (Let's Encrypt)

### Quick start

```bash
git clone https://github.com/webdotG/kharon-mess
cd kharon-mess/server

cp .env.example .env

# Get TLS certificate
certbot certonly --standalone -d yourdomain.com
mkdir certs
cp /etc/letsencrypt/live/yourdomain.com/fullchain.pem certs/
cp /etc/letsencrypt/live/yourdomain.com/privkey.pem certs/

docker compose up -d
```

### Environment variables

| Variable | Default | Description |
|---|---|---|
| `MSG_TTL_MS` | `120000` | Message lifetime in ms (2 min) |
| `MAX_MSG_SIZE` | `4096` | Max payload size in bytes |
| `MAX_QUEUE_PER_USER` | `50` | Max offline queue per user |
| `MAX_CONNS_PER_IP` | `3` | Max simultaneous connections per IP |
| `MAX_CLIENTS` | `100` | Max unique connected users |
| `MSG_RATE_LIMIT` | `3` | Messages per second per connection |

---

## Android client

### Building

```bash
cd android
KEYSTORE_PATH=/path/to/keystore.jks \
KEYSTORE_PASS=yourpass \
KEY_ALIAS=youralias \
KEY_PASS=yourpass \
./gradlew assembleRelease
```

### Certificate pinning

```bash
openssl s_client -connect yourdomain.com:443 2>/dev/null \
  | openssl x509 -pubkey -noout \
  | openssl pkey -pubin -outform DER \
  | openssl dgst -sha256 -binary \
  | base64
```

Replace the hash in `KharonSocket.kt`:
```kotlin
.add("yourdomain.com", "sha256/YOUR_HASH_HERE")
```

### Adding a contact

Two methods:
1. **Copy/paste key** — Add Contact → My Key → copy → send via any channel → colleague pastes in Add Contact → Enter Key
2. **QR code** — show your QR, colleague scans

---

## UI Themes

| Theme | Style |
|---|---|
| Default | Clean dark interface |
| Terminal Dark | Green on black — Matrix/MikroTik style |
| Terminal Light | Dark green on white — DEC terminal style |
| Princess | Soft pink/purple/blue — cute & sweet |

---

## Project structure

```
kharon-mess/
├── server/          # Node.js WSS relay
│   ├── index.js     # WebSocket server, message handling
│   ├── hub.js       # Connection manager, TTL sweep
│   ├── nginx/       # Nginx config
│   └── docker-compose.yml
└── android/         # Kotlin + Jetpack Compose client
    └── app/src/main/java/com/kharon/messenger/
        ├── crypto/      # X25519 + XSalsa20-Poly1305
        ├── network/     # WebSocket + cert pinning
        ├── storage/     # Room + SQLCipher
        └── ui/          # Screens + 4 themes
```

---

## Roadmap

- [ ] Push notifications (FCM / UnifiedPush)
- [ ] iOS client
- [ ] Forward Secrecy (Double Ratchet)
- [ ] Group chats
- [ ] Voice messages
- [ ] Message deletion before TTL
- [ ] Pinned messages (max 24h)
- [ ] Server federation
- [ ] Tor/I2P support
- [ ] Reproducible builds
- [ ] GitHub Actions CI

---

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md). Pull requests are welcome.

For security vulnerabilities, see [SECURITY.md](SECURITY.md) — do **not** open a public issue.

---

## License

MIT — run your own instance, fork it, contribute back.

---

## Why "Kharon"?

Charon (Χάρων) was the ferryman of Hades who carried souls across the rivers Styx and Acheron. He took what was given, transported it across, and that was the end. No record. No return.

That's what this messenger does.
