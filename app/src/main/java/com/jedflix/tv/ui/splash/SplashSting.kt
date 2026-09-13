package com.jedflix.tv.ui.splash

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import com.jedflix.tv.R

/**
 * Plays the launch sting once per activity. Lives above the splash route so it can keep going
 * after Home replaces the wordmark.
 */
@Composable
fun SplashStingEffect() {
    val context = LocalContext.current
    DisposableEffect(Unit) {
        val player = startSplashSting(context.applicationContext)
        onDispose { player.releaseQuietly() }
    }
}

private fun startSplashSting(context: Context): MediaPlayer {
    val attrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()
    val fd = context.resources.openRawResourceFd(R.raw.splash_sting)
    return MediaPlayer().apply {
        setAudioAttributes(attrs)
        setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
        fd.close()
        setVolume(1f, 1f)
        setOnErrorListener { _, _, _ -> true }
        prepare()
        start()
    }
}

private fun MediaPlayer.releaseQuietly() {
    runCatching { if (isPlaying) stop() }
    runCatching { release() }
}
