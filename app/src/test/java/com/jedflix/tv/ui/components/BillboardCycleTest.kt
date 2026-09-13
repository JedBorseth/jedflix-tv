package com.jedflix.tv.ui.components

import com.jedflix.tv.data.tmdb.MediaTitle
import com.jedflix.tv.data.tmdb.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BillboardCycleTest {

    @Test
    fun emptyOrSingleHasNoNext() {
        assertNull(BillboardCycle.next(emptyList(), "movie-1"))
        assertNull(BillboardCycle.next(listOf(title(1)), "movie-1"))
    }

    @Test
    fun walksThenWraps() {
        val featured = listOf(title(1), title(2), title(3))
        assertEquals("movie-2", BillboardCycle.next(featured, "movie-1")?.key)
        assertEquals("movie-3", BillboardCycle.next(featured, "movie-2")?.key)
        assertEquals("movie-1", BillboardCycle.next(featured, "movie-3")?.key)
    }

    @Test
    fun unknownCurrentStartsAtFirst() {
        val featured = listOf(title(4), title(5))
        assertEquals("movie-4", BillboardCycle.next(featured, "movie-99")?.key)
    }

    @Test
    fun panMovesFromLeftToRight() {
        val width = 1920f
        val left = BillboardCycle.translationX(width, 0f)
        val right = BillboardCycle.translationX(width, 1f)
        assertEquals(width * (BillboardCycle.PAN_SCALE - 1f) / 2f, left, 0.01f)
        assertEquals(-left, right, 0.01f)
    }

    private fun title(id: Int) = MediaTitle(
        id = id,
        mediaType = MediaType.MOVIE,
        title = "Title $id",
        overview = "",
        posterUrl = "/x",
        backdropUrl = "/b",
        year = null,
        rating = null,
        genres = emptyList(),
    )
}
