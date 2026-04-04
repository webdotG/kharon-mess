package com.kharon.messenger

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import java.net.URLEncoder
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.kharon.messenger.model.ReceptionMode
import com.kharon.messenger.service.KharonForegroundService
import com.kharon.messenger.ui.screens.*
import com.kharon.messenger.ui.theme.*
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @javax.inject.Inject
    lateinit var socket: com.kharon.messenger.network.KharonSocket

    private fun requestBatteryOptimizationExemption() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        // Проверяем, не в белом ли мы списке уже
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            }
            try { startActivity(intent) } catch (e: Exception) { }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestBatteryOptimizationExemption()
        
        // Запрет скриншотов и показа в switcher задач
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)

        // Запускаем ForegroundService только при первом старте
        startKharonService()

        setContent {
            KharonMessengerApp(onChatOpen = { pubKey -> socket.markChatActive(pubKey) })
        }
    }

    private fun startKharonService() {
        val intent = Intent(this, KharonForegroundService::class.java).apply {
            action = KharonForegroundService.ACTION_START
            putExtra(KharonForegroundService.EXTRA_MODE, ReceptionMode.LIVE.name)
        }
        startForegroundService(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        // Останавливаем сервис только если приложение закрыто свайпом
        if (isFinishing) {
            val intent = Intent(this, KharonForegroundService::class.java).apply {
                action = KharonForegroundService.ACTION_STOP
            }
            startService(intent)
        }
    }
}

// ─── Корневой Composable ──────────────────────────────────────────────────────

@Composable
fun KharonMessengerApp(onChatOpen: (String) -> Unit = {}) {
    val navController = rememberNavController()

    var currentThemeId  by remember { mutableStateOf(ThemeId.DEFAULT) }
    
    val currentTheme = remember(currentThemeId) { 
        themeById(currentThemeId) 
    }
    
    var currentMode     by remember { mutableStateOf<ReceptionMode>(ReceptionMode.LIVE) }


    KharonThemeProvider(theme = currentTheme) {
        NavHost(
            navController    = navController,
            startDestination = "contacts",
        ) {

            composable("contacts") {
                ContactsScreen(
                    onContactClick  = { contact ->
                        val encodedKey  = URLEncoder.encode(contact.pubKey, StandardCharsets.UTF_8.name())
                        val encodedName = URLEncoder.encode(contact.name, StandardCharsets.UTF_8.name())
                        onChatOpen(contact.pubKey)
                        navController.navigate("chat/$encodedKey/$encodedName")
                    },
                    onAddContact    = { navController.navigate("add_contact") },
                    onSettingsClick = { navController.navigate("settings") },
                    currentMode     = currentMode
                )
            }

            composable(
                route     = "chat/{contactPubKey}/{name}",
                arguments = listOf(
                    navArgument("contactPubKey") { type = NavType.StringType },
                    navArgument("name")          { type = NavType.StringType },
                )
            ) { backStack ->
                val rawKey  = backStack.arguments?.getString("contactPubKey") ?: ""
                val rawName = backStack.arguments?.getString("name") ?: ""
                val name    = URLDecoder.decode(rawName, StandardCharsets.UTF_8.name())
                ChatScreen(
                    contactName   = name,
                    contactPubKey = URLDecoder.decode(rawKey, StandardCharsets.UTF_8.name()).replace(" ", "+"),
                )
            }

            composable("add_contact") {
                AddContactScreen(
                    onBack      = { navController.popBackStack() },
                    onAdded     = { navController.popBackStack() },
                )
            }

            composable("settings") {
                SettingsScreen(
                    currentThemeId  = currentThemeId,
                    currentMode     = currentMode,
                    onThemeSelect   = { currentThemeId = it },
                    onModeSelect    = { currentMode = it },
                    onBack          = { navController.popBackStack() },
                )
            }
        }
    }
}