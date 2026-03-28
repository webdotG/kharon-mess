package com.kharon.messenger.ui.screens

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.kharon.messenger.ui.theme.BorderStyle
import com.kharon.messenger.ui.theme.win95Border
import com.kharon.messenger.ui.theme.KharonUI
import com.kharon.messenger.viewmodel.ContactsViewModel

@Composable
fun AddContactScreen(
    onBack:    () -> Unit,
    onAdded:   () -> Unit,
    viewModel: ContactsViewModel = hiltViewModel(),
) {
    val state     by viewModel.uiState.collectAsState()
    val theme      = KharonUI.current
    val colors     = theme.colors
    val clipboard  = LocalClipboardManager.current

    var tab         by remember { mutableStateOf(0) }  // 0=мой ключ, 1=ввести ключ, 2=QR
    var inputKey    by remember { mutableStateOf("") }
    var contactName by remember { mutableStateOf("") }
    var scannedKey  by remember { mutableStateOf("") }
    var copied      by remember { mutableStateOf(false) }

    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.let {
            scannedKey = it
            inputKey   = it
            tab        = 1
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .systemBarsPadding()
    ) {
        // ── Title bar ─────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.titleBar)
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = colors.titleBarText
                )
            }
            Text(
                text       = "Add Contact",
                color      = colors.titleBarText,
                fontSize   = theme.typography.titleSize,
                fontFamily = theme.typography.fontFamily,
                fontWeight = FontWeight.Bold,
            )
        }

        // ── Табы ──────────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surface)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf("My Key", "Enter Key", "QR").forEachIndexed { idx, label ->
                val selected = tab == idx
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(theme.shapes.buttonRadius))
                        .background(if (selected) colors.primary else colors.surfaceVariant)
                        .clickable { tab = idx }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text       = label,
                        color      = if (selected) colors.onPrimary else colors.onSurface,
                        fontSize   = theme.typography.bodySize,
                        fontFamily = theme.typography.fontFamily,
                    )
                }
            }
        }

        // ── Контент ───────────────────────────────────────────────────────────
        when (tab) {
            0 -> MyKeyTab(
                myPubKey  = state.myPubKey,
                copied    = copied,
                onCopy    = {
                    clipboard.setText(AnnotatedString(state.myPubKey))
                    copied = true
                },
                theme     = theme,
            )
            1 -> EnterKeyTab(
                inputKey    = inputKey,
                contactName = contactName,
                onKeyChange = { inputKey = it },
                onNameChange= { contactName = it },
                onAdd       = {
                    if (inputKey.isNotEmpty() && contactName.isNotEmpty()) {
                        viewModel.addContact(inputKey.trim(), contactName.trim())
                        onAdded()
                    }
                },
                theme = theme,
            )
            2 -> QrTab(
                myPubKey  = state.myPubKey,
                onScan    = {
                    val opts = ScanOptions().apply {
                        setPrompt("Scan colleague's QR code")
                        setBeepEnabled(false)
                        setOrientationLocked(true)
                    }
                    scanLauncher.launch(opts)
                },
                theme = theme,
            )
        }
    }
}

// ─── Таб: Мой ключ ────────────────────────────────────────────────────────────

@Composable
private fun MyKeyTab(
    myPubKey: String,
    copied:   Boolean,
    onCopy:   () -> Unit,
    theme:    com.kharon.messenger.ui.theme.KharonTheme,
) {
    val colors = theme.colors

    Column(
        modifier            = Modifier.fillMaxSize().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text       = "Share your key with a colleague so they can add you",
            color      = colors.subtle,
            fontSize   = theme.typography.bodySize,
            fontFamily = theme.typography.fontFamily,
        )

        // Ключ в рамке
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(theme.shapes.cardRadius))
                .background(colors.surfaceVariant)
                .padding(16.dp),
        ) {
            Text(
                text       = myPubKey,
                color      = colors.onSurface,
                fontSize   = theme.typography.captionSize,
                fontFamily = theme.typography.fontFamily,
            )
        }

        // Кнопка копировать
        Button(
            onClick  = onCopy,
            modifier = Modifier.fillMaxWidth(),
            colors   = ButtonDefaults.buttonColors(
                containerColor = if (copied) colors.online else colors.primary
            ),
            shape = RoundedCornerShape(theme.shapes.buttonRadius),
        ) {
            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                text       = if (copied) "Copied! Send to colleague" else "Copy Key",
                fontFamily = theme.typography.fontFamily,
            )
        }

        if (copied) {
            Text(
                text       = "Send this key via SMS, email, or any messenger.\nYour colleague pastes it in 'Enter Key' tab.",
                color      = colors.subtle,
                fontSize   = theme.typography.captionSize,
                fontFamily = theme.typography.fontFamily,
            )
        }
    }
}

// ─── Таб: Ввести ключ ─────────────────────────────────────────────────────────

