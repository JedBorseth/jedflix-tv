package com.jedflix.tv.ui.home

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Button
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.jedflix.tv.R
import com.jedflix.tv.data.tmdb.Catalog
import com.jedflix.tv.data.tmdb.CatalogSection
import com.jedflix.tv.data.tmdb.MediaTitle
import com.jedflix.tv.data.tmdb.TmdbRepository
import com.jedflix.tv.ui.components.BillboardBackdrop
import com.jedflix.tv.ui.components.BillboardInfo
import com.jedflix.tv.ui.components.CatalogRowView
import com.jedflix.tv.ui.components.CatalogSkeletons
import com.jedflix.tv.ui.components.JedflixDrawer
import com.jedflix.tv.ui.components.RailCollapsedWidth
import com.jedflix.tv.ui.theme.JedflixIcons
import com.jedflix.tv.ui.theme.WarmWhite
import com.jedflix.tv.ui.theme.Zinc400

/** Fraction of the viewport height where a focused row (other than the first) is pinned. */
private const val ROW_PIVOT = 0.16f

@Composable
fun CatalogScreen(
    section: CatalogSection,
    repository: TmdbRepository,
    onSectionSelected: (CatalogSection) -> Unit,
    onSearch: () -> Unit,
    onTitleClick: (MediaTitle) -> Unit,
) {
    val viewModel: CatalogViewModel = viewModel(
        key = section.name,
        factory = CatalogViewModel.Factory(section, repository),
    )
    val state by viewModel.state.collectAsStateWithLifecycle()

    JedflixDrawer(
        selected = section,
        searchSelected = false,
        onSelect = onSectionSelected,
        onSearch = onSearch,
    ) {
        Crossfade(
            targetState = state,
            animationSpec = tween(400),
            label = "catalog-state",
            modifier = Modifier.fillMaxSize(),
        ) { current ->
            when (current) {
                CatalogUiState.Loading -> CatalogSkeletons(modifier = Modifier.padding(start = RailCollapsedWidth))
                is CatalogUiState.Error -> CatalogError(kind = current.kind, onRetry = viewModel::retry)
                is CatalogUiState.Ready -> CatalogContent(
                    catalog = current.catalog,
                    onTitleClick = onTitleClick,
                )
            }
        }
    }
}

/** Vertical scrolling is driven by which row has focus, so focus itself must not scroll the column. */
@OptIn(ExperimentalFoundationApi::class)
private object NoAutoScrollSpec : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float = 0f
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CatalogContent(
    catalog: Catalog,
    onTitleClick: (MediaTitle) -> Unit,
) {
    val fallbackHero = catalog.featured.firstOrNull() ?: catalog.rows.first().items.first()
    var hero: MediaTitle by remember(catalog) { mutableStateOf(fallbackHero) }
    var backdrop: MediaTitle by remember(catalog) { mutableStateOf(fallbackHero) }
    var focusedRow by remember(catalog) { mutableIntStateOf(0) }
    val firstCardFocus = remember { FocusRequester() }
    val playFocus = remember { FocusRequester() }
    val listState = rememberLazyListState()
    val rowScrollSpec = LocalBringIntoViewSpec.current

    LaunchedEffect(catalog) {
        runCatching { firstCardFocus.requestFocus() }
    }

    LaunchedEffect(focusedRow) {
        if (focusedRow == 0) {
            listState.animateScrollToItem(0)
        } else {
            val pivotPx = (listState.layoutInfo.viewportSize.height * ROW_PIVOT).toInt()
            listState.animateScrollToItem(index = focusedRow + 1, scrollOffset = -pivotPx)
        }
    }

    val scrolledAway by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0 }
    }
    val backdropAlpha by animateFloatAsState(
        targetValue = if (scrolledAway) 0.35f else 1f,
        animationSpec = tween(350),
        label = "backdrop-alpha",
    )

    Box(modifier = Modifier.fillMaxSize().testTag("catalog")) {
        BillboardBackdrop(
            title = backdrop,
            modifier = Modifier.graphicsLayer { alpha = backdropAlpha },
        )
        CompositionLocalProvider(LocalBringIntoViewSpec provides NoAutoScrollSpec) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(start = RailCollapsedWidth),
                contentPadding = PaddingValues(bottom = 48.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item(key = "billboard") {
                    BillboardInfo(
                        title = hero,
                        playFocusRequester = playFocus,
                        onPlay = { onTitleClick(hero) },
                    )
                }
                itemsIndexed(catalog.rows, key = { _, row -> row.id }) { index, row ->
                    CompositionLocalProvider(LocalBringIntoViewSpec provides rowScrollSpec) {
                        CatalogRowView(
                            row = row,
                            onItemFocused = { focused ->
                                focusedRow = index
                                backdrop = focused
                                if (index == 0) hero = focused
                            },
                            onItemClick = onTitleClick,
                            firstItemFocusRequester = if (index == 0) firstCardFocus else null,
                            upFocusRequester = if (index == 0) playFocus else null,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CatalogError(kind: ErrorKind, onRetry: () -> Unit) {
    val retryFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { retryFocus.requestFocus() } }

    Box(
        modifier = Modifier.fillMaxSize().padding(start = RailCollapsedWidth).testTag("catalog-error"),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.error_title),
                style = MaterialTheme.typography.headlineMedium,
                color = WarmWhite,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(
                    when (kind) {
                        ErrorKind.MISSING_KEY -> R.string.error_missing_key
                        ErrorKind.NETWORK -> R.string.error_network
                    },
                ),
                style = MaterialTheme.typography.bodyLarge,
                color = Zinc400,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onRetry,
                modifier = Modifier.focusRequester(retryFocus).testTag("retry"),
            ) {
                Icon(JedflixIcons.Refresh, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.action_retry))
            }
        }
    }
}
