package com.jedflix.tv.ui.settings

import androidx.compose.ui.graphics.ImageBitmap

data class SettingsUiState(
    val apiKey: String = "",
    val savedApiKey: String = "",
    val dirty: Boolean = false,
    val qrPairing: QrPairingUi = QrPairingUi.Hidden,
)

sealed interface QrPairingUi {
    data object Hidden : QrPairingUi
    data object Opening : QrPairingUi
    data class Waiting(val qr: ImageBitmap) : QrPairingUi
    data object Failed : QrPairingUi
    data object Expired : QrPairingUi
}

val QrPairingUi.isExpanded: Boolean
    get() = this !is QrPairingUi.Hidden