@Composable
private fun EnterKeyTab(
    inputKey:    String,
    contactName: String,
    onKeyChange: (String) -> Unit,
    onNameChange:(String) -> Unit,
    onAdd:       () -> Unit,
    theme:       com.kharon.messenger.ui.theme.KharonTheme,
) {
    val colors = theme.colors
    val keyValid = inputKey.trim().length == 44

    Column(
        modifier            = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text       = "Paste the key you received from your colleague",
            color      = colors.subtle,
            fontSize   = theme.typography.bodySize,
            fontFamily = theme.typography.fontFamily,
        )

        // Поле ключа
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text       = "Public Key",
                color      = colors.onBackground,
                fontSize   = theme.typography.captionSize,
                fontFamily = theme.typography.fontFamily,
            )
            BasicTextField(
                value         = inputKey,
                onValueChange = onKeyChange,
                modifier      = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(theme.shapes.buttonRadius))
                    .background(colors.surfaceVariant)
                    .padding(12.dp),
                textStyle = TextStyle(
                    color      = colors.onSurface,
                    fontSize   = theme.typography.captionSize,
                    fontFamily = theme.typography.fontFamily,
                ),
                maxLines = 3,
                decorationBox = { inner ->
                    if (inputKey.isEmpty()) {
                        Text(
                            "Paste 44-character key here...",
                            color    = colors.subtle,
                            fontSize = theme.typography.captionSize,
                        )
                    }
                    inner()
                }
            )
            // Валидация
            if (inputKey.isNotEmpty() && !keyValid) {
                Text(
                    text       = "Key must be 44 characters (${inputKey.trim().length}/44)",
                    color      = colors.offline,
                    fontSize   = theme.typography.captionSize,
                    fontFamily = theme.typography.fontFamily,
                )
            } else if (keyValid) {
                Text(
                    text       = "✓ Valid key",
                    color      = colors.online,
                    fontSize   = theme.typography.captionSize,
                    fontFamily = theme.typography.fontFamily,
                )
            }
        }

        // Имя контакта
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text       = "Contact Name",
                color      = colors.onBackground,
                fontSize   = theme.typography.captionSize,
                fontFamily = theme.typography.fontFamily,
            )
            BasicTextField(
                value         = contactName,
                onValueChange = onNameChange,
                modifier      = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(theme.shapes.buttonRadius))
                    .background(colors.surfaceVariant)
                    .padding(12.dp),
                textStyle = TextStyle(
                    color      = colors.onSurface,
                    fontSize   = theme.typography.bodySize,
                    fontFamily = theme.typography.fontFamily,
                ),
                singleLine = true,
                decorationBox = { inner ->
                    if (contactName.isEmpty()) {
                        Text("e.g. Alexey", color = colors.subtle, fontSize = theme.typography.bodySize)
                    }
                    inner()
                }
            )
        }

        Button(
            onClick  = onAdd,
            enabled  = keyValid && contactName.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
            colors   = ButtonDefaults.buttonColors(containerColor = colors.primary),
            shape    = RoundedCornerShape(theme.shapes.buttonRadius),
        ) {
            Text(
                text       = "Add Contact",
                fontFamily = theme.typography.fontFamily,
            )
        }
    }
}

// ─── Таб: QR ─────────────────────────────────────────────────────────────────

@Composable
private fun QrTab(
    myPubKey: String,
    onScan:   () -> Unit,
    theme:    com.kharon.messenger.ui.theme.KharonTheme,
) {
    val colors = theme.colors

    Column(
        modifier            = Modifier.fillMaxSize().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (myPubKey.isNotEmpty()) {
            val qrBitmap = remember(myPubKey) { generateQr(myPubKey, 512) }
            qrBitmap?.let {
                Image(
                    bitmap             = it.asImageBitmap(),
                    contentDescription = "My QR code",
                    modifier           = Modifier
                        .size(220.dp)
                        .clip(RoundedCornerShape(theme.shapes.cardRadius))
                        .background(androidx.compose.ui.graphics.Color.White)
                        .padding(8.dp),
                )
            }
        }

        Button(
            onClick  = onScan,
            modifier = Modifier.fillMaxWidth(),
            colors   = ButtonDefaults.buttonColors(containerColor = colors.primary),
            shape    = RoundedCornerShape(theme.shapes.buttonRadius),
        ) {
            Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Scan Colleague's QR", fontFamily = theme.typography.fontFamily)
        }
    }
}

// ─── QR генератор ─────────────────────────────────────────────────────────────

private fun generateQr(content: String, size: Int): Bitmap? {
    return try {
        val hints = mapOf(EncodeHintType.MARGIN to 1)
        val bits  = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints)
        val bmp   = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bmp.setPixel(x, y, if (bits[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
            }
        }
        bmp
    } catch (e: Exception) { null }
}
