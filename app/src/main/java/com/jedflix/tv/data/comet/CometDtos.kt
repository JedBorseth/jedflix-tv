package com.jedflix.tv.data.comet

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
    val enableTorrent: Boolean,
    val removeTrash: Boolean,
    val deduplicateStreams: Boolean,
    val maxResultsPerResolution: Int,
)

@Serializable
data class CometDebridServiceDto(
    val service: String,
    val apiKey: String,
)
