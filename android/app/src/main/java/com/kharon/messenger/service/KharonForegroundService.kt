package com.kharon.messenger.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.kharon.messenger.MainActivity
import com.kharon.messenger.R
import com.kharon.messenger.crypto.CryptoManager
import com.kharon.messenger.network.ConnectionState
import com.kharon.messenger.network.KharonSocket
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ForegroundService держит WebSocket соединение живым когда приложение в фоне.
 *
 * Без ForegroundService Android убьёт соединение через несколько минут.
 * Нотификация обязательна — это честно по отношению к пользователю
 * (он видит что приложение работает) и требуется Play Market политикой.
 *
 * Тип сервиса: dataSync — наиболее подходящий для мессенджеров.
 */
@AndroidEntryPoint
class KharonForegroundService : Service() {

    @Inject lateinit var socket: KharonSocket
    @Inject lateinit var crypto: CryptoManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification(ConnectionState.Connecting))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startSocket()
            ACTION_STOP  -> {
                socket.disconnect()
                stopSelf()
            }
        }
        // STICKY — если сервис убит системой, перезапустить с последним intent
        return START_STICKY
    }

    private fun startSocket() {
        val keyPair = crypto.getOrCreateKeyPair()
        socket.connect(keyPair.publicKey, keyPair.secretKey)

        // Следим за состоянием соединения — обновляем нотификацию
        scope.launch {
            socket.state.collect { state ->
                updateNotification(state)
            }
        }

        // Keepalive ping каждые 30 секунд
        // WebSocket соединения могут тихо умереть без трафика
        scope.launch {
            while (true) {
                delay(30_000)
                socket.ping()
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        socket.disconnect()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ─── Нотификация ──────────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Kharon",
            NotificationManager.IMPORTANCE_LOW   // LOW = без звука, без вибрации
        ).apply {
            description = "Kharon messenger connection"
            setShowBadge(false)
        }

        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    private fun buildNotification(state: ConnectionState): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val (statusText, iconRes) = when (state) {
            is ConnectionState.Connected    -> "Connected" to R.drawable.ic_notification_connected
            is ConnectionState.Connecting   -> "Connecting..." to R.drawable.ic_notification_connecting
            is ConnectionState.Disconnected -> "Disconnected" to R.drawable.ic_notification_disconnected
            is ConnectionState.Error        -> "Connection error" to R.drawable.ic_notification_disconnected
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Kharon")
            .setContentText(statusText)
            .setSmallIcon(iconRes)
            .setContentIntent(pendingIntent)
            .setOngoing(true)           // нельзя смахнуть
            .setSilent(true)            // без звука
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun updateNotification(state: ConnectionState) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(state))
    }

    companion object {
        const val ACTION_START = "com.kharon.messenger.START"
        const val ACTION_STOP  = "com.kharon.messenger.STOP"
        private const val CHANNEL_ID      = "kharon_service"
        private const val NOTIFICATION_ID = 1001
    }
}
