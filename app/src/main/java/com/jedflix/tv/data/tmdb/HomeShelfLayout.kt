package com.jedflix.tv.data.tmdb

/** Saved Home catalog Shelf order and visibility. Empty order means factory defaults. */
data class HomeShelfConfig(
    val order: List<String> = emptyList(),
    val hidden: Set<String> = emptySet(),
)

data class HomeShelfPref(
    val id: String,
    val title: String,
    val visible: Boolean,
)

/**
 * Resolves Home catalog Shelf order/visibility. Personal Shelves (Continue Watching,
 * My List, Watch History) and Trending Now are not part of this list. Trending Now
 * is always the first rail on Home and cannot be hidden or reordered.
 */
object HomeShelfLayout {
    fun factorySpecs(): List<ShelfSpec> =
        CatalogShelves.forSection(CatalogSection.HOME).filterNot(::isPinned)

    fun resolve(
        config: HomeShelfConfig,
        factory: List<ShelfSpec> = factorySpecs(),
    ): List<HomeShelfPref> {
        val editable = factory.filterNot(::isPinned)
        val byId = editable.associateBy { it.id }
        val known = byId.keys
        val ordered = LinkedHashSet<String>()
        for (id in config.order) {
            if (id in known) ordered.add(id)
        }
        for (spec in editable) {
            if (spec.id !in ordered) ordered.add(spec.id)
        }
        return ordered.map { id ->
            val spec = byId.getValue(id)
            HomeShelfPref(id = spec.id, title = spec.title, visible = spec.id !in config.hidden)
        }
    }

    fun toConfig(prefs: List<HomeShelfPref>): HomeShelfConfig = HomeShelfConfig(
        order = prefs.map { it.id }.filterNot { it == CatalogShelves.TRENDING_HOME },
        hidden = prefs.filterNot { it.visible }.map { it.id }
            .filterNot { it == CatalogShelves.TRENDING_HOME }
            .toSet(),
    )

    fun fetchIds(
        prefs: List<HomeShelfPref>,
        billboardId: String = CatalogShelves.TRENDING_HOME,
    ): Set<String> = prefs.filter { it.visible }.map { it.id }.toSet() + billboardId

    fun arrangeRows(rows: List<CatalogRow>, prefs: List<HomeShelfPref>): List<CatalogRow> {
        val byId = rows.associateBy { it.id }
        val rest = prefs.filter { it.visible }.mapNotNull { byId[it.id] }
            .filter { it.id != CatalogShelves.TRENDING_HOME }
        return listOfNotNull(byId[CatalogShelves.TRENDING_HOME]) + rest
    }

    fun pinTrending(rows: List<CatalogRow>): List<CatalogRow> {
        val trending = rows.firstOrNull { it.id == CatalogShelves.TRENDING_HOME } ?: return rows
        return listOf(trending) + rows.filter { it.id != CatalogShelves.TRENDING_HOME }
    }

    fun toggleVisible(prefs: List<HomeShelfPref>, id: String): List<HomeShelfPref> {
        if (id == CatalogShelves.TRENDING_HOME) return prefs
        return prefs.map { if (it.id == id) it.copy(visible = !it.visible) else it }
    }

    /** How many Home shelves Settings shows before Show all. */
    const val SETTINGS_PREVIEW_COUNT = 4

    fun settingsList(
        prefs: List<HomeShelfPref>,
        expanded: Boolean,
        previewCount: Int = SETTINGS_PREVIEW_COUNT,
    ): List<HomeShelfPref> {
        val editable = prefs.filterNot { it.id == CatalogShelves.TRENDING_HOME }
        if (expanded || editable.size <= previewCount) return editable
        return editable.take(previewCount)
    }

    fun settingsNeedsShowAll(
        prefs: List<HomeShelfPref>,
        previewCount: Int = SETTINGS_PREVIEW_COUNT,
    ): Boolean = prefs.count { it.id != CatalogShelves.TRENDING_HOME } > previewCount

    fun move(prefs: List<HomeShelfPref>, id: String, delta: Int): List<HomeShelfPref> {
        if (id == CatalogShelves.TRENDING_HOME) return prefs
        val index = prefs.indexOfFirst { it.id == id }
        if (index < 0) return prefs
        val target = index + delta
        if (target !in prefs.indices) return prefs
        if (prefs.getOrNull(target)?.id == CatalogShelves.TRENDING_HOME) return prefs
        val mutable = prefs.toMutableList()
        val item = mutable.removeAt(index)
        mutable.add(target, item)
        return mutable
    }

    private fun isPinned(spec: ShelfSpec): Boolean = spec.id == CatalogShelves.TRENDING_HOME
}
