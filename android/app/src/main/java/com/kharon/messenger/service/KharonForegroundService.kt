package com.kharon.messenger.service

import android.app.*
import android.content.*
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.kharon.messenger.MainActivity
import com.kharon.messenger.R
import com.kharon.messenger.crypto.CryptoManager
import com.kharon.messenger.network.ConnectionState
import com.kharon.messenger.network.KharonSocket
import com.kharon.messenger.model.ReceptionMode
import dagger.hilt.android.AndroidEntryPoint
import com.kharon.messenger.util.KLog
import kotlinx.coroutines.*
import javax.inject.Inject

@AndroidEntryPoint
class KharonForegroundService : Service() {

    @Inject lateinit var socket: KharonSocket
    @Inject lateinit var crypto: CryptoManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var currentMode = ReceptionMode.LIVE
    private var socketStarted = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification(ConnectionState.Connecting))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val modeName = intent?.getStringExtra(EXTRA_MODE) ?: ReceptionMode.LIVE.name

        val newMode = ReceptionMode.valueOf(modeName)
        when (intent?.action) {
            ACTION_START -> {
                if (newMode != currentMode) {
                    // Режим изменился — сбрасываем и переподключаемся
                    KLog.svc("mode changed $currentMode -> $newMode — resetting")
                    currentMode = newMode
                    socketStarted = false
                    socket.disconnect()
                }
                startSocket()
            }
            ACTION_STOP  -> {
                socket.disconnect()
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun startSocket() {
        if (socketStarted) { KLog.svc("startSocket already started — skip"); return }
        socketStarted = true
        KLog.svc("startSocket mode=$currentMode")
        val keyPair = crypto.getOrCreateKeyPair()
        // Не переподключаемся если уже в процессе подключения или подключены
        if (socket.state.value is ConnectionState.Connected ||
            socket.state.value is ConnectionState.Connecting) return
        socket.connect(keyPair.publicKey, currentMode)

        scope.launch {
            socket.state.collect { state ->
                updateNotification(state)
                // Если мы в пульсе и сокет закрылся - планируем следующий раз
                if (state is ConnectionState.Disconnected && currentMode.minutes > 0) {
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
    }

    private fun scheduleNextPulse() {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, KharonForegroundService::class.java).apply {
            action = ACTION_START
            putExtra(EXTRA_MODE, currentMode.name)
        }
        val pendingIntent = PendingIntent.getService(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val triggerAt = System.currentTimeMillis() + (currentMode.minutes * 60 * 1000)
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "Kharon", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(state: ConnectionState): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        val statusText = when (state) {
            is ConnectionState.Connected -> "Connected"
            is ConnectionState.Connecting -> "Connecting..."
            else -> "Offline"
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
        const val ACTION_STOP  = "com.kharon.messenger.STOP"
        const val EXTRA_MODE   = "extra_reception_mode"
        private const val CHANNEL_ID      = "kharon_service"
        private const val NOTIFICATION_ID = 1001
    }
}