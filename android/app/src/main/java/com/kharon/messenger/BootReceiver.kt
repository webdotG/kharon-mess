package com.kharon.messenger.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.kharon.messenger.model.ReceptionMode
import com.kharon.messenger.service.KharonForegroundService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_LOCKED_BOOT_COMPLETED) {
            val serviceIntent = Intent(context, KharonForegroundService::class.java).apply {
                action = KharonForegroundService.ACTION_START
                putExtra(KharonForegroundService.EXTRA_MODE, ReceptionMode.LIVE.name)
            }
            context.startForegroundService(serviceIntent)
        }
    }
}