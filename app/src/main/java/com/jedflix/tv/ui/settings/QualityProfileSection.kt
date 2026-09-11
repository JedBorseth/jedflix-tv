package com.jedflix.tv.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.jedflix.tv.R
import com.jedflix.tv.data.settings.QualityProfile
import com.jedflix.tv.ui.theme.JedflixIcons
import com.jedflix.tv.ui.theme.WarmWhite
import com.jedflix.tv.ui.theme.Zinc400
import com.jedflix.tv.ui.theme.Zinc800
import com.jedflix.tv.ui.theme.Zinc950

@Composable
fun QualityProfileSection(
    selected: QualityProfile,
    onSelect: (QualityProfile) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val dropdownFocus = remember { FocusRequester() }
    val optionFocus = remember { QualityProfile.entries.associateWith { FocusRequester() } }
    val shape = RoundedCornerShape(8.dp)

    BackHandler(enabled = expanded) { expanded = false }

    LaunchedEffect(expanded, selected) {
        if (!expanded) return@LaunchedEffect
        withFrameNanos { }
        runCatching { optionFocus.getValue(selected).requestFocus() }
    }

    Column(modifier = Modifier.testTag("settings-quality")) {
        Text(
            text = stringResource(R.string.settings_quality_heading),
            style = MaterialTheme.typography.headlineMedium,
            color = WarmWhite,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.settings_quality_description),
            style = MaterialTheme.typography.bodyLarge,
            color = Zinc400,
        )
        Spacer(Modifier.height(20.dp))
        Surface(
            onClick = { expanded = !expanded },
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(dropdownFocus)
                .testTag("settings-quality-dropdown"),
            shape = ClickableSurfaceDefaults.shape(shape),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Zinc800,
                focusedContainerColor = Zinc800,
                pressedContainerColor = Zinc800,
                contentColor = WarmWhite,
                focusedContentColor = WarmWhite,
            ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f, pressedScale = 1f),
            border = ClickableSurfaceDefaults.border(
                focusedBorder = Border(border = BorderStroke(2.dp, WarmWhite), shape = shape),
            ),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = selected.label(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = WarmWhite,
                    )
                    if (!expanded) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = selected.description(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Zinc400,
                        )
                    }
                }
                Icon(
                    imageVector = if (expanded) JedflixIcons.ExpandLess else JedflixIcons.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Column(
                modifier = Modifier.padding(top = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                QualityProfile.entries.forEach { profile ->
                    QualityProfileOption(
                        profile = profile,
                        selected = profile == selected,
                        modifier = Modifier.focusRequester(optionFocus.getValue(profile)),
                        onClick = {
                            onSelect(profile)
                            expanded = false
                            runCatching { dropdownFocus.requestFocus() }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun QualityProfileOption(
    profile: QualityProfile,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(8.dp)
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .testTag("settings-quality-option-${profile.stored}"),
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) Zinc800 else Zinc950,
            focusedContainerColor = WarmWhite,
            pressedContainerColor = Zinc800,
            contentColor = WarmWhite,
            focusedContentColor = Zinc950,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f, pressedScale = 1f),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(border = BorderStroke(2.dp, WarmWhite), shape = shape),
            border = if (selected) {
                Border(border = BorderStroke(2.dp, WarmWhite.copy(alpha = 0.45f)), shape = shape)
            } else {
                Border.None
            },
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = profile.label(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = profile.description(),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (selected) {
                Icon(
                    imageVector = JedflixIcons.Check,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun QualityProfile.label(): String = stringResource(
    when (this) {
        QualityProfile.Max -> R.string.settings_quality_max
        QualityProfile.Medium -> R.string.settings_quality_medium
        QualityProfile.Low -> R.string.settings_quality_low
    },
)

@Composable
private fun QualityProfile.description(): String = stringResource(
    when (this) {
        QualityProfile.Max -> R.string.settings_quality_max_description
        QualityProfile.Medium -> R.string.settings_quality_medium_description
        QualityProfile.Low -> R.string.settings_quality_low_description
    },
)
