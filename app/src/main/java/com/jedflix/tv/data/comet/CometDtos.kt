package com.jedflix.tv.data.comet

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Stremio stream resource as returned by Comet's `/stream/{type}/{id}.json`. */
@Serializable
data class CometStreamsResponse(
    val streams: List<CometStreamDto> = emptyList(),
)

@Serializable
data class CometStreamDto(
    val name: String? = null,
    val description: String? = null,
    val url: String? = null,
    val infoHash: String? = null,
    val behaviorHints: CometBehaviorHintsDto? = null,
)

@Serializable
data class CometBehaviorHintsDto(
    val filename: String? = null,
    val videoSize: Long? = null,
    val bingeGroup: String? = null,
)

/** Subset of Comet's `ConfigModel`; every field has a server-side default so we only send what we set. */
@Serializable
data class CometConfigDto(
    val debridServices: List<CometDebridServiceDto>,
    val cachedOnly: Boolean,
    val sortCachedUncachedTogether: Boolean,
    val enableTorrent: Boolean,
    val removeTrash: Boolean,
    val deduplicateStreams: Boolean,
    val maxResultsPerResolution: Int,
    val maxSize: Long,
    val languages: CometLanguagesDto,
    val options: CometOptionsDto,
    val rtnSettings: CometRtnSettingsDto,
)

@Serializable
data class CometLanguagesDto(
    val required: List<String>,
)

@Serializable
data class CometOptionsDto(
    @SerialName("remove_unknown_languages") val removeUnknownLanguages: Boolean,
    @SerialName("allow_english_in_languages") val allowEnglishInLanguages: Boolean,
)

@Serializable
data class CometDebridServiceDto(
    val service: String,
    val apiKey: String,
)

@Serializable
data class CometRtnSettingsDto(
    @SerialName("custom_ranks") val customRanks: CometCustomRanksDto,
)

@Serializable
data class CometCustomRanksDto(
    val audio: CometAudioRanksDto,
)

@Serializable
data class CometAudioRanksDto(
    @SerialName("dts_lossless") val dtsLossless: CometCustomRankDto,
)

@Serializable
data class CometCustomRankDto(
    val fetch: Boolean,
)

fun cometAddonConfig(apiKey: String): CometConfigDto = CometConfigDto(
    debridServices = listOf(CometDebridServiceDto(service = "realdebrid", apiKey = apiKey)),
    cachedOnly = true,
    sortCachedUncachedTogether = false,
    enableTorrent = false,
    removeTrash = true,
    deduplicateStreams = true,
    // Comet uses zero for unlimited. Leave ranking to Comet and retain every fallback.
    maxResultsPerResolution = 0,
    maxSize = 0,
    languages = CometLanguagesDto(required = listOf("en")),
    options = CometOptionsDto(removeUnknownLanguages = true, allowEnglishInLanguages = false),
    rtnSettings = CometRtnSettingsDto(
        customRanks = CometCustomRanksDto(
            audio = CometAudioRanksDto(dtsLossless = CometCustomRankDto(fetch = false)),
        ),
    ),
)
