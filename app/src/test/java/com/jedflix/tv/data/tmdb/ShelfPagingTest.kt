package com.jedflix.tv.data.tmdb

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShelfPagingTest {

    @Test
    fun prefetchStartsAtEighthLastItem() {
        assertFalse(ShelfPaging.shouldPrefetch(11, 20, hasMore = true))
        assertTrue(ShelfPaging.shouldPrefetch(12, 20, hasMore = true))
        assertTrue(ShelfPaging.shouldPrefetch(19, 20, hasMore = true))
    }

    @Test
    fun prefetchDoesNothingWhenComplete() {
        assertFalse(ShelfPaging.shouldPrefetch(19, 20, hasMore = false))
        assertFalse(ShelfPaging.shouldPrefetch(0, 0, hasMore = true))
    }

    @Test
    fun secondPageOnlyWhenFirstPageIsFull() {
        assertEquals(2, ShelfPaging.nextPage(currentPage = 1, pageSize = 20, loadedCount = 20))
        assertNull(ShelfPaging.nextPage(currentPage = 1, pageSize = 17, loadedCount = 17))
        assertNull(ShelfPaging.nextPage(currentPage = 2, pageSize = 20, loadedCount = 40))
        assertEquals(2, ShelfPaging.nextPage(currentPage = 1, pageSize = 20, knownTotal = 21, loadedCount = 20))
        assertNull(ShelfPaging.nextPage(currentPage = 1, pageSize = 20, knownTotal = 20, loadedCount = 20))
    }
}
