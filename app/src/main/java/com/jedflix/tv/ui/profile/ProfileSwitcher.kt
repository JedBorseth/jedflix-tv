package com.jedflix.tv.ui.profile

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import androidx.compose.foundation.BorderStroke
import com.jedflix.tv.R
import com.jedflix.tv.data.library.ProfileAvatars
import com.jedflix.tv.data.library.UserProfile
import com.jedflix.tv.ui.theme.JedflixIcons
import com.jedflix.tv.ui.theme.WarmWhite
import com.jedflix.tv.ui.theme.Zinc400
import com.jedflix.tv.ui.theme.Zinc800
import com.jedflix.tv.ui.theme.Zinc950

@Composable
fun ProfileAvatarButton(
    profile: UserProfile?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val initials = profile?.name?.firstOrNull()?.uppercaseChar()?.toString().orEmpty()
    Surface(
        onClick = onClick,
        modifier = modifier
            .size(48.dp)
            .testTag("profile-avatar"),
        shape = ClickableSurfaceDefaults.shape(shape = CircleShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color(ProfileAvatars.colorArgb(profile?.avatarKey.orEmpty())),
            focusedContainerColor = Color(ProfileAvatars.colorArgb(profile?.avatarKey.orEmpty())),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.12f),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(border = BorderStroke(3.dp, WarmWhite), shape = CircleShape),
        ),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Text(
                text = initials,
                color = WarmWhite,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
            )
        }
    }
}

@Composable
fun ProfileOverlayHost(
    state: ProfileUiState,
    onSwitch: (Long) -> Unit,
    onAdd: () -> Unit,
    onEdit: (UserProfile) -> Unit,
    onClose: () -> Unit,
    onBackToPicker: () -> Unit,
    onSave: (String, String) -> Unit,
    onDelete: (Long) -> Unit,
) {
    when (val overlay = state.overlay) {
        ProfileOverlay.Hidden -> Unit
        ProfileOverlay.Picker -> ProfilePickerDialog(
            state = state,
            onSwitch = onSwitch,
            onAdd = onAdd,
            onEdit = onEdit,
            onClose = onClose,
        )
        is ProfileOverlay.Editor -> ProfileEditorDialog(
            existing = overlay.existing,
            canDelete = state.canDelete && overlay.existing != null,
            onSave = onSave,
            onDelete = { overlay.existing?.let { onDelete(it.id) } },
            onCancel = onBackToPicker,
        )
    }
}

