package com.jedflix.tv.ui.focus

import com.jedflix.tv.data.tmdb.CatalogRow
import com.jedflix.tv.data.tmdb.MediaTitle
import com.jedflix.tv.data.tmdb.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RailRestoreTest {

    @Test
    fun keepsSavedKeyWhenStillPresent() {
        assertEquals("tv-2", RailRestore.itemKey("tv-2", listOf("tv-1", "tv-2", "tv-3")))
    }

    @Test
    fun fallsBackToFirstWhenSavedKeyIsGone() {
        assertEquals("tv-1", RailRestore.itemKey("tv-99", listOf("tv-1", "tv-2")))
    }

    @Test
    fun firstVisitUsesFirstKey() {
        assertEquals("tv-1", RailRestore.itemKey(null, listOf("tv-1", "tv-2")))
    }

    @Test
    fun emptyRailHasNoKey() {
        assertEquals(null, RailRestore.itemKey("tv-1", emptyList()))
    }

    @Test
    fun catalogRestoresBillboardPlay() {
        val target = RailRestore.catalogTarget(null, RailRestore.BILLBOARD_PLAY, listOf(row("a", "movie-1")))
        assertEquals(RailRestore.CatalogTarget.BillboardPlay, target)
    }

    @Test
    fun catalogColdStartIsFirstTitle() {
        val target = RailRestore.catalogTarget(null, null, listOf(row("a", "movie-1")))
        assertEquals(RailRestore.CatalogTarget.FirstTitle, target)
    }

    @Test
    fun catalogRestoresTitleByIdentity() {
        val rows = listOf(row("trending", "movie-1", "movie-2"), row("crave", "movie-8", "movie-9"))
        val target = RailRestore.catalogTarget("crave", "movie-9", rows)
        assertEquals(RailRestore.CatalogTarget.Title("crave", "movie-9"), target)
    }

    @Test
    fun catalogMissingTitleFallsBackToFirstInThatShelf() {
        val rows = listOf(row("crave", "movie-8", "movie-9"))
        val target = RailRestore.catalogTarget("crave", "movie-gone", rows)
        assertEquals(RailRestore.CatalogTarget.Title("crave", "movie-8"), target)
    }

    @Test
    fun catalogMissingShelfFallsBackToFirstTitle() {
        val target = RailRestore.catalogTarget("gone", "movie-1", listOf(row("crave", "movie-8")))
        assertTrue(target is RailRestore.CatalogTarget.FirstTitle)
    }

    private fun row(id: String, vararg keys: String): CatalogRow =
        CatalogRow(
            id = id,
            title = id,
            items = keys.map { key ->
                val (type, idPart) = key.split("-", limit = 2)
                MediaTitle(
                    id = idPart.toInt(),
                    mediaType = if (type == "tv") MediaType.TV else MediaType.MOVIE,
                    title = key,
                    overview = "",
                    posterUrl = "/x",
                    backdropUrl = null,
                    year = null,
                    rating = null,
                    genres = emptyList(),
                )
            },
        )
}
