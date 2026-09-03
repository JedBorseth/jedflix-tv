package com.jedflix.tv.ui.components

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.DrawerValue
import androidx.tv.material3.Icon
import androidx.tv.material3.ModalNavigationDrawer
import androidx.tv.material3.NavigationDrawerItem
import androidx.tv.material3.NavigationDrawerItemDefaults
import androidx.tv.material3.NavigationDrawerScope
import androidx.tv.material3.Text
import com.jedflix.tv.R
import com.jedflix.tv.data.tmdb.CatalogSection
import com.jedflix.tv.ui.theme.JedflixIcons
import com.jedflix.tv.ui.theme.JedflixRed
import com.jedflix.tv.ui.theme.WarmWhite
import com.jedflix.tv.ui.theme.Zinc400
import com.jedflix.tv.ui.theme.Zinc900
import com.jedflix.tv.ui.theme.Zinc950

/** Horizontal space the collapsed rail occupies; catalog content starts after it. */
val RailCollapsedWidth = 80.dp

@Composable
fun JedflixDrawer(
    selected: CatalogSection?,
    searchSelected: Boolean,
    onSelect: (CatalogSection) -> Unit,
    onSearch: () -> Unit,
    content: @Composable () -> Unit,
) {
    ModalNavigationDrawer(
        drawerContent = { drawerValue ->
            JedflixNavRail(
                drawerValue = drawerValue,
                selected = selected,
                searchSelected = searchSelected,
                onSelect = onSelect,
                onSearch = onSearch,
            )
        },
        scrimBrush = Brush.horizontalGradient(
            0f to Zinc950.copy(alpha = 0.96f),
            0.45f to Zinc950.copy(alpha = 0.8f),
            1f to Zinc950.copy(alpha = 0.55f),
        ),
        content = content,
    )
}

/** Netflix-TV style left rail: icons when collapsed, labels slide in when the rail has focus. */
@Composable
fun NavigationDrawerScope.JedflixNavRail(
    drawerValue: DrawerValue,
    selected: CatalogSection?,
    searchSelected: Boolean,
    onSelect: (CatalogSection) -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sectionFocus = remember { CatalogSection.entries.associateWith { FocusRequester() } }
    val searchFocus = remember { FocusRequester() }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .padding(horizontal = 12.dp, vertical = 24.dp)
            .testTag("nav-rail")
            .focusProperties {
                onEnter = {
                    if (searchSelected) {
                        searchFocus.requestFocus()
                    } else {
                        selected?.let { sectionFocus.getValue(it).requestFocus() }
                    }
                }
            }
            .focusGroup(),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(modifier = Modifier.height(40.dp).padding(start = 12.dp), contentAlignment = Alignment.CenterStart) {
            if (drawerValue == DrawerValue.Open) {
                JedflixWordmark(fontSize = 22.sp)
            } else {
                Text(
                    text = "J",
                    color = JedflixRed,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Black,
                )
            }
        }
        Spacer(Modifier.height(16.dp))

        RailItem(
            icon = JedflixIcons.Search,
            label = stringResource(R.string.nav_search),
            selected = searchSelected,
            onClick = onSearch,
            testTag = "nav-search",
            modifier = Modifier.focusRequester(searchFocus),
        )
        RailItem(
            icon = JedflixIcons.Home,
            label = stringResource(R.string.nav_home),
            selected = selected == CatalogSection.HOME,
            onClick = { onSelect(CatalogSection.HOME) },
            testTag = "nav-home",
            modifier = Modifier.focusRequester(sectionFocus.getValue(CatalogSection.HOME)),
        )
        RailItem(
            icon = JedflixIcons.Movie,
            label = stringResource(R.string.nav_movies),
            selected = selected == CatalogSection.MOVIES,
            onClick = { onSelect(CatalogSection.MOVIES) },
            testTag = "nav-movies",
            modifier = Modifier.focusRequester(sectionFocus.getValue(CatalogSection.MOVIES)),
        )
        RailItem(
            icon = JedflixIcons.Tv,
            label = stringResource(R.string.nav_shows),
            selected = selected == CatalogSection.SHOWS,
            onClick = { onSelect(CatalogSection.SHOWS) },
            testTag = "nav-shows",
            modifier = Modifier.focusRequester(sectionFocus.getValue(CatalogSection.SHOWS)),
        )
    }
}

@Composable
private fun NavigationDrawerScope.RailItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
) {
    NavigationDrawerItem(
        selected = selected,
        onClick = onClick,
        modifier = modifier.testTag(testTag),
        leadingContent = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
            )
        },
        colors = NavigationDrawerItemDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = Zinc400,
            focusedContainerColor = WarmWhite,
            focusedContentColor = Zinc900,
            selectedContainerColor = Zinc900,
            selectedContentColor = WarmWhite,
            focusedSelectedContainerColor = WarmWhite,
            focusedSelectedContentColor = Zinc900,
        ),
    ) {
        Text(text = label, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
    }
}
