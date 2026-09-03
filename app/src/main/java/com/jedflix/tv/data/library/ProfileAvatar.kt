package com.jedflix.tv.data.library

/** Preset profile pictures — packed ARGB colors until real artwork is added. */
data class ProfileAvatar(
    val key: String,
    val colorArgb: Int,
)

object ProfileAvatars {
    val all: List<ProfileAvatar> = listOf(
        ProfileAvatar("crimson", 0xFFE50914.toInt()),
        ProfileAvatar("azure", 0xFF2563EB.toInt()),
        ProfileAvatar("emerald", 0xFF059669.toInt()),
        ProfileAvatar("amber", 0xFFD97706.toInt()),
        ProfileAvatar("violet", 0xFF7C3AED.toInt()),
        ProfileAvatar("rose", 0xFFE11D48.toInt()),
        ProfileAvatar("teal", 0xFF0D9488.toInt()),
        ProfileAvatar("slate", 0xFF64748B.toInt()),
        ProfileAvatar("orange", 0xFFEA580C.toInt()),
        ProfileAvatar("indigo", 0xFF4F46E5.toInt()),
    )

    val defaultKey: String = all.first().key

    fun colorArgb(key: String): Int =
        all.firstOrNull { it.key == key }?.colorArgb ?: all.first().colorArgb
}
