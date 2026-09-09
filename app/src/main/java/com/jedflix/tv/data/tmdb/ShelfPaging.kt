package com.jedflix.tv.data.tmdb

/** Two TMDB pages per Shelf; the second is fetched when focus reaches the 8th-last Title. */
object ShelfPaging {
    const val PAGE_SIZE = 20
    const val MAX_PAGES = 2
    const val PREFETCH_FROM_END = 8

    fun shouldPrefetch(focusedIndex: Int, itemCount: Int, hasMore: Boolean): Boolean {
        if (!hasMore || itemCount <= 0) return false
        return focusedIndex >= itemCount - PREFETCH_FROM_END
    }

    fun nextPage(currentPage: Int, pageSize: Int, knownTotal: Int? = null, loadedCount: Int): Int? {
        if (currentPage >= MAX_PAGES) return null
        if (knownTotal != null && loadedCount >= knownTotal) return null
        if (pageSize < PAGE_SIZE) return null
        return currentPage + 1
    }
}
