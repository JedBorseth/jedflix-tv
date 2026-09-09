package com.jedflix.tv.ui.focus

import com.jedflix.tv.data.tmdb.CatalogRow

/** Pure restore rules for independent horizontal rails. */
object RailRestore {
    const val BILLBOARD_PLAY = "__billboard_play__"

    fun itemKey(saved: String?, keys: List<String>): String? {
        if (saved != null && saved in keys) return saved
        return keys.firstOrNull()
    }

    fun itemKey(saved: Int?, keys: List<Int>): Int? {
        if (saved != null && saved in keys) return saved
        return keys.firstOrNull()
    }

    sealed interface CatalogTarget {
        data object BillboardPlay : CatalogTarget
        data object FirstTitle : CatalogTarget
        data class Title(val rowId: String, val itemKey: String) : CatalogTarget
    }

    fun catalogTarget(
        savedRowId: String?,
        savedItemKey: String?,
        rows: List<CatalogRow>,
    ): CatalogTarget {
        if (savedItemKey == BILLBOARD_PLAY) return CatalogTarget.BillboardPlay
        if (savedRowId == null || savedItemKey == null) return CatalogTarget.FirstTitle
        val row = rows.find { it.id == savedRowId } ?: return CatalogTarget.FirstTitle
        val key = itemKey(savedItemKey, row.items.map { it.key }) ?: return CatalogTarget.FirstTitle
        return CatalogTarget.Title(row.id, key)
    }
}

enum class DetailRail { HERO, CAST, SEASONS, EPISODES, SIMILAR }
