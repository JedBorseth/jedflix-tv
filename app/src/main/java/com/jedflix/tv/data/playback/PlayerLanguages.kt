package com.jedflix.tv.data.playback

import java.util.Locale

/** Language codes and labels for in-band audio/text tracks. */
object PlayerLanguages {
    const val ENGLISH = "en"
    const val UNKNOWN = "und"

    fun normalize(code: String?): String? {
        val raw = code?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val primary = raw.lowercase(Locale.US).replace('_', '-').substringBefore('-')
        if (primary == UNKNOWN || primary == "unk") return UNKNOWN
        val mapped = ISO3_TO_2[primary] ?: primary
        return mapped.takeIf { it.isNotEmpty() }
    }

    fun matches(formatLanguage: String?, preferred: String): Boolean {
        val left = normalize(formatLanguage) ?: return false
        val right = normalize(preferred) ?: return false
        if (left == UNKNOWN || right == UNKNOWN) return false
        return left == right
    }

    fun displayName(code: String?): String {
        val normalized = normalize(code) ?: return "Unknown"
        if (normalized == UNKNOWN) return "Unknown"
        val locale = Locale.Builder().setLanguage(normalized).build()
        val name = locale.getDisplayLanguage(Locale.US)
        return name.takeIf { it.isNotBlank() && !it.equals(normalized, ignoreCase = true) }
            ?: normalized.uppercase(Locale.US)
    }

    private val ISO3_TO_2 = mapOf(
        "eng" to "en",
        "jpn" to "ja",
        "jap" to "ja",
        "spa" to "es",
        "fre" to "fr",
        "fra" to "fr",
        "ger" to "de",
        "deu" to "de",
        "ita" to "it",
        "por" to "pt",
        "rus" to "ru",
        "kor" to "ko",
        "chi" to "zh",
        "zho" to "zh",
        "ara" to "ar",
        "hin" to "hi",
        "nld" to "nl",
        "dut" to "nl",
        "pol" to "pl",
        "tur" to "tr",
        "swe" to "sv",
        "nor" to "no",
        "dan" to "da",
        "fin" to "fi",
        "ces" to "cs",
        "cze" to "cs",
        "hun" to "hu",
        "tha" to "th",
        "vie" to "vi",
        "ukr" to "uk",
        "heb" to "he",
        "ind" to "id",
        "may" to "ms",
        "msa" to "ms",
        "ron" to "ro",
        "rum" to "ro",
        "ell" to "el",
        "gre" to "el",
    )
}

object AudioFormatLabel {
    fun format(
        languageCode: String?,
        codecs: String?,
        mimeType: String?,
        channelCount: Int,
        trackLabel: String? = null,
    ): String {
        val language = PlayerLanguages.displayName(languageCode)
        val codec = codecLabel(codecs, mimeType)
        val channels = channelLabel(channelCount)
        val extras = listOfNotNull(codec, channels).joinToString(" ")
        val base = if (extras.isBlank()) language else "$language  ·  $extras"
        val extraLabel = trackLabel?.trim()?.takeIf { it.isNotEmpty() && !it.equals(language, ignoreCase = true) }
        return if (extraLabel != null && extraLabel !in base) "$base  ·  $extraLabel" else base
    }

    fun codecLabel(codecs: String?, mimeType: String?): String? {
        val src = "${codecs.orEmpty()} ${mimeType.orEmpty()}".lowercase(Locale.US)
        return when {
            "atmos" in src || "ec+3" in src || "joc" in src -> "Atmos"
            "truehd" in src || "mlp" in src -> "TrueHD"
            "eac3" in src || "ec-3" in src || "eac-3" in src -> "DD+"
            "ac-3" in src || "ac3" in src -> "Dolby Digital"
            "dts-hd" in src || "dtshd" in src -> "DTS-HD"
            "dts" in src || "dca" in src -> "DTS"
            "opus" in src -> "Opus"
            "flac" in src -> "FLAC"
            "vorbis" in src -> "Vorbis"
            "pcm" in src -> "PCM"
            "aac" in src || "mp4a" in src -> "AAC"
            else -> codecs
                ?.substringBefore('.')
                ?.takeIf { it.isNotBlank() }
                ?.uppercase(Locale.US)
        }
    }

    fun channelLabel(channelCount: Int): String? = when (channelCount) {
        1 -> "Mono"
        2 -> "Stereo"
        6, 7 -> "5.1"
        8, 9 -> "7.1"
        else -> if (channelCount > 0) "$channelCount ch" else null
    }
}

object TextTrackLabel {
    fun format(languageCode: String?, trackLabel: String?): String {
        val language = PlayerLanguages.displayName(languageCode)
        val extra = trackLabel?.trim()?.takeIf { hint ->
            hint.isNotEmpty() &&
                !hint.equals(language, ignoreCase = true) &&
                !hint.equals(languageCode, ignoreCase = true)
        } ?: return language
        return "$language  ·  $extra"
    }
}

object PlaybackClock {
    fun formatMs(ms: Long): String {
        val clamped = ms.coerceAtLeast(0L)
        val totalSec = clamped / 1_000L
        val hours = totalSec / 3_600L
        val minutes = (totalSec % 3_600L) / 60L
        val seconds = totalSec % 60L
        return if (hours > 0L) {
            "$hours:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
        } else {
            "$minutes:${seconds.toString().padStart(2, '0')}"
        }
    }
}
