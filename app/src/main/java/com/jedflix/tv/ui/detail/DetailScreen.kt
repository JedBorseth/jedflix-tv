package com.jedflix.tv.ui.detail

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Glow
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import androidx.compose.foundation.BorderStroke
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.jedflix.tv.R
import com.jedflix.tv.data.tmdb.CastMember
import com.jedflix.tv.data.tmdb.CatalogRow
import com.jedflix.tv.data.tmdb.MediaTitle
import com.jedflix.tv.data.tmdb.MediaType
import com.jedflix.tv.data.tmdb.TitleDetails
import com.jedflix.tv.data.tmdb.TmdbRepository
import com.jedflix.tv.data.tmdb.TvEpisode
import com.jedflix.tv.data.tmdb.TvSeason
import com.jedflix.tv.ui.components.BillboardBackdrop
import com.jedflix.tv.ui.components.CatalogRowView
import com.jedflix.tv.ui.components.ContentStartPadding
import com.jedflix.tv.ui.components.SkeletonBlock
import com.jedflix.tv.ui.components.SkeletonRow
import com.jedflix.tv.ui.components.rememberShimmerBrush
import com.jedflix.tv.ui.home.ErrorKind
import com.jedflix.tv.ui.theme.JedflixIcons
import com.jedflix.tv.ui.theme.WarmWhite
import com.jedflix.tv.ui.theme.Zinc300
import com.jedflix.tv.ui.theme.Zinc400
import com.jedflix.tv.ui.theme.Zinc800
import com.jedflix.tv.ui.theme.Zinc950
import java.util.Locale

@Composable
fun DetailScreen(
    mediaType: MediaType,
    mediaId: Int,
    repository: TmdbRepository,
    onTitleClick: (MediaTitle) -> Unit,
) {
    val viewModel: DetailViewModel = viewModel(
        key = "${mediaType.apiValue}-$mediaId",
        factory = DetailViewModel.Factory(mediaType, mediaId, repository),
    )
    val state by viewModel.state.collectAsStateWithLifecycle()

    Crossfade(
        targetState = state,
        animationSpec = tween(350),
        label = "detail-state",
        modifier = Modifier.fillMaxSize().background(Zinc950),
    ) { current ->
        when (current) {
            DetailUiState.Loading -> DetailSkeleton()
            is DetailUiState.Error -> DetailError(kind = current.kind, onRetry = viewModel::retry)
            is DetailUiState.Ready -> DetailContent(
                state = current,
                onSelectSeason = viewModel::selectSeason,
                onTitleClick = onTitleClick,
            )
        }
    }
}

