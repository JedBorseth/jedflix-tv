package com.jedflix.tv.ui.search

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.jedflix.tv.R
import com.jedflix.tv.JedflixTvApp
import com.jedflix.tv.data.library.UserLibraryRepository
import com.jedflix.tv.data.tmdb.CatalogSection
import com.jedflix.tv.data.tmdb.MediaTitle
import com.jedflix.tv.data.tmdb.TmdbRepository
import com.jedflix.tv.ui.components.ContentStartPadding
import com.jedflix.tv.ui.components.JedflixDrawer
import com.jedflix.tv.ui.components.PosterCard
import com.jedflix.tv.ui.components.PosterHeight
import com.jedflix.tv.ui.components.PosterWidth
import com.jedflix.tv.ui.components.ProfileAvatarEndClearance
import com.jedflix.tv.ui.components.RailCollapsedWidth
import com.jedflix.tv.ui.components.SkeletonBlock
import com.jedflix.tv.ui.components.rememberShimmerBrush
import com.jedflix.tv.ui.home.ErrorKind
import com.jedflix.tv.ui.theme.JedflixIcons
import com.jedflix.tv.ui.theme.WarmWhite
import com.jedflix.tv.ui.theme.Zinc400
import com.jedflix.tv.ui.theme.Zinc800
import com.jedflix.tv.ui.theme.Zinc950
import kotlinx.coroutines.launch

@Composable
fun SearchScreen(
    repository: TmdbRepository,
    library: UserLibraryRepository,
    onSectionSelected: (CatalogSection) -> Unit,
    onSettings: () -> Unit,
    onLive: () -> Unit,
    onTitleClick: (MediaTitle) -> Unit,
) {
    val recommendations = (LocalContext.current.applicationContext as JedflixTvApp).recommendationRepository
    val viewModel: SearchViewModel = viewModel(factory = SearchViewModel.Factory(repository, library, recommendations))
    val query by viewModel.query.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val prompt by viewModel.prompt.collectAsStateWithLifecycle()
    val fieldFocus = remember { FocusRequester() }
    val promptFocus = remember { FocusRequester() }
    val customFocus = remember { FocusRequester() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        if (viewModel.selectedTitleKey == null) runCatching { promptFocus.requestFocus() }
    }
    // The drawer's later handler retains priority while the drawer is open.
    BackHandler(enabled = prompt != null) {
        viewModel.clearDiscovery()
        scope.launch {
            withFrameNanos { }
            runCatching { promptFocus.requestFocus() }
        }
    }

    JedflixDrawer(
        selected = null,
        searchSelected = true,
        onSelect = onSectionSelected,
        onSearch = {},
        onLive = onLive,
        onSettings = onSettings,
        library = library,
        contentFocusRequester = promptFocus,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Zinc950)
                .padding(start = RailCollapsedWidth)
                .testTag("search"),
        ) {
            SearchField(
                query = query,
                onQueryChange = viewModel::onQueryChange,
                onSearchSubmit = viewModel::onSearchSubmit,
                nextFocus = if (query.isBlank()) promptFocus else customFocus,
                modifier = Modifier
                    .padding(start = ContentStartPadding, end = ProfileAvatarEndClearance, top = 28.dp, bottom = 16.dp)
                    .focusRequester(fieldFocus),
            )
            SearchPrompts(
                query = query,
                busy = state is SearchUiState.Discovering,
                onPrompt = viewModel::discover,
                firstFocus = promptFocus,
                customFocus = customFocus,
            )
            if (prompt != null) {
                Text(
                    text = prompt.orEmpty(),
                    style = MaterialTheme.typography.titleLarge,
                    color = WarmWhite,
                    modifier = Modifier.padding(start = ContentStartPadding, top = 12.dp, bottom = 8.dp),
                )
            }
            Crossfade(
                targetState = state,
                animationSpec = tween(250),
                label = "search-state",
                modifier = Modifier.fillMaxSize(),
            ) { current ->
                when (current) {
                    SearchUiState.Loading -> SearchSkeleton()
                    is SearchUiState.Discovering -> SearchSkeleton()
                    SearchUiState.Empty -> SearchMessage(stringResource(R.string.search_empty))
                    is SearchUiState.Idle -> SearchIdle(
                        recents = current.recents,
                        onRecentClick = viewModel::onQueryChange,
                    )
                    is SearchUiState.Error -> SearchError(kind = current.kind)
                    is SearchUiState.DiscoveryError -> SearchMessage(stringResource(
                        if (current.limited) R.string.discovery_limited else R.string.discovery_error,
                    ))
                    is SearchUiState.Results -> SearchResults(
                        titles = current.titles,
                        onTitleClick = { title -> viewModel.rememberSelectedTitle(title.key); onTitleClick(title) },
                        focusResults = current.prompt != null && current.prompt == prompt,
                        restoredKey = viewModel.selectedTitleKey,
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchPrompts(query: String, busy: Boolean, onPrompt: (String) -> Unit,
                          firstFocus: FocusRequester, customFocus: FocusRequester) {
    val keyboard = LocalSoftwareKeyboardController.current
    val choices = listOf(
        stringResource(R.string.prompt_silly),
        stringResource(R.string.prompt_comfort),
        stringResource(R.string.prompt_twists),
        stringResource(R.string.prompt_gems),
        stringResource(R.string.prompt_binge),
    )
    val listState = rememberLazyListState()
    LaunchedEffect(query.isNotBlank()) {
        if (query.isNotBlank()) listState.scrollToItem(0)
    }
    LazyRow(
        state = listState,
        contentPadding = PaddingValues(start = ContentStartPadding, end = 48.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().testTag("search-prompts"),
    ) {
        if (query.isNotBlank()) {
            item(key = "custom-prompt") {
                Button(
                    onClick = { keyboard?.hide(); onPrompt(query) },
                    enabled = !busy,
                    modifier = Modifier.focusRequester(customFocus).testTag("search-custom-prompt"),
                ) { Text(stringResource(R.string.prompt_custom)) }
            }
        }
        items(choices, key = { it }) { choice ->
            Button(
                onClick = { keyboard?.hide(); onPrompt(choice) },
                enabled = !busy,
                colors = ButtonDefaults.colors(
                    containerColor = Zinc800,
                    contentColor = WarmWhite,
                    focusedContainerColor = WarmWhite,
                    focusedContentColor = Zinc950,
                ),
                modifier = Modifier.testTag("search-prompt-$choice")
                    .then(if (choice == choices.first()) Modifier.focusRequester(firstFocus) else Modifier),
            ) { Text(choice, fontWeight = FontWeight.Medium) }
        }
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearchSubmit: () -> Unit,
    nextFocus: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .background(Zinc800, RoundedCornerShape(8.dp))
            .padding(horizontal = 16.dp)
            .testTag("search-field"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = JedflixIcons.Search,
            contentDescription = null,
            modifier = Modifier.size(22.dp),
        )
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) {
                Text(
                    text = stringResource(R.string.search_placeholder),
                    color = Zinc400,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(
                    color = WarmWhite,
                    fontSize = 18.sp,
                ),
                cursorBrush = SolidColor(WarmWhite),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    imeAction = ImeAction.Search,
                ),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        keyboard?.hide()
                        onSearchSubmit()
                    },
                ),
                modifier = Modifier.fillMaxWidth().onPreviewKeyEvent { event ->
                    // A single-line editor consumes Down for cursor movement. On TV,
                    // leave the editor for its action button without scrolling any Shelf.
                    if (event.key == Key.DirectionDown && event.type == KeyEventType.KeyDown) {
                        keyboard?.hide()
                        runCatching { nextFocus.requestFocus() }
                        true
                    } else false
                },
            )
        }
    }
}

@Composable
private fun SearchResults(
    titles: List<MediaTitle>,
    onTitleClick: (MediaTitle) -> Unit,
    focusResults: Boolean = false,
    restoredKey: String? = null,
) {
    val firstFocus = remember { FocusRequester() }
    val targetIndex = titles.indexOfFirst { it.key == restoredKey }.coerceAtLeast(0)
    val targetKey = titles.getOrNull(targetIndex)?.key
    val gridState = rememberLazyGridState(initialFirstVisibleItemIndex = targetIndex)
    LaunchedEffect(focusResults, targetKey) {
        if ((focusResults || restoredKey != null) && titles.isNotEmpty()) {
            withFrameNanos { }
            runCatching { firstFocus.requestFocus() }
        }
    }
    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Adaptive(minSize = PosterWidth + 16.dp),
        contentPadding = PaddingValues(
            start = ContentStartPadding,
            end = 48.dp,
            bottom = 48.dp,
            top = 8.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize().testTag("search-results"),
    ) {
        items(titles, key = { it.key }) { title ->
            PosterCard(
                title = title,
                onClick = { onTitleClick(title) },
                modifier = if (title.key == targetKey) Modifier.focusRequester(firstFocus) else Modifier,
            )
        }
    }
}

@Composable
private fun SearchSkeleton() {
    val brush = rememberShimmerBrush()
    Column(modifier = Modifier.padding(start = ContentStartPadding, top = 8.dp)) {
        repeat(2) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(bottom = 16.dp),
            ) {
                repeat(8) {
                    SkeletonBlock(brush, width = PosterWidth, height = PosterHeight, radius = 6.dp)
                }
            }
        }
    }
}

