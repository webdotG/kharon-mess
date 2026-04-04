package com.kharon.messenger.ui.screens

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.kharon.messenger.ui.theme.KharonTheme
import com.kharon.messenger.ui.theme.KharonUI
import com.kharon.messenger.viewmodel.ContactsViewModel

@Composable
fun AddContactScreen(
    onBack: () -> Unit,
    onAdded: () -> Unit,
    viewModel: ContactsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val theme = KharonUI.current
    val colors = theme.colors
    val clipboard = LocalClipboardManager.current

    var tab by remember { mutableIntStateOf(0) }
    var inputKey by remember { mutableStateOf("") }
    var contactName by remember { mutableStateOf("") }
    var copied by remember { mutableStateOf(false) }

    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.let {
            inputKey = it
            tab = 1
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .systemBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.titleBar)
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "[ < ] ",
                    color = colors.primary,
                    fontSize = 18.sp,
                    fontFamily = theme.typography.fontFamily,
                    modifier = Modifier.clickable { onBack() }
                )
                Text(
                    text = "ADD_NODE",
                    color = colors.titleBarText,
                    fontSize = 18.sp,
                    fontFamily = theme.typography.fontFamily,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(
                text = "[+]",
                color = colors.primary,
                fontSize = 18.sp,
                fontFamily = theme.typography.fontFamily
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surface)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            listOf("MY_KEY", "ENTER", "SCAN").forEachIndexed { idx, label ->
                val selected = tab == idx
                Text(
                    text = if (selected) "[$label]" else " $label ",
                    color = if (selected) colors.primary else colors.subtle,
                    fontSize = 18.sp,
                    fontFamily = theme.typography.fontFamily,
                    modifier = Modifier.clickable { tab = idx }
                )
            }
        }

        Column(modifier = Modifier.padding(16.dp)) {
            when (tab) {
                0 -> MyKeyTab(
                    myPubKey = state.myPubKey,
                    copied = copied,
                    onCopy = {
                        clipboard.setText(AnnotatedString(state.myPubKey))
                        copied = true
                    },
                    theme = theme
                )
                1 -> EnterKeyTab(
                    inputKey = inputKey,
                    contactName = contactName,
                    onKeyChange = { inputKey = it },
                    onNameChange = { contactName = it },
                    onAdd = {
                        if (inputKey.isNotEmpty() && contactName.isNotEmpty()) {
                            viewModel.addContact(inputKey.trim(), contactName.trim())
                            onAdded()
                        }
                    },
                    theme = theme
                )
                2 -> QrTab(
                    myPubKey = state.myPubKey,
                    onScan = {
                        val opts = ScanOptions().apply {
                            setPrompt("SCAN_QR_CODE")
                            setBeepEnabled(false)
                            setOrientationLocked(true)
                        }
                        scanLauncher.launch(opts)
                    },
                    theme = theme
                )
            }
        }
    }
}

@Composable
private fun MyKeyTab(
    myPubKey: String,
    copied: Boolean,
    onCopy: () -> Unit,
    theme: KharonTheme,
) {
    val colors = theme.colors
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = "> YOUR_PUBLIC_KEY:",
            color = colors.subtle,
            fontSize = 14.sp,
            fontFamily = theme.typography.fontFamily
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surfaceVariant)
                .padding(12.dp)
        ) {
            Text(
                text = myPubKey,
                color = colors.onSurface,
                fontSize = 14.sp,
                fontFamily = theme.typography.fontFamily
            )
        }
        Text(
            text = if (copied) "[ KEY_COPIED_SUCCESS ]" else "[ CLICK_TO_COPY ]",
            color = if (copied) colors.online else colors.primary,
            fontSize = 18.sp,
            fontFamily = theme.typography.fontFamily,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.clickable { onCopy() }
        )
    }
}

@Composable
private fun EnterKeyTab(
    inputKey: String,
    contactName: String,
    onKeyChange: (String) -> Unit,
    onNameChange: (String) -> Unit,
    onAdd: () -> Unit,
    theme: KharonTheme,
) {
    val colors = theme.colors
    val keyValid = inputKey.trim().length == 44

    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Column {
            Text("> NODE_KEY:", color = colors.subtle, fontSize = 14.sp)
            BasicTextField(
                value = inputKey,
                onValueChange = onKeyChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surfaceVariant)
                    .padding(12.dp),
                textStyle = TextStyle(color = colors.onSurface, fontSize = 18.sp, fontFamily = theme.typography.fontFamily),
                decorationBox = { inner ->
                    if (inputKey.isEmpty()) Text("0x...", color = colors.subtle, fontSize = 18.sp)
                    inner()
                }
            )
        }

        Column {
            Text("> NODE_ALIAS:", color = colors.subtle, fontSize = 14.sp)
            BasicTextField(
                value = contactName,
                onValueChange = onNameChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surfaceVariant)
                    .padding(12.dp),
                textStyle = TextStyle(color = colors.onSurface, fontSize = 18.sp, fontFamily = theme.typography.fontFamily),
                singleLine = true,
                decorationBox = { inner ->
                    if (contactName.isEmpty()) Text("IDENTIFIER", color = colors.subtle, fontSize = 18.sp)
                    inner()
                }
            )
        }

        Text(
            text = if (keyValid && contactName.isNotEmpty()) "[ REGISTER_NODE ]" else "[ INVALID_DATA ]",
            color = if (keyValid && contactName.isNotEmpty()) colors.primary else colors.subtle,
            fontSize = 18.sp,
            fontFamily = theme.typography.fontFamily,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.clickable(enabled = keyValid && contactName.isNotEmpty()) { onAdd() }
        )
    }
}

@Composable
private fun QrTab(
    myPubKey: String,
    onScan: () -> Unit,
    theme: KharonTheme,
) {
    val colors = theme.colors
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        if (myPubKey.isNotEmpty()) {
            val qrBitmap = remember(myPubKey) { generateQr(myPubKey, 512) }
            qrBitmap?.let {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .size(240.dp)
                        .background(androidx.compose.ui.graphics.Color.White)
                        .padding(8.dp)
                )
            }
        }
        Text(
            text = "[ LAUNCH_SCANNER ]",
            color = colors.primary,
            fontSize = 18.sp,
            fontFamily = theme.typography.fontFamily,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.clickable { onScan() }
        )
    }
}

private fun generateQr(content: String, size: Int): Bitmap? {
    return try {
        val hints = mapOf(EncodeHintType.MARGIN to 1)
        val bits = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bmp.setPixel(x, y, if (bits[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
            }
        }
        bmp
    } catch (e: Exception) { null }
}       