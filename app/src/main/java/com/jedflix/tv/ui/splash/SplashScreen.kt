package com.jedflix.tv.ui.splash

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.sp
import com.jedflix.tv.ui.components.JedflixWordmark
import com.jedflix.tv.ui.theme.Zinc950
import kotlinx.coroutines.delay

private const val INTRO_MILLIS = 1300
private const val HOLD_MILLIS = 250L
private const val EXIT_MILLIS = 350

/**
 * Netflix-style launch: letters of the wordmark zoom in one after another, glow, then the whole
 * screen fades into Home. OK / Enter / Back skip straight to Home.
 */
@Composable
fun SplashScreen(onFinished: () -> Unit) {
    var finished by remember { mutableStateOf(false) }
    val finish = {
        if (!finished) {
            finished = true
            onFinished()
        }
    }

    val intro = remember { Animatable(0f) }
    val exit = remember { Animatable(0f) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        runCatching { focusRequester.requestFocus() }
        intro.animateTo(1f, tween(INTRO_MILLIS, easing = LinearEasing))
        delay(HOLD_MILLIS)
        exit.animateTo(1f, tween(EXIT_MILLIS))
        finish()
    }

    BackHandler { finish() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Zinc950)
            .testTag("splash")
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { event ->
                val isSelect = event.key == Key.DirectionCenter ||
                    event.key == Key.Enter ||
                    event.key == Key.NumPadEnter
                if (event.type == KeyEventType.KeyUp && isSelect) {
                    finish()
                    true
                } else {
                    false
                }
            }
            .graphicsLayer { alpha = 1f - exit.value },
        contentAlignment = Alignment.Center,
    ) {
        JedflixWordmark(progress = intro.value, fontSize = 96.sp)
    }
}
