package com.jedflix.tv.ui.player

internal enum class PlayerFocusRequest {
    Unchanged,
    Play,
    Skip,
    Menu,
    UpNext,
    Transport,
}

/** Focus when chrome, menus, or up-next change. Skip is handled separately so it cannot steal the timeline. */
internal fun playerFocusWhenChromeChanges(
    error: Boolean,
    upNextOpen: Boolean,
    menuOpen: Boolean,
    controlsVisible: Boolean,
): PlayerFocusRequest = when {
    error -> PlayerFocusRequest.Unchanged
    upNextOpen -> PlayerFocusRequest.UpNext
    menuOpen -> PlayerFocusRequest.Menu
    controlsVisible -> PlayerFocusRequest.Play
    else -> PlayerFocusRequest.Transport
}

/** Auto-focus Skip Intro only when chrome is hidden. Scrubbing with chrome open keeps the timeline. */
internal fun playerFocusWhenSkipChanges(
    error: Boolean,
    upNextOpen: Boolean,
    menuOpen: Boolean,
    controlsVisible: Boolean,
    skipVisible: Boolean,
): PlayerFocusRequest {
    if (error || upNextOpen || menuOpen || controlsVisible || !skipVisible) {
        return PlayerFocusRequest.Unchanged
    }
    return PlayerFocusRequest.Skip
}
