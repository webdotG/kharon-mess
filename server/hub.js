'use strict'

const crypto = require('crypto')

const MSG_TTL_MS        = parseInt(process.env.MSG_TTL_MS         ?? '120000')
const MAX_MSG_SIZE      = parseInt(process.env.MAX_MSG_SIZE       ?? '4096')
const MAX_QUEUE         = parseInt(process.env.MAX_QUEUE_PER_USER ?? '50')
const MAX_CONNS_PER_IP  = parseInt(process.env.MAX_CONNS_PER_IP   ?? '3')
const MAX_CLIENTS       = parseInt(process.env.MAX_CLIENTS        ?? '100')
const SWEEP_INTERVAL_MS = parseInt(process.env.SWEEP_INTERVAL_MS  ?? '30000')

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

function isValidMsgId(id) {
  return typeof id === 'string' && id.length >= 8 && id.length <= 64
}

// Парсим режим в минуты
function parseModeMinutes(mode) {
  if (!mode || mode === 'LIVE' || mode === 'SILENT') return 0
  const match = mode.match(/PULSE_(\d+)/)
  return match ? parseInt(match[1]) : 0
}

class Hub {
  constructor() {
    this.clients       = new Map()
    this.queues        = new Map()
    this.ipConnections = new Map()
    this.lastKnownMode = new Map() // pubKey -> minutes
    this.stats         = { delivered: 0, expired: 0, rejected: 0, blocked: 0 }

    this._sweepTimer = setInterval(() => this._sweep(), SWEEP_INTERVAL_MS)
    this._statsTimer = setInterval(() => this._logStats(), 5 * 60 * 1000)
  }

  register(pubKey, ws, ip, mode = 'LIVE') {
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

    const existing = this.clients.get(pubKey)
    if (existing && existing !== ws) {
      existing.close(1008, 'replaced')
      this._decrementIp(existing._remoteIp)
    }

    ws._remoteIp = ip
    ws._receptionMode = mode

    // Запоминаем режим получателя
    this.lastKnownMode.set(pubKey, parseModeMinutes(mode))

    this.clients.set(pubKey, ws)
    this.ipConnections.set(ip, ipCount + 1)
    console.log(`[hub] connect  peers=${this.clients.size} mode=${mode}`)

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

    const msgId = isValidMsgId(id) ? id : this._generateId()
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

  cancel(fromKey, toKey, msgId) {
    if (!isValidPubKey(toKey))  return { ok: false, reason: 'invalid_recipient' }
    if (!isValidMsgId(msgId))   return { ok: false, reason: 'invalid_msgId' }

    const queue = this.queues.get(toKey)
    if (!queue || queue.length === 0) return { ok: false, reason: 'not_in_queue' }

    const before   = queue.length
    const filtered = queue.filter(m => !(m.id === msgId && m.from === fromKey))
    if (filtered.length === before) return { ok: false, reason: 'msg_not_found' }

    filtered.length ? this.queues.set(toKey, filtered) : this.queues.delete(toKey)
    console.log(`[hub] cancel  msgId=${msgId.substring(0, 8)}...`)
    return { ok: true }
  }

  sendReadReceipt(readerKey, senderKey, msgId) {
    if (!isValidPubKey(senderKey)) return { ok: false, reason: 'invalid_sender' }
    if (!isValidMsgId(msgId))      return { ok: false, reason: 'invalid_msgId' }

    const receipt  = { type: 'read_receipt', msgId, from: readerKey }
    const senderWs = this.clients.get(senderKey)

    if (senderWs && senderWs.readyState === 1) {
      this._send(senderWs, receipt)
      this.stats.delivered++
    } else {
      this._enqueue(senderKey, { ...receipt, id: this._generateId() })
    }
    return { ok: true }
  }

  // Обновляем режим при mode_update от клиента
  updateMode(pubKey, mode) {
    this.lastKnownMode.set(pubKey, parseModeMinutes(mode))
  }

  _enqueue(pubKey, envelope) {
    if (!this.queues.has(pubKey)) this.queues.set(pubKey, [])
    const queue = this.queues.get(pubKey)
    if (queue.length >= MAX_QUEUE) { this.stats.rejected++; return }

    // TTL = базовый + интервал окна получателя
    const recipientMinutes = this.lastKnownMode.get(pubKey) ?? 0
    const ttl = MSG_TTL_MS + (recipientMinutes * 60 * 1000)
    queue.push({ ...envelope, expiresAt: Date.now() + ttl })
    console.log(`[hub] enqueue for ${pubKey.substring(0,8)}... ttl=${Math.round(ttl/1000)}s`)
  }

  _flushQueue(pubKey, ws) {
    const queue = this.queues.get(pubKey)
    const mode  = ws._receptionMode || 'LIVE'

    if (queue && queue.length > 0) {
      const now = Date.now()
      for (const msg of queue) {
        if (msg.expiresAt <= now) { this.stats.expired++; continue }
        const { expiresAt, ...envelope } = msg
        this._send(ws, envelope)
        this.stats.delivered++
      }
      this.queues.delete(pubKey)
    }

    if (mode !== 'LIVE') {
      setTimeout(() => {
        this._send(ws, { type: 'queue_end' })
        console.log(`[hub] queue_end sent to ${pubKey.substring(0, 8)}...`)
      }, 100)
    }
  }

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
    return crypto.randomBytes(8).toString('hex') + Date.now().toString(36)
  }

  _logStats() {
    const { delivered, expired, rejected, blocked } = this.stats
    console.log(`[hub] stats  peers=${this.clients.size}  delivered=${delivered}  expired=${expired}  rejected=${rejected}  blocked=${blocked}`)
  }

  broadcast(fromKey, data) {
    for (const [pubKey, ws] of this.clients) {
      if (pubKey === fromKey) continue
      if (ws.readyState === 1) this._send(ws, data)
    }
  }

  terminateAll() {
    clearInterval(this._sweepTimer)
    clearInterval(this._statsTimer)
    for (const ws of this.clients.values()) {
      try { ws.close(1001, 'server_shutdown'); ws.terminate() } catch {}
    }
    this.clients.clear()
    this.queues.clear()
    this.ipConnections.clear()
  }
}

module.exports = new Hub()