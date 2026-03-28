'use strict'

require('dotenv').config()

const http = require('http')
const { WebSocketServer } = require('ws')
const hub = require('./hub')

const PORT       = parseInt(process.env.PORT           ?? '3000')
const RATE_LIMIT = parseInt(process.env.MSG_RATE_LIMIT ?? '3')
const BUCKET_MAX = parseInt(process.env.MSG_BUCKET_MAX ?? '9')

// Token bucket: refill at RATE_LIMIT tokens/sec, max BUCKET_MAX
// Computed lazily on each message — no per-connection timers
function makeBucket() {
  return { tokens: BUCKET_MAX, lastRefil: Date.now() }
}

function consumeToken(bucket) {
  const now  = Date.now()
  const fill = ((now - bucket.lastRefil) / 1000) * RATE_LIMIT
  bucket.tokens    = Math.min(BUCKET_MAX, bucket.tokens + fill)
  bucket.lastRefil = now
  if (bucket.tokens < 1) return false
  bucket.tokens -= 1
  return true
}

const server = http.createServer((req, res) => {
  if (req.url === '/health') {
    res.writeHead(200, { 'Content-Type': 'application/json' })
    res.end(JSON.stringify({ ok: true, peers: hub.clients.size }))
    return
  }
  res.writeHead(404).end()
})

const wss = new WebSocketServer({ server, maxPayload: 8192 })

wss.on('connection', (ws, req) => {
  const ip = req.headers['x-real-ip'] || req.socket.remoteAddress || 'unknown'

  let clientKey  = null
  let identified = false
  const bucket   = makeBucket()

  const helloTimeout = setTimeout(() => {
    if (!identified) ws.close(1008, 'hello_timeout')
  }, 10_000)

  ws.on('message', (raw) => {
    if (!consumeToken(bucket)) {
      console.warn(`[ws] rate_limit  ip=${ip}`)
      ws.close(1008, 'rate_limit_exceeded')
      return
    }

    if (raw.length > 8192) {
      ws.close(1009, 'message_too_large')
      return
    }

    let msg
    try { msg = JSON.parse(raw) }
    catch { send(ws, { type: 'error', message: 'invalid_json' }); return }

    if (typeof msg !== 'object' || typeof msg.type !== 'string') {
      send(ws, { type: 'error', message: 'invalid_message' })
      return
    }

    switch (msg.type) {
      case 'hello': {
        if (identified) { send(ws, { type: 'error', message: 'already_identified' }); return }
        const result = hub.register(msg.pubKey, ws, ip)
        if (!result.ok) {
          send(ws, { type: 'error', message: result.reason })
          ws.close(1008, result.reason)
          return
        }
        clientKey  = msg.pubKey
        identified = true
        clearTimeout(helloTimeout)
        send(ws, { type: 'welcome', peersOnline: hub.clients.size })
        break
      }

      case 'msg': {
        if (!identified) { send(ws, { type: 'error', message: 'not_identified' }); return }
        const result = hub.route(clientKey, msg.to, msg.payload, msg.id)
        if (!result.ok) { send(ws, { type: 'error', message: result.reason }); return }
        send(ws, { type: 'ack', id: result.id })
        break
      }

      case 'ping': {
        send(ws, { type: 'pong' })
        break
      }

      default:
        send(ws, { type: 'error', message: 'unknown_type' })
    }
  })

  ws.on('close', (code) => {
    clearTimeout(helloTimeout)
    if (clientKey) hub.unregister(clientKey, ip)
    console.log(`[ws] closed  code=${code}`)
  })

  ws.on('error', (err) => console.error('[ws] error:', err.message))
})

function send(ws, data) {
  if (ws.readyState === ws.OPEN) ws.send(JSON.stringify(data))
}

server.listen(PORT, '0.0.0.0', () => {
  console.log(`[kharon] port=${PORT}  rate=${RATE_LIMIT}/s  bucket=${BUCKET_MAX}`)
})

process.on('SIGTERM', () => {
  console.log('[kharon] shutdown')
  hub.destroy()
  wss.close(() => server.close(() => process.exit(0)))
})
