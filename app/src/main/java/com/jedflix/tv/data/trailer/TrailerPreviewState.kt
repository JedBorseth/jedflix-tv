package com.jedflix.tv.data.trailer

enum class TrailerPreviewPhase {
    Hidden,
    Preparing,
    Opening,
    Playing,
}

data class TrailerPreviewState(
    val titleKey: String? = null,
    val clipUrl: String? = null,
    val holdElapsed: Boolean = false,
    val playerReady: Boolean = false,
    val failed: Boolean = false,
    val phase: TrailerPreviewPhase = TrailerPreviewPhase.Hidden,
) {
    val visible: Boolean get() = phase == TrailerPreviewPhase.Opening || phase == TrailerPreviewPhase.Playing
}

sealed interface TrailerPreviewEvent {
    data class Focused(val titleKey: String) : TrailerPreviewEvent
    data object HoldElapsed : TrailerPreviewEvent
    data class ClipReady(val titleKey: String, val url: String) : TrailerPreviewEvent
    data class PlayerReady(val titleKey: String) : TrailerPreviewEvent
    data object PlayerFailed : TrailerPreviewEvent
    data object HoldExpiredUnready : TrailerPreviewEvent
    data object MorphFinished : TrailerPreviewEvent
    data object ClipEnded : TrailerPreviewEvent
    data object Reset : TrailerPreviewEvent
}

fun TrailerPreviewState.reduce(event: TrailerPreviewEvent): TrailerPreviewState = when (event) {
    is TrailerPreviewEvent.Focused -> {
        if (event.titleKey == titleKey && !failed && phase != TrailerPreviewPhase.Hidden) {
            this
        } else {
            TrailerPreviewState(titleKey = event.titleKey, phase = TrailerPreviewPhase.Preparing)
        }
    }
    TrailerPreviewEvent.HoldElapsed -> {
        if (phase != TrailerPreviewPhase.Preparing) this
        else copy(holdElapsed = true).maybeOpen()
    }
    is TrailerPreviewEvent.ClipReady -> {
        if (event.titleKey != titleKey || phase != TrailerPreviewPhase.Preparing) this
        else copy(clipUrl = event.url).maybeOpen()
    }
    is TrailerPreviewEvent.PlayerReady -> {
        if (event.titleKey != titleKey || phase != TrailerPreviewPhase.Preparing) this
        else copy(playerReady = true).maybeOpen()
    }
    TrailerPreviewEvent.PlayerFailed -> {
        if (titleKey == null) this
        else copy(failed = true, phase = TrailerPreviewPhase.Hidden, playerReady = false)
    }
    TrailerPreviewEvent.HoldExpiredUnready -> {
        if (phase != TrailerPreviewPhase.Preparing) this
        else copy(failed = true, phase = TrailerPreviewPhase.Hidden, playerReady = false)
    }
    TrailerPreviewEvent.MorphFinished -> {
        if (phase != TrailerPreviewPhase.Opening) this
        else copy(phase = TrailerPreviewPhase.Playing)
    }
    TrailerPreviewEvent.ClipEnded -> {
        if (phase != TrailerPreviewPhase.Playing) this
        else TrailerPreviewState()
    }
    TrailerPreviewEvent.Reset -> TrailerPreviewState()
}

private fun TrailerPreviewState.maybeOpen(): TrailerPreviewState =
    if (phase == TrailerPreviewPhase.Preparing && holdElapsed && playerReady && clipUrl != null) {
        copy(phase = TrailerPreviewPhase.Opening)
    } else {
        this
    }
