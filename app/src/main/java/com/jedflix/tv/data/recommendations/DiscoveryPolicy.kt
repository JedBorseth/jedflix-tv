package com.jedflix.tv.data.recommendations

import com.jedflix.tv.data.library.LibraryRows
import com.jedflix.tv.data.tmdb.Catalog
import com.jedflix.tv.data.tmdb.CatalogShelves
import com.jedflix.tv.data.tmdb.MediaTitle
import com.jedflix.tv.data.tmdb.MediaType
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Cheap local guard; recent digital releases require fresh server evidence. */
object DiscoveryPolicy {
    fun eligible(title: MediaTitle, today: LocalDate, verifiedKeys: Set<String> = emptySet()): Boolean {
        if (title.mediaType != MediaType.MOVIE) return true
        // The server verifies public release dates, which can precede a regional primary date.
        if (title.key in verifiedKeys) return true
        val date = title.releaseDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        if (date == null) return title.year?.toIntOrNull()?.let { it < today.year } ?: false
        if (date > today) return false
        return ChronoUnit.DAYS.between(date, today) >= 30
    }

    fun apply(
        catalog: Catalog,
        today: LocalDate = LocalDate.now(),
        verifiedKeys: Set<String> = emptySet(),
        dislikedKeys: Set<String> = emptySet(),
        focusedRowId: String? = null,
        focusedItemKey: String? = null,
    ): Catalog {
        val seen = HashSet<String>()
        val keepFocusedOwner = catalog.rows.any { row ->
            row.id == focusedRowId && row.id !in utilityRows && row.items.any { it.key == focusedItemKey }
        }
        val rows = catalog.rows.mapNotNull { row ->
            if (row.id in utilityRows) return@mapNotNull row
            val items = row.items.filter {
                it.key !in dislikedKeys && eligible(it, today, verifiedKeys) &&
                    if (keepFocusedOwner && it.key == focusedItemKey) row.id == focusedRowId
                    else seen.add(it.key)
            }
            row.copy(items = items).takeIf { items.isNotEmpty() }
        }
        // Filtering the billboard never changes its source to a provider/personal shelf.
        val trendingItems = rows.firstOrNull { it.id == CatalogShelves.TRENDING_HOME }?.items.orEmpty()
        val trendingByKey = trendingItems.associateBy { it.key }
        return catalog.copy(
            rows = rows,
            featured = catalog.featured.mapNotNull { trendingByKey[it.key] }
                .ifEmpty { trendingItems.take(8) },
        )
    }

    private val utilityRows = setOf(LibraryRows.CONTINUE_WATCHING, LibraryRows.MY_LIST, LibraryRows.WATCH_HISTORY)
}
