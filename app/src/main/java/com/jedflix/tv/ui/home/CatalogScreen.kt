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
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Button
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.jedflix.tv.BuildConfig
import com.jedflix.tv.R
import com.jedflix.tv.data.library.LibraryItem
import com.jedflix.tv.data.library.LibraryRows
import com.jedflix.tv.data.library.UserLibraryRepository
import com.jedflix.tv.data.settings.SettingsStore
import com.jedflix.tv.data.tmdb.Catalog
import com.jedflix.tv.data.tmdb.CatalogSection
import com.jedflix.tv.data.tmdb.MediaTitle
import com.jedflix.tv.data.tmdb.TmdbRepository
import com.jedflix.tv.data.trailer.TrailerPreviewPhase
import com.jedflix.tv.ui.components.BillboardBackdrop
import com.jedflix.tv.ui.components.BillboardInfo
import com.jedflix.tv.ui.components.CatalogRowView
import com.jedflix.tv.ui.components.CatalogSkeletons
import com.jedflix.tv.ui.components.JedflixDrawer
import com.jedflix.tv.ui.components.RailCollapsedWidth
import com.jedflix.tv.ui.focus.RailRestore
import com.jedflix.tv.ui.theme.JedflixIcons
import com.jedflix.tv.ui.theme.WarmWhite
import com.jedflix.tv.ui.theme.Zinc400

/** Fraction of the viewport height where a focused row (other than the first) is pinned. */
private const val ROW_PIVOT = 0.16f