@Composable
private fun ProfilePickerDialog(
    state: ProfileUiState,
    onSwitch: (Long) -> Unit,
    onAdd: () -> Unit,
    onEdit: (UserProfile) -> Unit,
    onClose: () -> Unit,
) {
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(state.profiles) {
        runCatching { firstFocus.requestFocus() }
    }
    BackHandler(onBack = onClose)
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Zinc950.copy(alpha = 0.94f))
                .testTag("profile-picker"),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(R.string.profile_who),
                    style = MaterialTheme.typography.displayMedium,
                    color = WarmWhite,
                )
                Spacer(Modifier.height(36.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    contentPadding = PaddingValues(horizontal = 48.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    items(state.profiles, key = { it.id }) { profile ->
                        val requester = if (profile.id == state.active?.id) firstFocus else null
                        ProfilePickCard(
                            profile = profile,
                            selected = profile.id == state.active?.id,
                            onSelect = { onSwitch(profile.id) },
                            onEdit = { onEdit(profile) },
                            modifier = requester?.let { Modifier.focusRequester(it) } ?: Modifier,
                        )
                    }
                    if (state.canAdd) {
                        item(key = "add") {
                            AddProfileCard(onClick = onAdd)
                        }
                    }
                }
                Spacer(Modifier.height(28.dp))
                Button(
                    onClick = onClose,
                    colors = ButtonDefaults.colors(
                        containerColor = Color.White.copy(alpha = 0.16f),
                        contentColor = WarmWhite,
                        focusedContainerColor = WarmWhite,
                        focusedContentColor = Zinc950,
                    ),
                ) {
                    Text(stringResource(R.string.profile_done), fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun ProfilePickCard(
    profile: UserProfile,
    selected: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.width(140.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Surface(
            onClick = onSelect,
            modifier = Modifier.size(110.dp).testTag("profile-card"),
            shape = ClickableSurfaceDefaults.shape(shape = CircleShape),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Color(ProfileAvatars.colorArgb(profile.avatarKey)),
                focusedContainerColor = Color(ProfileAvatars.colorArgb(profile.avatarKey)),
            ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
            border = ClickableSurfaceDefaults.border(
                border = if (selected) {
                    Border(border = BorderStroke(3.dp, WarmWhite.copy(alpha = 0.55f)), shape = CircleShape)
                } else {
                    Border.None
                },
                focusedBorder = Border(border = BorderStroke(4.dp, WarmWhite), shape = CircleShape),
            ),
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Text(
                    text = profile.name.firstOrNull()?.uppercaseChar()?.toString().orEmpty(),
                    color = WarmWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = 40.sp,
                )
            }
        }
        Text(
            text = profile.name,
            style = MaterialTheme.typography.titleMedium,
            color = WarmWhite,
            maxLines = 1,
        )
        Button(
            onClick = onEdit,
            colors = ButtonDefaults.colors(
                containerColor = Color.Transparent,
                contentColor = Zinc400,
                focusedContainerColor = WarmWhite,
                focusedContentColor = Zinc950,
            ),
        ) {
            Text(stringResource(R.string.profile_edit), fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun AddProfileCard(onClick: () -> Unit) {
    Column(
        modifier = Modifier.width(140.dp).testTag("profile-add"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Surface(
            onClick = onClick,
            modifier = Modifier.size(110.dp),
            shape = ClickableSurfaceDefaults.shape(shape = CircleShape),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Zinc800,
                focusedContainerColor = Zinc800,
            ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
            border = ClickableSurfaceDefaults.border(
                focusedBorder = Border(border = BorderStroke(4.dp, WarmWhite), shape = CircleShape),
            ),
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(JedflixIcons.Add, contentDescription = null, modifier = Modifier.size(42.dp))
            }
        }
        Text(
            text = stringResource(R.string.profile_add),
            style = MaterialTheme.typography.titleMedium,
            color = WarmWhite,
        )
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun ProfileEditorDialog(
    existing: UserProfile?,
    canDelete: Boolean,
    onSave: (String, String) -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
) {
    var name by remember(existing?.id) { mutableStateOf(existing?.name.orEmpty()) }
    var avatarKey by remember(existing?.id) {
        mutableStateOf(existing?.avatarKey ?: ProfileAvatars.defaultKey)
    }
    val nameFocus = remember { FocusRequester() }
    LaunchedEffect(existing?.id) { runCatching { nameFocus.requestFocus() } }
    BackHandler(onBack = onCancel)

    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Zinc950.copy(alpha = 0.94f))
                .testTag("profile-editor"),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(0.7f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(
                        if (existing == null) R.string.profile_create_title else R.string.profile_edit,
                    ),
                    style = MaterialTheme.typography.displayMedium,
                    color = WarmWhite,
                )
                Spacer(Modifier.height(28.dp))
                ColorAvatar(avatarKey = avatarKey, size = 96.dp, name = name)
                Spacer(Modifier.height(20.dp))
                NameField(
                    value = name,
                    onValueChange = { if (it.length <= 20) name = it },
                    modifier = Modifier.focusRequester(nameFocus).fillMaxWidth(0.55f),
                )
                Spacer(Modifier.height(22.dp))
                Text(
                    text = stringResource(R.string.profile_picture),
                    style = MaterialTheme.typography.titleMedium,
                    color = Zinc400,
                )
                Spacer(Modifier.height(12.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                ) {
                    items(ProfileAvatars.all, key = { it.key }) { preset ->
                        ColorChoice(
                            color = Color(preset.colorArgb),
                            selected = preset.key == avatarKey,
                            onClick = { avatarKey = preset.key },
                        )
                    }
                }
                Spacer(Modifier.height(32.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = { onSave(name, avatarKey) },
                        enabled = name.trim().isNotEmpty(),
                        colors = ButtonDefaults.colors(
                            containerColor = WarmWhite,
                            contentColor = Zinc950,
                            focusedContainerColor = WarmWhite,
                            focusedContentColor = Zinc950,
                        ),
                    ) {
                        Text(stringResource(R.string.profile_save), fontWeight = FontWeight.SemiBold)
                    }
                    Button(
                        onClick = onCancel,
                        colors = ButtonDefaults.colors(
                            containerColor = Color.White.copy(alpha = 0.16f),
                            contentColor = WarmWhite,
                            focusedContainerColor = WarmWhite,
                            focusedContentColor = Zinc950,
                        ),
                    ) {
                        Text(stringResource(R.string.profile_cancel), fontWeight = FontWeight.SemiBold)
                    }
                    if (canDelete && existing != null) {
                        Button(
                            onClick = onDelete,
                            colors = ButtonDefaults.colors(
                                containerColor = Color.White.copy(alpha = 0.12f),
                                contentColor = WarmWhite,
                                focusedContainerColor = Color(0xFFB91C1C),
                                focusedContentColor = WarmWhite,
                            ),
                        ) {
                            Icon(JedflixIcons.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.profile_delete), fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ColorAvatar(avatarKey: String, size: Dp, name: String) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(Color(ProfileAvatars.colorArgb(avatarKey))),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = name.trim().firstOrNull()?.uppercaseChar()?.toString().orEmpty(),
            color = WarmWhite,
            fontWeight = FontWeight.Bold,
            fontSize = (size.value * 0.4f).sp,
        )
    }
}

@Composable
private fun ColorChoice(color: Color, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(52.dp),
        shape = ClickableSurfaceDefaults.shape(shape = CircleShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = color,
            focusedContainerColor = color,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.12f),
        border = ClickableSurfaceDefaults.border(
            border = if (selected) {
                Border(border = BorderStroke(3.dp, WarmWhite), shape = CircleShape)
            } else {
                Border.None
            },
            focusedBorder = Border(border = BorderStroke(3.dp, WarmWhite), shape = CircleShape),
        ),
    ) {}
}

@Composable
private fun NameField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(52.dp)
            .background(Zinc800, androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (value.isEmpty()) {
            Text(
                text = stringResource(R.string.profile_name_placeholder),
                color = Zinc400,
                style = MaterialTheme.typography.titleMedium,
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(color = WarmWhite, fontSize = 18.sp),
            cursorBrush = SolidColor(WarmWhite),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Done,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
