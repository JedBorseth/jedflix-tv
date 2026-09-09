package com.jedflix.tv.ui.focus

import androidx.compose.foundation.focusGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer

fun Modifier.optionalFocusRequester(requester: FocusRequester?): Modifier =
    if (requester != null) focusRequester(requester) else this

/** Intercept D-pad enter so a rail lands on its own item, not the previous rail's X. */
fun Modifier.independentRail(enter: FocusRequester): Modifier =
    focusGroup()
        .focusRestorer(enter)
        .focusProperties {
            onEnter = { enter.requestFocus() }
        }

/**
 * Explicit neighbors beat spatial search. [FocusRequester.Cancel] on Right keeps
 * the last item from leaking to the profile button.
 */
fun Modifier.railItemFocus(
    up: FocusRequester? = null,
    down: FocusRequester? = null,
    blockRight: Boolean = false,
): Modifier = focusProperties {
    if (up != null) this.up = up
    if (down != null) this.down = down
    if (blockRight) right = FocusRequester.Cancel
}

@Composable
fun rememberRailListState(stateKey: String): LazyListState =
    rememberSaveable(stateKey, saver = LazyListState.Saver) { LazyListState() }
