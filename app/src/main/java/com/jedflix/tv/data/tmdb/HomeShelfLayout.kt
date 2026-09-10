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
 * My List, Watch History) are not part of this list.
 */
object HomeShelfLayout {
    fun factorySpecs(): List<ShelfSpec> = CatalogShelves.forSection(CatalogSection.HOME)

    fun resolve(
        config: HomeShelfConfig,
        factory: List<ShelfSpec> = factorySpecs(),
    ): List<HomeShelfPref> {
        val byId = factory.associateBy { it.id }
        val known = factory.map { it.id }.toSet()
        val ordered = LinkedHashSet<String>()
        for (id in config.order) {
            if (id in known) ordered.add(id)
        }
        for (spec in factory) {
            if (spec.id !in ordered) ordered.add(spec.id)
        }
        return ordered.map { id ->
            val spec = byId.getValue(id)
            HomeShelfPref(id = spec.id, title = spec.title, visible = spec.id !in config.hidden)
        }
    }

    fun toConfig(prefs: List<HomeShelfPref>): HomeShelfConfig = HomeShelfConfig(
        order = prefs.map { it.id },
        hidden = prefs.filterNot { it.visible }.map { it.id }.toSet(),
    )

    fun fetchIds(
        prefs: List<HomeShelfPref>,
        billboardId: String = CatalogShelves.TRENDING_HOME,
    ): Set<String> = prefs.filter { it.visible }.map { it.id }.toSet() + billboardId

    fun arrangeRows(rows: List<CatalogRow>, prefs: List<HomeShelfPref>): List<CatalogRow> {
        val byId = rows.associateBy { it.id }
        return prefs.filter { it.visible }.mapNotNull { byId[it.id] }
    }

    fun toggleVisible(prefs: List<HomeShelfPref>, id: String): List<HomeShelfPref> =
        prefs.map { if (it.id == id) it.copy(visible = !it.visible) else it }

    fun move(prefs: List<HomeShelfPref>, id: String, delta: Int): List<HomeShelfPref> {
        val index = prefs.indexOfFirst { it.id == id }
        if (index < 0) return prefs
        val target = index + delta
        if (target !in prefs.indices) return prefs
        val mutable = prefs.toMutableList()
        val item = mutable.removeAt(index)
        mutable.add(target, item)
        return mutable
    }
}
