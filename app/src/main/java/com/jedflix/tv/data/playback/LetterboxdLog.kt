package com.jedflix.tv.data.playback

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/** Letterboxd deep links so a phone can log the title currently playing. */
object LetterboxdLog {
    fun url(title: String): String {
        val encoded = URLEncoder.encode(title.trim(), StandardCharsets.UTF_8.name())
            .replace("+", "%20")
        return "letterboxd://x-callback-url/log?name=$encoded"
    }
}
