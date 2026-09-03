package com.jedflix.tv.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.jedflix.tv.R
import com.jedflix.tv.data.settings.SettingsStore
import com.jedflix.tv.data.tmdb.CatalogSection
import com.jedflix.tv.ui.components.ContentStartPadding
import com.jedflix.tv.ui.components.JedflixDrawer
import com.jedflix.tv.ui.components.RailCollapsedWidth
import com.jedflix.tv.ui.theme.WarmWhite
import com.jedflix.tv.ui.theme.Zinc400
import com.jedflix.tv.ui.theme.Zinc800
import com.jedflix.tv.ui.theme.Zinc950

@Composable
fun SettingsScreen(
    settingsStore: SettingsStore,
    onSectionSelected: (CatalogSection) -> Unit,
    onSearch: () -> Unit,
) {
    val viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory(settingsStore))
    val state by viewModel.state.collectAsStateWithLifecycle()
    val fieldFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        runCatching { fieldFocus.requestFocus() }
    }

    JedflixDrawer(
        selected = null,
        searchSelected = false,
        settingsSelected = true,
        onSelect = onSectionSelected,
        onSearch = onSearch,
        onSettings = {},
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Zinc950)
                .padding(start = RailCollapsedWidth)
                .testTag("settings"),
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 720.dp)
                    .padding(start = ContentStartPadding, end = 48.dp, top = 36.dp),
            ) {
                Text(
                    text = stringResource(R.string.settings_title),
                    style = MaterialTheme.typography.displayMedium,
                    color = WarmWhite,
                    fontWeight = FontWeight.Black,
                )
                Spacer(Modifier.height(36.dp))
                Text(
                    text = stringResource(R.string.settings_rd_heading),
                    style = MaterialTheme.typography.headlineMedium,
                    color = WarmWhite,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.settings_rd_description),
                    style = MaterialTheme.typography.bodyLarge,
                    color = Zinc400,
                )
                Spacer(Modifier.height(20.dp))
                ApiKeyField(
                    value = state.apiKey,
                    onValueChange = viewModel::onApiKeyChange,
                    onDone = viewModel::save,
                    modifier = Modifier.focusRequester(fieldFocus),
                )
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = viewModel::save,
                    modifier = Modifier.testTag("settings-save"),
                ) {
                    Text(stringResource(R.string.settings_save))
                }
            }
        }
    }
}

@Composable
private fun ApiKeyField(
    value: String,
    onValueChange: (String) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .background(Zinc800, RoundedCornerShape(8.dp))
            .padding(horizontal = 16.dp)
            .testTag("settings-rd-key"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) {
                Text(
                    text = stringResource(R.string.settings_rd_key_placeholder),
                    color = Zinc400,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                textStyle = TextStyle(
                    color = WarmWhite,
                    fontSize = 18.sp,
                ),
                cursorBrush = SolidColor(WarmWhite),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    keyboardType = KeyboardType.Ascii,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { onDone() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
