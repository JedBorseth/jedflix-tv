package com.jedflix.tv.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.jedflix.tv.R
import com.jedflix.tv.data.library.LibraryRows
import com.jedflix.tv.data.tmdb.CatalogRow
import com.jedflix.tv.data.tmdb.MediaTitle
import com.jedflix.tv.ui.focus.RailRestore
import com.jedflix.tv.ui.focus.independentRail
import com.jedflix.tv.ui.focus.optionalFocusRequester
import com.jedflix.tv.ui.focus.railItemFocus
import com.jedflix.tv.ui.focus.rememberRailListState
import com.jedflix.tv.ui.theme.WarmWhite

val ContentStartPadding = 48.dp

@Composable
fun CatalogRowView(
    row: CatalogRow,
    modifier: Modifier = Modifier,
    progressFor: ((MediaTitle) -> Float?)? = null,
    onItemFocused: ((index: Int, title: MediaTitle) -> Unit)? = null,
    onItemClick: ((MediaTitle) -> Unit)? = null,
    firstItemFocusRequester: FocusRequester? = null,
    enterFocusRequester: FocusRequester? = null,
    contentReturnFocus: FocusRequester? = null,
    restoreFocusRequester: FocusRequester? = null,
    restoredItemKey: String? = null,
    returnItemKey: String? = null,
    /** Where D-pad up should go from this row (e.g. the billboard's Play button). */
    upFocusRequester: FocusRequester? = null,
    downFocusRequester: FocusRequester? = null,
    stateKey: String = row.id,
) {
    val heading = when (row.id) {
        LibraryRows.CONTINUE_WATCHING -> stringResource(R.string.row_continue_watching)
        LibraryRows.MY_LIST -> stringResource(R.string.row_my_list)
        LibraryRows.WATCH_HISTORY -> stringResource(R.string.row_watch_history)
        else -> row.title
    }
    val enter = enterFocusRequester ?: remember(row.id) { FocusRequester() }
    var lastKey by remember(row.id) { mutableStateOf(restoredItemKey) }
    val enterKey = RailRestore.itemKey(lastKey, row.items.map { it.key })
    val listState = rememberRailListState(stateKey)

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = heading,
            style = MaterialTheme.typography.titleMedium,
            color = WarmWhite,
            modifier = Modifier.padding(start = ContentStartPadding),
        )
        LazyRow(
            state = listState,
            contentPadding = PaddingValues(start = ContentStartPadding, end = 48.dp, top = 8.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .testTag("row-${row.id}")
                .independentRail(enter),
        ) {
            itemsIndexed(row.items, key = { _, item -> item.key }) { index, item ->
                PosterCard(
                    title = item,
                    progress = if (row.showProgress) progressFor?.invoke(item) else null,
                    modifier = Modifier
                        .optionalFocusRequester(if (item.key == enterKey) enter else null)
                        .optionalFocusRequester(
                            if (index == 0) firstItemFocusRequester else null,
                        )
                        .optionalFocusRequester(
                            if (item.key == returnItemKey) contentReturnFocus else null,
                        )
                        .optionalFocusRequester(
                            if (item.key == enterKey) restoreFocusRequester else null,
                        )
                        .railItemFocus(
                            up = upFocusRequester,
                            down = downFocusRequester,
                            blockRight = index == row.items.lastIndex,
                        ),
                    onFocused = {
                        lastKey = item.key
                        onItemFocused?.invoke(index, item)
                    },
                    onClick = { onItemClick?.invoke(item) },
                )
            }
        }
    }
}
