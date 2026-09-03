package com.jedflix.tv.data.rdpairing

import java.security.SecureRandom

private const val CODE_BYTES = 32
private const val CODE_MIN_LENGTH = 16
private const val CODE_MAX_LENGTH = 128
private val CODE_CHARSET = Regex("^[A-Za-z0-9_-]+$")
private const val HEX = "0123456789abcdef"

/**
 * High-entropy pairing code for the TV Real-Debrid transfer slot.
 * 32 random bytes as lowercase hex (64 characters of `[0-9a-f]`).
 */
fun generatePairingCode(random: SecureRandom = SecureRandom()): String {
    val bytes = ByteArray(CODE_BYTES)
    random.nextBytes(bytes)
    val code = buildString(bytes.size * 2) {
        for (b in bytes) {
            val i = b.toInt() and 0xff
            append(HEX[i ushr 4])
            append(HEX[i and 0x0f])
        }
    }
    check(code.length in CODE_MIN_LENGTH..CODE_MAX_LENGTH && CODE_CHARSET.matches(code)) {
        "Generated pairing code failed charset/length checks"
    }
    return code
}

fun pairingPageUrl(code: String): String = "$PAGE_BASE$code"

const val PAGE_BASE = "https://borseth.ddns.net/tv/rd/"
const val API_BASE = "https://borseth.ddns.net/backend/api/v1/tv/rd-key/"
