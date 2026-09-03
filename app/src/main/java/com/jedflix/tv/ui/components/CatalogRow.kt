package com.jedflix.tv.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.jedflix.tv.R
import com.jedflix.tv.data.library.LibraryRows
import com.jedflix.tv.data.tmdb.CatalogRow
import com.jedflix.tv.data.tmdb.MediaTitle
import com.jedflix.tv.ui.theme.WarmWhite

val ContentStartPadding = 48.dp

@Composable
fun CatalogRowView(
    row: CatalogRow,
    modifier: Modifier = Modifier,
    progressFor: ((MediaTitle) -> Float?)? = null,
    onItemFocused: ((MediaTitle) -> Unit)? = null,
    onItemClick: ((MediaTitle) -> Unit)? = null,
    firstItemFocusRequester: FocusRequester? = null,
    /** Where D-pad up should go from this row (e.g. the billboard's Play button). */
    upFocusRequester: FocusRequester? = null,
) {
    val heading = when (row.id) {
        LibraryRows.CONTINUE_WATCHING -> stringResource(R.string.row_continue_watching)
        LibraryRows.MY_LIST -> stringResource(R.string.row_my_list)
        LibraryRows.WATCH_HISTORY -> stringResource(R.string.row_watch_history)
        else -> row.title
    }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = heading,
            style = MaterialTheme.typography.titleMedium,
            color = WarmWhite,
            modifier = Modifier.padding(start = ContentStartPadding),
        )
        LazyRow(
            // Vertical padding leaves room for the focused card's scale so the row doesn't clip it.
            contentPadding = PaddingValues(start = ContentStartPadding, end = 48.dp, top = 8.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .testTag("row-${row.id}")
                .then(
                    if (upFocusRequester != null) {
                        Modifier.focusProperties { up = upFocusRequester }
                    } else {
                        Modifier
                    },
                )
                .focusRestorer(),
        ) {
            itemsIndexed(row.items, key = { _, item -> item.key }) { index, item ->
                val requester = if (index == 0) firstItemFocusRequester else null
                PosterCard(
                    title = item,
                    progress = if (row.showProgress) progressFor?.invoke(item) else null,
                    modifier = if (requester != null) Modifier.focusRequester(requester) else Modifier,
                    onFocused = onItemFocused?.let { callback -> { callback(item) } },
                    onClick = { onItemClick?.invoke(item) },
                )
            }
        }
    }
}