@Composable
private fun SearchIdle(
    recents: List<String>,
    onRecentClick: (String) -> Unit,
) {
    if (recents.isEmpty()) {
        SearchMessage(stringResource(R.string.search_idle))
        return
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = ContentStartPadding, end = 48.dp, top = 8.dp)
            .testTag("search-recents"),
    ) {
        Text(
            text = stringResource(R.string.search_recent),
            style = MaterialTheme.typography.titleMedium,
            color = WarmWhite,
        )
        Spacer(Modifier.height(12.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(recents, key = { it }) { recent ->
                Button(
                    onClick = { onRecentClick(recent) },
                    colors = ButtonDefaults.colors(
                        containerColor = Zinc800,
                        contentColor = WarmWhite,
                        focusedContainerColor = WarmWhite,
                        focusedContentColor = Zinc950,
                    ),
                ) {
                    Text(recent, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
private fun SearchMessage(message: String) {
    Box(
        modifier = Modifier.fillMaxSize().padding(horizontal = 48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.headlineSmall,
            color = Zinc400,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun SearchError(kind: ErrorKind) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.search_error),
                style = MaterialTheme.typography.headlineSmall,
                color = WarmWhite,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(
                    when (kind) {
                        ErrorKind.MISSING_KEY -> R.string.error_missing_key
                        ErrorKind.NETWORK -> R.string.error_network
                    },
                ),
                color = Zinc400,
            )
        }
    }
}
