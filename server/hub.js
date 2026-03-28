'use strict'

const MSG_TTL_MS        = parseInt(process.env.MSG_TTL_MS         ?? '120000')
const MAX_MSG_SIZE      = parseInt(process.env.MAX_MSG_SIZE       ?? '4096')
const MAX_QUEUE         = parseInt(process.env.MAX_QUEUE_PER_USER ?? '50')
const MAX_CONNS_PER_IP  = parseInt(process.env.MAX_CONNS_PER_IP   ?? '3')
const MAX_CLIENTS       = parseInt(process.env.MAX_CLIENTS        ?? '100')
const SWEEP_INTERVAL_MS = parseInt(process.env.SWEEP_INTERVAL_MS  ?? '30000')

// X25519 pubkey = 32 bytes = 44 base64 chars
function isValidPubKey(key) {
  return typeof key === 'string'
    && key.length === 44
    && /^[A-Za-z0-9+/]{43}=$/.test(key)
}

function isValidPayload(payload) {
  return typeof payload === 'string'
    && payload.length > 0
    && payload.length <= MAX_MSG_SIZE
    && /^[A-Za-z0-9+/]+=*$/.test(payload)
}

class Hub {
  constructor() {
    this.clients       = new Map() // pubKey → WebSocket
    this.queues        = new Map() // pubKey → [{ id, from, payload, expiresAt }]
    this.ipConnections = new Map() // ip → connection count
    this.stats         = { delivered: 0, expired: 0, rejected: 0, blocked: 0 }

    // Single sweep interval instead of per-message timers
    this._sweepTimer = setInterval(() => this._sweep(), SWEEP_INTERVAL_MS)
    this._statsTimer = setInterval(() => this._logStats(), 5 * 60 * 1000)
  }

  register(pubKey, ws, ip) {
    if (!isValidPubKey(pubKey)) return { ok: false, reason: 'invalid_pubKey' }

    const ipCount = this.ipConnections.get(ip) ?? 0
    if (ipCount >= MAX_CONNS_PER_IP) {
      this.stats.blocked++
      return { ok: false, reason: 'too_many_connections_from_ip' }
    }

    const isNewClient = !this.clients.has(pubKey)
    if (isNewClient && this.clients.size >= MAX_CLIENTS) {
      this.stats.blocked++
      return { ok: false, reason: 'server_full' }
    }

    // Evict stale connection if same key reconnects
    const existing = this.clients.get(pubKey)
    if (existing && existing !== ws) {
      existing.close(1008, 'replaced')
      this._decrementIp(existing._remoteIp)
    }

    ws._remoteIp = ip
    this.clients.set(pubKey, ws)
    this.ipConnections.set(ip, ipCount + 1)
    console.log(`[hub] connect  peers=${this.clients.size}`)

    this._flushQueue(pubKey, ws)
    return { ok: true }
  }

  unregister(pubKey, ip) {
    this.clients.delete(pubKey)
    this._decrementIp(ip)
    console.log(`[hub] disconnect  peers=${this.clients.size}`)
  }

  route(fromKey, toKey, payload, id) {
    if (!isValidPubKey(toKey))    return { ok: false, reason: 'invalid_recipient' }
    if (!isValidPayload(payload)) return { ok: false, reason: 'invalid_payload' }
    if (fromKey === toKey)        return { ok: false, reason: 'self_send' }

    const msgId = (typeof id === 'string' && id.length > 0 && id.length < 64)
      ? id
      : this._generateId()

    const envelope = { type: 'msg', from: fromKey, payload, id: msgId }
    const recipientWs = this.clients.get(toKey)

    if (recipientWs && recipientWs.readyState === 1) {
      this._send(recipientWs, envelope)
      this.stats.delivered++
    } else {
      this._enqueue(toKey, envelope)
    }

    return { ok: true, id: msgId }
  }

  _enqueue(pubKey, envelope) {
    if (!this.queues.has(pubKey)) this.queues.set(pubKey, [])
    const queue = this.queues.get(pubKey)
    if (queue.length >= MAX_QUEUE) { this.stats.rejected++; return }
    queue.push({ ...envelope, expiresAt: Date.now() + MSG_TTL_MS })
  }

  _flushQueue(pubKey, ws) {
    const queue = this.queues.get(pubKey)
    if (!queue?.length) return
    const now = Date.now()
    for (const msg of queue) {
      if (msg.expiresAt <= now) { this.stats.expired++; continue }
      const { expiresAt, ...envelope } = msg
      this._send(ws, envelope)
      this.stats.delivered++
    }
    this.queues.delete(pubKey)
  }

  // Sweep runs once per interval — no per-message timers
  // Messages expire at expiresAt timestamp, max ~30s late
  _sweep() {
    const now = Date.now()
    let expired = 0
    for (const [pubKey, queue] of this.queues) {
      const alive = queue.filter(m => m.expiresAt > now)
      expired += queue.length - alive.length
      alive.length ? this.queues.set(pubKey, alive) : this.queues.delete(pubKey)
    }
    if (expired > 0) {
      this.stats.expired += expired
      console.log(`[hub] sweep  expired=${expired}  queues=${this.queues.size}`)
    }
  }

  _decrementIp(ip) {
    if (!ip) return
    const count = this.ipConnections.get(ip) ?? 0
    count <= 1 ? this.ipConnections.delete(ip) : this.ipConnections.set(ip, count - 1)
  }

  _send(ws, data) {
    try { ws.send(JSON.stringify(data)) }
    catch (e) { console.error('[hub] send error:', e.message) }
  }

  _generateId() {
    return Math.random().toString(36).slice(2) + Date.now().toString(36)
  }

  _logStats() {
    const { delivered, expired, rejected, blocked } = this.stats
    console.log(`[hub] stats  peers=${this.clients.size}  delivered=${delivered}  expired=${expired}  rejected=${rejected}  blocked=${blocked}`)
  }

  destroy() {
    clearInterval(this._sweepTimer)
    clearInterval(this._statsTimer)
  }
}

module.exports = new Hub()