@Composable
private fun DetailContent(
    state: DetailUiState.Ready,
    onSelectSeason: (Int) -> Unit,
    onTitleClick: (MediaTitle) -> Unit,
) {
    val details = state.details
    val playFocus = remember { FocusRequester() }
    val episodesFocus = remember { FocusRequester() }

    LaunchedEffect(details.title.key) {
        runCatching { playFocus.requestFocus() }
    }

    Box(modifier = Modifier.fillMaxSize().testTag("detail")) {
        BillboardBackdrop(title = details.title)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item(key = "hero") {
                DetailHero(
                    details = details,
                    playFocusRequester = playFocus,
                    onBrowseEpisodes = {
                        runCatching { episodesFocus.requestFocus() }
                    },
                )
            }
            if (details.cast.isNotEmpty()) {
                item(key = "cast") {
                    CastRow(cast = details.cast)
                }
            }
            if (details.seasons.isNotEmpty()) {
                item(key = "episodes") {
                    EpisodeSection(
                        seasons = details.seasons,
                        selectedSeason = state.selectedSeason,
                        episodes = state.episodes,
                        loading = state.episodesLoading,
                        firstEpisodeFocus = episodesFocus,
                        onSelectSeason = onSelectSeason,
                    )
                }
            }
            if (details.similar.isNotEmpty()) {
                item(key = "similar") {
                    CatalogRowView(
                        row = CatalogRow(
                            id = "similar",
                            title = stringResource(R.string.row_more_like_this),
                            items = details.similar,
                        ),
                        onItemClick = onTitleClick,
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailHero(
    details: TitleDetails,
    playFocusRequester: FocusRequester,
    onBrowseEpisodes: () -> Unit,
) {
    val title = details.title
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = ContentStartPadding, end = 48.dp, top = 36.dp),
        horizontalArrangement = Arrangement.spacedBy(28.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Box(
            modifier = Modifier
                .width(160.dp)
                .height(240.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Zinc800),
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(title.posterUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = stringResource(R.string.cd_poster, title.title),
                contentScale = ContentScale.Crop,
                placeholder = ColorPainter(Zinc800),
                error = ColorPainter(Zinc800),
                modifier = Modifier.fillMaxSize(),
            )
        }
        Column(modifier = Modifier.weight(1f).padding(bottom = 4.dp)) {
            Text(
                text = title.title,
                style = MaterialTheme.typography.displayMedium,
                color = WarmWhite,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = details.metaLine(),
                style = MaterialTheme.typography.titleMedium,
                color = Zinc300,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = title.overview,
                style = MaterialTheme.typography.bodyMedium,
                color = Zinc300,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(0.85f),
            )
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (title.mediaType == MediaType.TV) {
                    ActionButton(
                        label = stringResource(R.string.action_browse_episodes),
                        icon = JedflixIcons.Play,
                        filled = true,
                        modifier = Modifier.focusRequester(playFocusRequester),
                        onClick = onBrowseEpisodes,
                    )
                } else {
                    ActionButton(
                        label = stringResource(R.string.action_play),
                        icon = JedflixIcons.Play,
                        filled = true,
                        modifier = Modifier.focusRequester(playFocusRequester),
                        onClick = {},
                    )
                }
                ActionButton(
                    label = stringResource(R.string.action_my_list),
                    icon = JedflixIcons.Add,
                    filled = false,
                    onClick = {},
                )
            }
        }
    }
}

@Composable
private fun ActionButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    filled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.colors(
            containerColor = if (filled) WarmWhite else Color.White.copy(alpha = 0.22f),
            contentColor = if (filled) Zinc950 else WarmWhite,
            focusedContainerColor = WarmWhite,
            focusedContentColor = Zinc950,
        ),
    ) {
        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(text = label, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun CastRow(cast: List<CastMember>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = stringResource(R.string.row_cast),
            style = MaterialTheme.typography.titleMedium,
            color = WarmWhite,
            modifier = Modifier.padding(start = ContentStartPadding),
        )
        LazyRow(
            contentPadding = PaddingValues(start = ContentStartPadding, end = 48.dp, top = 8.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(cast, key = { it.id }) { member ->
                CastCard(member)
            }
        }
    }
}

@Composable
private fun CastCard(member: CastMember) {
    val shape = RoundedCornerShape(6.dp)
    Column(modifier = Modifier.width(110.dp)) {
        Surface(
            onClick = {},
            modifier = Modifier
                .width(110.dp)
                .height(150.dp)
                .testTag("cast-card"),
            shape = ClickableSurfaceDefaults.shape(shape = shape),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Zinc800,
                focusedContainerColor = Zinc800,
            ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
            border = ClickableSurfaceDefaults.border(
                focusedBorder = Border(border = BorderStroke(3.dp, WarmWhite), shape = shape),
            ),
            glow = ClickableSurfaceDefaults.glow(
                focusedGlow = Glow(elevationColor = Color.White.copy(alpha = 0.3f), elevation = 12.dp),
            ),
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(member.profileUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = member.name,
                contentScale = ContentScale.Crop,
                placeholder = ColorPainter(Zinc800),
                error = ColorPainter(Zinc800),
                modifier = Modifier.fillMaxSize(),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = member.name,
            style = MaterialTheme.typography.bodyMedium,
            color = WarmWhite,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = member.character,
            style = MaterialTheme.typography.bodySmall,
            color = Zinc400,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun EpisodeSection(
    seasons: List<TvSeason>,
    selectedSeason: Int?,
    episodes: List<TvEpisode>,
    loading: Boolean,
    firstEpisodeFocus: FocusRequester,
    onSelectSeason: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = stringResource(R.string.row_episodes),
            style = MaterialTheme.typography.titleMedium,
            color = WarmWhite,
            modifier = Modifier.padding(start = ContentStartPadding),
        )
        LazyRow(
            contentPadding = PaddingValues(start = ContentStartPadding, end = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(seasons, key = { it.seasonNumber }) { season ->
                val selected = season.seasonNumber == selectedSeason
                Button(
                    onClick = { onSelectSeason(season.seasonNumber) },
                    colors = ButtonDefaults.colors(
                        containerColor = if (selected) WarmWhite.copy(alpha = 0.22f) else Color.Transparent,
                        contentColor = if (selected) WarmWhite else Zinc400,
                        focusedContainerColor = WarmWhite,
                        focusedContentColor = Zinc950,
                    ),
                ) {
                    Text(season.name)
                }
            }
        }
        if (loading) {
            val brush = rememberShimmerBrush()
            LazyRow(
                contentPadding = PaddingValues(start = ContentStartPadding, end = 48.dp, top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(6) {
                    SkeletonBlock(brush, width = 220.dp, height = 124.dp, radius = 6.dp)
                }
            }
        } else {
            LazyRow(
                contentPadding = PaddingValues(start = ContentStartPadding, end = 48.dp, top = 8.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                itemsIndexed(episodes, key = { _, ep -> ep.episodeNumber }) { index, episode ->
                    EpisodeCard(
                        episode = episode,
                        modifier = if (index == 0) Modifier.focusRequester(firstEpisodeFocus) else Modifier,
                    )
                }
            }
        }
    }
}

@Composable
private fun EpisodeCard(episode: TvEpisode, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(6.dp)
    Column(modifier = modifier.width(220.dp)) {
        Surface(
            onClick = {},
            modifier = Modifier
                .width(220.dp)
                .height(124.dp)
                .testTag("episode-card"),
            shape = ClickableSurfaceDefaults.shape(shape = shape),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Zinc800,
                focusedContainerColor = Zinc800,
            ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
            border = ClickableSurfaceDefaults.border(
                focusedBorder = Border(border = BorderStroke(3.dp, WarmWhite), shape = shape),
            ),
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(episode.stillUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = episode.title,
                contentScale = ContentScale.Crop,
                placeholder = ColorPainter(Zinc800),
                error = ColorPainter(Zinc800),
                modifier = Modifier.fillMaxSize(),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.episode_label, episode.episodeNumber, episode.title),
            style = MaterialTheme.typography.bodyMedium,
            color = WarmWhite,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun DetailSkeleton() {
    val brush = rememberShimmerBrush()
    val sink = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { sink.requestFocus() } }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("detail-skeleton")
            .focusRequester(sink)
            .focusable()
            .padding(start = ContentStartPadding, top = 36.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            SkeletonBlock(brush, width = 160.dp, height = 240.dp, radius = 8.dp)
            Column(modifier = Modifier.padding(top = 80.dp)) {
                SkeletonBlock(brush, width = 380.dp, height = 40.dp)
                Spacer(Modifier.height(14.dp))
                SkeletonBlock(brush, width = 240.dp, height = 16.dp)
                Spacer(Modifier.height(14.dp))
                SkeletonBlock(brush, width = 520.dp, height = 14.dp)
                Spacer(Modifier.height(8.dp))
                SkeletonBlock(brush, width = 460.dp, height = 14.dp)
            }
        }
        Spacer(Modifier.height(32.dp))
        SkeletonRow(brush)
    }
}

@Composable
private fun DetailError(kind: ErrorKind, onRetry: () -> Unit) {
    val retryFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { retryFocus.requestFocus() } }
    Box(
        modifier = Modifier.fillMaxSize().testTag("detail-error"),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.error_title_detail),
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

private fun TitleDetails.metaLine(): String {
    val parts = buildList {
        title.year?.let { add(it) }
        runtimeMinutes?.let { add(formatRuntime(it)) }
        addAll(title.genres)
        title.rating?.let { add("★ " + String.format(Locale.US, "%.1f", it)) }
    }
    return parts.joinToString("  •  ")
}

private fun formatRuntime(minutes: Int): String {
    val hours = minutes / 60
    val rest = minutes % 60
    return if (hours > 0) "${hours}h ${rest}m" else "${minutes}m"
}
