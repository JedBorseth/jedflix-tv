package com.jedflix.tv.data.comet

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

class CometConfigTest {
    private val payload = Json.parseToJsonElement(
        Json.encodeToString(CometConfigDto.serializer(), cometAddonConfig("test-key")),
    ).jsonObject

    @Test
    fun requestsUnlimitedResultsWithoutASizeCap() {
        assertEquals("0", payload.getValue("maxResultsPerResolution").jsonPrimitive.content)
        assertEquals("0", payload.getValue("maxSize").jsonPrimitive.content)
    }

    @Test
    fun requiresEnglishAtTheTopLevelUsedByComet() {
        val languages = payload.getValue("languages").jsonObject
        assertEquals(listOf("en"), languages.getValue("required").jsonArray.map { it.jsonPrimitive.content })
        val options = payload.getValue("options").jsonObject
        assertEquals("true", options.getValue("remove_unknown_languages").jsonPrimitive.content)
        assertEquals("false", options.getValue("allow_english_in_languages").jsonPrimitive.content)
    }

    @Test
    fun keepsCachedDebridOnlyTrashRemovalAndDeduplication() {
        assertEquals("true", payload.getValue("cachedOnly").jsonPrimitive.content)
        assertEquals("false", payload.getValue("sortCachedUncachedTogether").jsonPrimitive.content)
        assertEquals("false", payload.getValue("enableTorrent").jsonPrimitive.content)
        assertEquals("true", payload.getValue("removeTrash").jsonPrimitive.content)
        assertEquals("true", payload.getValue("deduplicateStreams").jsonPrimitive.content)
        val service = payload.getValue("debridServices").jsonArray.single().jsonObject
        assertEquals("realdebrid", service.getValue("service").jsonPrimitive.content)
        assertEquals("test-key", service.getValue("apiKey").jsonPrimitive.content)
    }
}
