package com.jedflix.tv.data.settings

enum class QualityProfile(val stored: String) {
    Max("max"),
    Medium("medium"),
    Low("low"),
    ;

    companion object {
        fun fromStored(value: String?): QualityProfile =
            entries.firstOrNull { it.stored.equals(value, ignoreCase = true) } ?: Max
    }
}
