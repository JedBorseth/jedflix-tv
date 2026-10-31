package com.jedflix.tv.data.migration

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** The Stremio-based Jedflix app that legacy com.jedflix.tv users move to. */
object NewJedflix {
    const val PACKAGE = "com.jedflix.stremio"
    const val REPOSITORY = "JedBorseth/jedflix-tv-stremio"
    const val LATEST_RELEASE_API = "https://api.github.com/repos/$REPOSITORY/releases/latest"
    const val RELEASE_DOWNLOADS = "https://github.com/$REPOSITORY/releases/download/"

    /** Every Jedflix APK is signed with this certificate. Changing it breaks the migration. */
    const val SIGNING_CERT_SHA256 = "64a76fdcb17b35f8eabea37f00c0d03f04ccd49404260bbc62334061f7d3eb97"

    /** First release with the separate Jedflix application ID. */
    const val MIN_VERSION_CODE = 34194312L
}

class MigrationException(message: String) : Exception(message)

/** One verified download target for this device's processor. */
data class NewJedflixArtifact(
    val versionName: String,
    val versionCode: Long,
    val abi: String,
    val url: String,
    val sha256: String,
    val size: Long,
)

/** Reads the latest GitHub release and locates its update manifest. */
object NewJedflixRelease {
    private val TAG = Regex("v[0-9]+\\.[0-9]+\\.[0-9]+")

    /** Returns the manifest URL and release tag. Drafts and prereleases are rejected. */
    fun manifestSource(releaseJson: String): Pair<String, String> {
        val release = parseObject(releaseJson)
        if (release["draft"]?.jsonPrimitive?.booleanOrNull == true ||
            release["prerelease"]?.jsonPrimitive?.booleanOrNull == true
        ) throw MigrationException("The new Jedflix has no stable release yet.")
        val tag = release.string("tag_name")
        if (!TAG.matches(tag)) throw MigrationException("The new Jedflix release has an unexpected tag.")
        val expected = NewJedflix.RELEASE_DOWNLOADS + tag + "/jedflix-app-update.json"
        val assets = release["assets"]?.jsonArray.orEmpty().map { it.asObject() }
        val manifest = assets.firstOrNull { it.string("name") == "jedflix-app-update.json" }
            ?: throw MigrationException("The new Jedflix release has no update manifest.")
        if (manifest.string("browser_download_url") != expected) {
            throw MigrationException("The new Jedflix release has an unexpected manifest address.")
        }
        return expected to tag
    }
}

/** Validates the published manifest and picks the artifact for this device. */
object NewJedflixManifest {
    private val VERSION = Regex("[0-9]+\\.[0-9]+\\.[0-9]+")
    private val SHA256 = Regex("[0-9a-f]{64}")
    private const val MAX_SIZE = 1L shl 30

    fun parse(text: String, tag: String, abis: Array<String>, sdk: Int): NewJedflixArtifact {
        val root = parseObject(text)
        if (root.int("schemaVersion") != 1) throw MigrationException("The new Jedflix manifest version is not supported.")
        if (root.string("packageName") != NewJedflix.PACKAGE) {
            throw MigrationException("The new Jedflix manifest is for a different app.")
        }
        val version = root.string("versionName")
        if (!VERSION.matches(version) || tag != "v$version") {
            throw MigrationException("The new Jedflix manifest version does not match its release.")
        }
        val code = root.long("versionCode")
        if (code < NewJedflix.MIN_VERSION_CODE || code > 2_100_000_000L) {
            throw MigrationException("The new Jedflix manifest has an invalid version code.")
        }
        if (root.int("minSdk") > sdk) throw MigrationException("The new Jedflix needs a newer Android version.")
        val artifacts = root["artifacts"]?.asObject() ?: throw MigrationException("The new Jedflix manifest has no files.")
        val abi = abis.firstOrNull { artifacts.containsKey(it) }
            ?: throw MigrationException(
                "Jedflix $version has no matching download for this TV.\n" +
                    "TV: ${abis.joinToString().ifEmpty { "unknown" }}. " +
                    "Available: ${artifacts.keys.sorted().joinToString().ifEmpty { "none" }}.",
            )
        val entry = artifacts.getValue(abi).asObject()
        val url = entry.string("url")
        val sha256 = entry.string("sha256")
        val size = entry.long("size")
        if (url != NewJedflix.RELEASE_DOWNLOADS + tag + "/jedflix-app-$version-$abi.apk") {
            throw MigrationException("The new Jedflix file address is not expected.")
        }
        if (!SHA256.matches(sha256) || size <= 0 || size > MAX_SIZE) {
            throw MigrationException("The new Jedflix file metadata is invalid.")
        }
        return NewJedflixArtifact(version, code, abi, url, sha256, size)
    }
}

private fun parseObject(text: String): JsonObject = try {
    Json.parseToJsonElement(text).asObject()
} catch (error: MigrationException) {
    throw error
} catch (error: Exception) {
    throw MigrationException("Invalid data from the new Jedflix release.")
}

private fun kotlinx.serialization.json.JsonElement.asObject(): JsonObject =
    this as? JsonObject ?: throw MigrationException("Invalid data from the new Jedflix release.")

private fun JsonObject.primitive(key: String): JsonPrimitive =
    this[key] as? JsonPrimitive ?: throw MigrationException("Invalid data from the new Jedflix release.")

private fun JsonObject.string(key: String): String = primitive(key).content

private fun JsonObject.int(key: String): Int =
    primitive(key).content.toIntOrNull() ?: throw MigrationException("Invalid data from the new Jedflix release.")

private fun JsonObject.long(key: String): Long =
    primitive(key).content.toLongOrNull() ?: throw MigrationException("Invalid data from the new Jedflix release.")
