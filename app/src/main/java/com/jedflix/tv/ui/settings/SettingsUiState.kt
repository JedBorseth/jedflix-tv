package com.jedflix.tv.ui.settings

import androidx.compose.ui.graphics.ImageBitmap
import com.jedflix.tv.data.settings.QualityProfile
import com.jedflix.tv.data.tmdb.HomeShelfConfig
import com.jedflix.tv.data.tmdb.HomeShelfLayout
import com.jedflix.tv.data.tmdb.HomeShelfPref

data class SettingsUiState(
    val apiKey: String = "",
    val savedApiKey: String = "",
    val dirty: Boolean = false,
    val qrPairing: QrPairingUi = QrPairingUi.Hidden,
    val homeShelves: List<HomeShelfPref> = HomeShelfLayout.resolve(HomeShelfConfig()),
    val pickedShelfId: String? = null,
    val qualityProfile: QualityProfile = QualityProfile.Max,
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