@Composable
fun CatalogScreen(
    section: CatalogSection,
    repository: TmdbRepository,
    library: UserLibraryRepository,
    settingsStore: SettingsStore,
    onSectionSelected: (CatalogSection) -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    onTitleClick: (MediaTitle) -> Unit,
    onContinueWatching: (LibraryItem) -> Unit,
) {
    val viewModel: CatalogViewModel = viewModel(
        key = section.name,
        factory = CatalogViewModel.Factory(section, repository, library, settingsStore),
    )
    val preview: TrailerPreviewViewModel = viewModel(
        key = "trailer-preview-${section.name}",
        factory = TrailerPreviewViewModel.Factory(
            context = LocalContext.current.applicationContext,
            tmdb = repository,
            clipBaseUrl = BuildConfig.TRAILER_CLIP_BASE_URL,
            settingsStore = settingsStore,
        ),
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val previewUi by preview.ui.collectAsStateWithLifecycle()
    val profileFocus = remember { FocusRequester() }
    val contentReturnFocus = remember { FocusRequester() }

    LifecycleStartEffect(preview) {
        onStopOrDispose { preview.reset() }
    }

    JedflixDrawer(
        selected = section,
        searchSelected = false,
        onSelect = onSectionSelected,
        onSearch = onSearch,
        onSettings = onSettings,
        library = library,
        profileFocusRequester = profileFocus,
        contentFocusRequester = contentReturnFocus,
        consumeRootBack = section == CatalogSection.HOME,
    ) {
        Crossfade(
            targetState = when (state) {
                CatalogUiState.Loading -> "loading"
                is CatalogUiState.Error -> "error"
                is CatalogUiState.Ready -> "ready"
            },
            animationSpec = tween(400),
            label = "catalog-state",
            modifier = Modifier.fillMaxSize(),
        ) { _ ->
            when (val current = state) {
                CatalogUiState.Loading -> CatalogSkeletons(modifier = Modifier.padding(start = RailCollapsedWidth))
                is CatalogUiState.Error -> CatalogError(kind = current.kind, onRetry = viewModel::retry)
                is CatalogUiState.Ready -> CatalogContent(
                    catalog = current.catalog,
                    myListKeys = current.myListKeys,
                    continueWatching = current.continueWatching,
                    profileFocus = profileFocus,
                    contentReturnFocus = contentReturnFocus,
                    restoredRowId = viewModel.focusRowId,
                    restoredItemKey = viewModel.focusItemKey,
                    profileStateKey = viewModel.profileStateKey,
                    onTitleClick = onTitleClick,
                    onContinueWatching = onContinueWatching,
                    onToggleMyList = viewModel::toggleMyList,
                    onBillboardPlayFocused = viewModel::onBillboardPlayFocused,
                    onTitleFocused = viewModel::onTitleFocused,
                    onShelfItemFocused = viewModel::onShelfItemFocused,
                    previewUi = previewUi,
                    previewPlayer = preview.player,
                    onPreviewTitle = preview::onTitleFocused,
                    onPreviewMorphFinished = preview::onMorphFinished,
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
    myListKeys: Set<String>,
    continueWatching: List<LibraryItem>,
    profileFocus: FocusRequester,
    contentReturnFocus: FocusRequester,
    restoredRowId: String?,
    restoredItemKey: String?,
    profileStateKey: String,
    onTitleClick: (MediaTitle) -> Unit,
    onContinueWatching: (LibraryItem) -> Unit,
    onToggleMyList: (MediaTitle) -> Unit,
    onBillboardPlayFocused: () -> Unit,
    onTitleFocused: (rowId: String, itemKey: String) -> Unit,
    onShelfItemFocused: (rowId: String, index: Int, itemCount: Int, hasMore: Boolean) -> Unit,
    previewUi: TrailerPreviewUi,
    previewPlayer: androidx.media3.exoplayer.ExoPlayer,
    onPreviewTitle: (MediaTitle) -> Unit,
    onPreviewMorphFinished: () -> Unit,
) {
    val fallbackHero = catalog.featured.firstOrNull()
        ?: catalog.rows.firstOrNull()?.items?.firstOrNull()
        ?: return
    var hero: MediaTitle by remember { mutableStateOf(fallbackHero) }
    var backdrop: MediaTitle by remember { mutableStateOf(fallbackHero) }
    val restoreTarget = remember(catalog.rows.map { it.id }) {
        RailRestore.catalogTarget(restoredRowId, restoredItemKey, catalog.rows)
    }
    val initialRow = when (val target = restoreTarget) {
        is RailRestore.CatalogTarget.Title ->
            catalog.rows.indexOfFirst { it.id == target.rowId }.takeIf { it >= 0 } ?: 0
        else -> 0
    }
    var focusedRow by remember { mutableIntStateOf(initialRow) }
    val firstCardFocus = remember { FocusRequester() }
    val playFocus = remember { FocusRequester() }
    val restoredItemFocus = remember { FocusRequester() }
    val rowIds = catalog.rows.map { it.id }
    val rowEnter = remember(rowIds) { rowIds.associateWith { FocusRequester() } }
    fun enterOf(rowId: String): FocusRequester = rowEnter.getValue(rowId)
    val listState = rememberLazyListState()
    val rowScrollSpec = LocalBringIntoViewSpec.current
    val continueByKey = remember(continueWatching) { continueWatching.associateBy { it.title.key } }
    val firstRowEnter = catalog.rows.firstOrNull()?.let { enterOf(it.id) }
    val returnIsPlay = restoreTarget is RailRestore.CatalogTarget.BillboardPlay ||
        restoredItemKey == RailRestore.BILLBOARD_PLAY
    var returnPlay by remember { mutableStateOf(returnIsPlay) }
    var returnRowId by remember { mutableStateOf(restoredRowId) }
    var returnItemKey by remember {
        mutableStateOf(
            (restoreTarget as? RailRestore.CatalogTarget.Title)?.itemKey
                ?: restoredItemKey.takeIf { it != RailRestore.BILLBOARD_PLAY },
        )
    }
    var previewOrigin by remember { mutableStateOf<Rect?>(null) }
    var billboardBounds by remember { mutableStateOf<Rect?>(null) }

    LaunchedEffect(Unit) {
        withFrameNanos { }
        when (val target = restoreTarget) {
            RailRestore.CatalogTarget.BillboardPlay -> {
                listState.scrollToItem(0)
                withFrameNanos { }
                runCatching { playFocus.requestFocus() }
            }
            is RailRestore.CatalogTarget.Title -> {
                val rowIndex = catalog.rows.indexOfFirst { it.id == target.rowId }
                if (rowIndex > 0) {
                    val pivotPx = (listState.layoutInfo.viewportSize.height * ROW_PIVOT).toInt()
                    listState.scrollToItem(index = rowIndex + 1, scrollOffset = -pivotPx)
                    withFrameNanos { }
                }
                runCatching { restoredItemFocus.requestFocus() }
            }
            RailRestore.CatalogTarget.FirstTitle -> {
                runCatching { firstCardFocus.requestFocus() }
            }
        }
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
    val coversHero = previewUi.phase == TrailerPreviewPhase.Opening ||
        previewUi.phase == TrailerPreviewPhase.Playing
    val infoAlpha by animateFloatAsState(
        targetValue = if (coversHero) 0f else 1f,
        animationSpec = tween(250),
        label = "billboard-info-alpha",
    )

    Box(modifier = Modifier.fillMaxSize().testTag("catalog")) {
        BillboardBackdrop(
            title = backdrop,
            modifier = Modifier.graphicsLayer { alpha = backdropAlpha },
            onBoundsInWindow = { billboardBounds = it },
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
                        inMyList = hero.key in myListKeys,
                        playFocusRequester = playFocus,
                        contentReturnFocus = contentReturnFocus,
                        returnToPlay = returnPlay,
                        upFocusRequester = profileFocus,
                        downFocusRequester = firstRowEnter,
                        onPlay = { onTitleClick(hero) },
                        onMyList = { onToggleMyList(hero) },
                        onPlayFocused = {
                            returnPlay = true
                            returnRowId = null
                            returnItemKey = RailRestore.BILLBOARD_PLAY
                            previewOrigin = billboardBounds
                            onBillboardPlayFocused()
                            onPreviewTitle(hero)
                        },
                        modifier = Modifier.graphicsLayer { alpha = infoAlpha },
                    )
                }
                itemsIndexed(catalog.rows, key = { _, row -> row.id }) { index, row ->
                    CompositionLocalProvider(LocalBringIntoViewSpec provides rowScrollSpec) {
                        CatalogRowView(
                            row = row,
                            progressFor = { title -> continueByKey[title.key]?.progress },
                            onItemFocused = { itemIndex, focused ->
                                focusedRow = index
                                returnPlay = false
                                returnRowId = row.id
                                returnItemKey = focused.key
                                if (row.drivesHero) {
                                    hero = focused
                                    backdrop = focused
                                }
                                onTitleFocused(row.id, focused.key)
                                onShelfItemFocused(row.id, itemIndex, row.items.size, row.hasMore)
                                onPreviewTitle(focused)
                            },
                            onItemBounds = { previewOrigin = it },
                            onItemClick = { title ->
                                if (row.id == LibraryRows.CONTINUE_WATCHING) {
                                    continueByKey[title.key]?.let(onContinueWatching)
                                        ?: onTitleClick(title)
                                } else {
                                    onTitleClick(title)
                                }
                            },
                            firstItemFocusRequester = if (index == 0) firstCardFocus else null,
                            enterFocusRequester = enterOf(row.id),
                            contentReturnFocus = contentReturnFocus,
                            restoredItemKey = when (val target = restoreTarget) {
                                is RailRestore.CatalogTarget.Title ->
                                    target.itemKey.takeIf { target.rowId == row.id }
                                else -> null
                            },
                            returnItemKey = returnItemKey.takeIf { returnRowId == row.id },
                            upFocusRequester = if (index == 0) playFocus else null,
                            stateKey = "$profileStateKey-${row.id}",
                            restoreFocusRequester = when (val target = restoreTarget) {
                                is RailRestore.CatalogTarget.Title ->
                                    restoredItemFocus.takeIf { target.rowId == row.id }
                                else -> null
                            },
                        )
                    }
                }
            }
        }
        TrailerPreviewOverlay(
            ui = previewUi,
            player = previewPlayer,
            originInWindow = previewOrigin,
            destinationInWindow = billboardBounds,
            onMorphFinished = onPreviewMorphFinished,
        )
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
