package com.kharon.messenger

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import java.net.URLEncoder
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
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
    var currentThemeId  by remember { mutableStateOf(ThemeId.DEFAULT) }
    var currentFontSize by remember { mutableStateOf(FontSize.MEDIUM) }
    val currentTheme = remember(currentThemeId, currentFontSize) {
        val base = themeById(currentThemeId)
        base.copy(
            typography = base.typography.copy(
                bodySize    = currentFontSize.body,
                titleSize   = currentFontSize.title,
                captionSize = currentFontSize.caption,
            )
        )
    }

    KharonThemeProvider(theme = currentTheme) {
        NavHost(
            navController    = navController,
            startDestination = "contacts",
        ) {

            // ── Контакты ──────────────────────────────────────────────────────
            composable("contacts") {
                ContactsScreen(
                    onContactClick  = { contact ->
                        val encodedKey  = URLEncoder.encode(contact.pubKey, StandardCharsets.UTF_8.name())
                        val encodedName = URLEncoder.encode(contact.name, StandardCharsets.UTF_8.name())
                        navController.navigate("chat/$encodedKey/$encodedName")
                    },
                    onAddContact    = { navController.navigate("add_contact") },
                    onSettingsClick = { navController.navigate("settings") },
                )
            }

            // ── Чат ───────────────────────────────────────────────────────────
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
                    contactPubKey = URLDecoder.decode(rawKey, StandardCharsets.UTF_8.name()),
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
                    currentThemeId  = currentThemeId,
                    currentFontSize = currentFontSize,
                    onThemeSelect   = { currentThemeId = it },
                    onFontSelect    = { currentFontSize = it },
                    onBack          = { navController.popBackStack() },
                )
            }
        }
    }
}
