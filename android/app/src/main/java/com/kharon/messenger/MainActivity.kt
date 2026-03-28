package com.kharon.messenger

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.kharon.messenger.service.KharonForegroundService
import com.kharon.messenger.ui.screens.*
import com.kharon.messenger.ui.theme.*
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Запрет скриншотов и показа в switcher задач
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)

        // Запускаем ForegroundService — он держит WebSocket живым
        startForegroundService()

        setContent {
            KharonMessengerApp()
        }
    }

    private fun startForegroundService() {
        val intent = Intent(this, KharonForegroundService::class.java).apply {
            action = KharonForegroundService.ACTION_START
        }
        startForegroundService(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        // Останавливаем сервис только если приложение закрыто свайпом
        // (не при повороте экрана)
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
fun KharonMessengerApp() {
    val navController = rememberNavController()

    // Тема — хранится в памяти, при желании можно сохранять в prefs
    var currentThemeId by remember { mutableStateOf(ThemeId.MODERN) }
    val currentTheme   = remember(currentThemeId) { themeById(currentThemeId) }

    KharonThemeProvider(theme = currentTheme) {
        NavHost(
            navController    = navController,
            startDestination = "contacts",
        ) {

            // ── Контакты ──────────────────────────────────────────────────────
            composable("contacts") {
                ContactsScreen(
                    onContactClick  = { contact ->
                        navController.navigate("chat/${contact.pubKey}/${contact.name}")
                    },
                    onAddContact    = { navController.navigate("add_contact") },
                    onSettingsClick = { navController.navigate("settings") },
                )
            }

            // ── Чат ───────────────────────────────────────────────────────────
            composable(
                route     = "chat/{pubKey}/{name}",
                arguments = listOf(
                    navArgument("pubKey") { type = NavType.StringType },
                    navArgument("name")   { type = NavType.StringType },
                )
            ) { backStack ->
                val name = backStack.arguments?.getString("name") ?: ""
                ChatScreen(
                    contactName = name,
                )
            }

            // ── Добавить контакт ──────────────────────────────────────────────
            composable("add_contact") {
                AddContactScreen(
                    onBack  = { navController.popBackStack() },
                    onAdded = { navController.popBackStack() },
                )
            }

            // ── Настройки / смена темы ────────────────────────────────────────
            composable("settings") {
                SettingsScreen(
                    currentThemeId = currentThemeId,
                    onThemeSelect  = { currentThemeId = it },
                    onBack         = { navController.popBackStack() },
                )
            }
        }
    }
}
