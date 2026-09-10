package com.jedflix.tv.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.jedflix.tv.R
import com.jedflix.tv.data.tmdb.HomeShelfPref
import com.jedflix.tv.ui.theme.JedflixIcons
import com.jedflix.tv.ui.theme.WarmWhite
import com.jedflix.tv.ui.theme.Zinc400
import com.jedflix.tv.ui.theme.Zinc800
import com.jedflix.tv.ui.theme.Zinc950

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeShelvesSection(
    shelves: List<HomeShelfPref>,
    pickedShelfId: String?,
    firstShelfFocus: FocusRequester,
    onToggleVisible: (String) -> Unit,
    onTogglePick: (String) -> Unit,
    onMovePicked: (Int) -> Unit,
    onReset: () -> Unit,
) {
    val resetFocus = remember { FocusRequester() }
    Column(modifier = Modifier.testTag("settings-home-shelves")) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.settings_home_shelves_heading),
                style = MaterialTheme.typography.headlineMedium,
                color = WarmWhite,
            )
            Button(
                onClick = onReset,
                modifier = Modifier
                    .focusRequester(resetFocus)
                    .focusProperties { down = firstShelfFocus }
                    .testTag("settings-home-shelves-reset"),
                colors = ButtonDefaults.colors(
                    containerColor = Color.White.copy(alpha = 0.22f),
                    contentColor = WarmWhite,
                    focusedContainerColor = WarmWhite,
                    focusedContentColor = Zinc950,
                ),
            ) {
                Text(stringResource(R.string.settings_home_shelves_reset), fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.settings_home_shelves_description),
            style = MaterialTheme.typography.bodyLarge,
            color = Zinc400,
        )
        Spacer(Modifier.height(20.dp))
        shelves.forEachIndexed { index, shelf ->
            key(shelf.id) {
                HomeShelfRow(
                    shelf = shelf,
                    index = index,
                    picked = pickedShelfId == shelf.id,
                    holding = pickedShelfId != null,
                    labelFocus = if (index == 0) firstShelfFocus else null,
                    upFocus = if (index == 0 && pickedShelfId == null) resetFocus else null,
                    onToggleVisible = { onToggleVisible(shelf.id) },
                    onTogglePick = { onTogglePick(shelf.id) },
                    onMovePicked = onMovePicked,
                )
                if (index != shelves.lastIndex) Spacer(Modifier.height(10.dp))
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomeShelfRow(
    shelf: HomeShelfPref,
    index: Int,
    picked: Boolean,
    holding: Boolean,
    labelFocus: FocusRequester?,
    upFocus: FocusRequester?,
    onToggleVisible: () -> Unit,
    onTogglePick: () -> Unit,
    onMovePicked: (Int) -> Unit,
) {
    val localLabelFocus = remember { FocusRequester() }
    val labelRequester = labelFocus ?: localLabelFocus
    val visibleFocus = remember { FocusRequester() }
    val bringIntoView = remember { BringIntoViewRequester() }
    val shape = RoundedCornerShape(8.dp)
    val iconShape = RoundedCornerShape(8.dp)

    LaunchedEffect(picked, index) {
        if (!picked) return@LaunchedEffect
        withFrameNanos { }
        bringIntoView.bringIntoView()
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .bringIntoViewRequester(bringIntoView)
            .alpha(if (shelf.visible || picked) 1f else 0.45f)
            .testTag("settings-home-shelf-${shelf.id}"),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            onClick = onTogglePick,
            modifier = Modifier
                .weight(1f)
                .focusRequester(labelRequester)
                .onPreviewKeyEvent { event ->
                    if (!picked || event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.DirectionUp -> {
                            onMovePicked(-1)
                            true
                        }
                        Key.DirectionDown -> {
                            onMovePicked(1)
                            true
                        }
                        Key.DirectionLeft, Key.DirectionRight -> true
                        else -> false
                    }
                }
                .focusProperties {
                    if (picked || holding) {
                        up = FocusRequester.Cancel
                        down = FocusRequester.Cancel
                        left = FocusRequester.Cancel
                        right = FocusRequester.Cancel
                    } else {
                        right = visibleFocus
                        if (upFocus != null) up = upFocus
                    }
                },
            shape = ClickableSurfaceDefaults.shape(shape),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = if (picked) WarmWhite.copy(alpha = 0.18f) else Zinc800,
                focusedContainerColor = if (picked) WarmWhite.copy(alpha = 0.28f) else Zinc800,
                pressedContainerColor = Zinc800,
                contentColor = WarmWhite,
                focusedContentColor = WarmWhite,
            ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f, pressedScale = 1f),
            border = ClickableSurfaceDefaults.border(
                focusedBorder = Border(border = BorderStroke(2.dp, WarmWhite), shape = shape),
                border = if (picked) {
                    Border(border = BorderStroke(2.dp, WarmWhite), shape = shape)
                } else {
                    Border.None
                },
            ),
        ) {
            Text(
                text = shelf.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (picked) FontWeight.Bold else FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            )
        }
        Surface(
            onClick = onToggleVisible,
            modifier = Modifier
                .size(52.dp)
                .testTag("settings-home-shelf-visible-${shelf.id}")
                .focusRequester(visibleFocus)
                .focusProperties {
                    if (holding) {
                        up = FocusRequester.Cancel
                        down = FocusRequester.Cancel
                        left = FocusRequester.Cancel
                        right = FocusRequester.Cancel
                    } else {
                        left = labelRequester
                        right = FocusRequester.Cancel
                    }
                },
            shape = ClickableSurfaceDefaults.shape(iconShape),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Zinc800,
                focusedContainerColor = Zinc800,
                pressedContainerColor = Zinc800,
                contentColor = WarmWhite,
                focusedContentColor = WarmWhite,
            ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f, pressedScale = 1.04f),
            border = ClickableSurfaceDefaults.border(
                focusedBorder = Border(border = BorderStroke(2.dp, WarmWhite), shape = iconShape),
            ),
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(
                    imageVector = if (shelf.visible) JedflixIcons.Visibility else JedflixIcons.VisibilityOff,
                    contentDescription = stringResource(
                        if (shelf.visible) R.string.settings_home_shelf_hide else R.string.settings_home_shelf_show,
                        shelf.title,
                    ),
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}
