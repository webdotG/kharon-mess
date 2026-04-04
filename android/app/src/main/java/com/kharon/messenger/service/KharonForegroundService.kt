package com.kharon.messenger.service

import android.app.*
import android.content.*
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.kharon.messenger.MainActivity
import com.kharon.messenger.R
import com.kharon.messenger.crypto.CryptoManager
import com.kharon.messenger.network.ConnectionState
import com.kharon.messenger.network.KharonSocket
import com.kharon.messenger.network.SocketEvent
import com.kharon.messenger.model.ReceptionMode
import dagger.hilt.android.AndroidEntryPoint
import com.kharon.messenger.util.KLog
import kotlinx.coroutines.*
import javax.inject.Inject

@AndroidEntryPoint
class KharonForegroundService : Service() {

    @Inject
    lateinit var socket: KharonSocket
    @Inject
    lateinit var crypto: CryptoManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var currentMode = ReceptionMode.LIVE
    private var socketStarted = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        startForeground(NOTIFICATION_ID, buildNotification(ConnectionState.Connecting))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val modeName = intent?.getStringExtra(EXTRA_MODE) ?: ReceptionMode.LIVE.name
        val newMode = ReceptionMode.valueOf(modeName)
        when (intent?.action) {
            ACTION_START -> {
                if (newMode != currentMode) {
                    KLog.svc("mode changed $currentMode -> $newMode — resetting")
                    currentMode = newMode
                    socketStarted = false
                    socket.disconnect()
                }
                startSocket()
            }

            ACTION_STOP -> {
                socket.disconnect()
                stopSelf()
            }

            ACTION_CHAT_OPEN -> {
                // Временно поднимаем сокет если офлайн
                if (socket.state.value is ConnectionState.Disconnected) {
                    KLog.svc("chatOpen — connecting temporarily")
                    val keyPair = crypto.getOrCreateKeyPair()
                    socket.connect(keyPair.publicKey, ReceptionMode.LIVE)
                }
            }

            ACTION_CHAT_CLOSE -> {
                // Возвращаем свой режим
                KLog.svc("chatClose — restoring mode=$currentMode")
                if (currentMode != ReceptionMode.LIVE) {
                    socketStarted = false
                    socket.disconnect()
                    // Небольшая задержка чтобы сокет закрылся
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        startSocket()
                    }, 500)
                }
            }
        }
        return START_STICKY
    }

    private fun startSocket() {
        if (socketStarted) {
            KLog.svc("startSocket already started — skip"); return
        }
        socketStarted = true
        KLog.svc("startSocket mode=$currentMode")
        val keyPair = crypto.getOrCreateKeyPair()
        if (socket.state.value is ConnectionState.Connected ||
            socket.state.value is ConnectionState.Connecting
        ) return
        socket.connect(keyPair.publicKey, currentMode)

        scope.launch {
            var wasConnected = false
            socket.state.collect { state ->
                updateNotification(state)
                if (state is ConnectionState.Connected) wasConnected = true
                if (state is ConnectionState.Disconnected && currentMode.minutes > 0 && wasConnected) {
                    KLog.svc("PULSE disconnect — scheduling next in ${currentMode.minutes} min")
                    scheduleNextPulse()
                    stopSelf()
                }
            }
        }

        if (currentMode == ReceptionMode.LIVE) {
            scope.launch {
                while (isActive) {
                    delay(30_000)
                    socket.ping()
                }
            }
        }

        if (currentMode != ReceptionMode.LIVE && currentMode != ReceptionMode.SILENT) {
            socket.resetPendingCount()
            scope.launch {
                socket.events.collect { event ->
                    when (event) {
                        is SocketEvent.QueueEnd -> {
                            val count = socket.getTotalPendingCount()
                            KLog.svc("QueueEnd received totalPending=$count")
                            showPulseNotification(count)
                        }

                        else -> {}
                    }
                }
            }
        }
    }

    private fun showPulseNotification(msgCount: Int) {
        val intent = Intent(this, MainActivity::class.java)
        val pending = PendingIntent.getActivity(
            this, 2, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val text = if (msgCount > 0) "Новых сообщений: $msgCount"
        else "Окно связи открылось — новых сообщений нет"
        KLog.svc("showPulseNotification: $text")

        val channelId = if (msgCount > 0) CHANNEL_ALERT_ID else CHANNEL_ID
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Kharon")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_notification_connected)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .apply {
                if (msgCount > 0) {
                    setVibrate(longArrayOf(0, 250, 100, 250))
                } else {
                    setSilent(true)
                }
            }
            .build()

        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID + 1, notification)
    }

    private fun scheduleNextPulse() {
        KLog.svc("scheduleNextPulse in ${currentMode.minutes} min")
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, KharonForegroundService::class.java).apply {
            action = ACTION_START
            putExtra(EXTRA_MODE, currentMode.name)
        }
        val pendingIntent = PendingIntent.getService(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val triggerAt = System.currentTimeMillis() + (currentMode.minutes * 60 * 1000L)

        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAt,
                        pendingIntent
                    )
                    KLog.svc("alarm EXACT scheduled")
                } else {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAt,
                        pendingIntent
                    )
                    KLog.svc("alarm INEXACT scheduled (no permission)")
                }
            }

            else -> {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAt,
                    pendingIntent
                )
                KLog.svc("alarm EXACT scheduled (pre-S)")
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannels() {
        val manager = getSystemService(NotificationManager::class.java)
        // Тихий канал для статуса сервиса
        val silentChannel = NotificationChannel(
            CHANNEL_ID, "Kharon статус", NotificationManager.IMPORTANCE_LOW
        )
        // Канал с вибрацией для новых сообщений
        val alertChannel = NotificationChannel(
            CHANNEL_ALERT_ID, "Kharon сообщения", NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 250, 100, 250)
        }
        manager.createNotificationChannel(silentChannel)
        manager.createNotificationChannel(alertChannel)
    }

    private fun buildNotification(state: ConnectionState): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )
        val statusText = when (state) {
            is ConnectionState.Connected -> "На связи"
            is ConnectionState.Connecting -> "Подключение..."
            is ConnectionState.Disconnected -> "Офлайн"
            is ConnectionState.Error -> "Ошибка"
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Kharon")
            .setContentText(statusText)
            .setSmallIcon(R.drawable.ic_notification_connected)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun updateNotification(state: ConnectionState) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(state))
    }

    companion object {
        const val ACTION_START = "com.kharon.messenger.START"
        const val ACTION_STOP = "com.kharon.messenger.STOP"
        const val ACTION_CHAT_OPEN = "com.kharon.messenger.CHAT_OPEN"   // ← добавь
        const val ACTION_CHAT_CLOSE = "com.kharon.messenger.CHAT_CLOSE"  // ← добавь
        const val EXTRA_MODE = "extra_reception_mode"
        private const val CHANNEL_ID = "kharon_service"
        private const val CHANNEL_ALERT_ID = "kharon_alerts"
        private const val NOTIFICATION_ID = 1001
    }
}