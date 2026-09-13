package com.jedflix.tv.data.backend

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Reachability of the JedFlix TV API (`server/`), as shown under Settings → About. */
sealed interface BackendHealthState {
    data object Unknown : BackendHealthState
    data object Checking : BackendHealthState

    /** `GET /health` answered `{"status":"ok"}`; [version] is the deployed git sha (or "dev"). */
    data class Online(val version: String) : BackendHealthState
    data object Offline : BackendHealthState
}

object BackendHealth {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Full `GET /health` URL for a configured base, or null when [baseUrl] is blank.
     * Trailing slashes on the base are tolerated: `https://h/tv-api/` → `https://h/tv-api/health`.
     */
    fun healthUrl(baseUrl: String): String? {
        val base = baseUrl.trim().trimEnd('/')
        if (base.isEmpty()) return null
        return "$base/health"
    }

    /**
     * Interprets a `/health` response. Only a 2xx with `status == "ok"` counts as online;
     * anything else (bad JSON, other status text, non-2xx) is offline.
     */
    fun parse(code: Int, body: String?): BackendHealthState {
        if (code !in 200..299) return BackendHealthState.Offline
        val dto = try {
            json.decodeFromString(HealthDto.serializer(), body.orEmpty())
        } catch (_: Exception) {
            return BackendHealthState.Offline
        }
        if (dto.status != "ok") return BackendHealthState.Offline
        return BackendHealthState.Online(version = dto.version?.takeIf { it.isNotBlank() } ?: "unknown")
    }
}

@Serializable
internal data class HealthDto(
    val status: String = "",
    val version: String? = null,
)
