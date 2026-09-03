package com.jedflix.tv.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.jedflix.tv.data.library.ProfileAvatars
import com.jedflix.tv.data.library.UserLibraryRepository
import com.jedflix.tv.data.library.UserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface ProfileOverlay {
    data object Hidden : ProfileOverlay
    data object Picker : ProfileOverlay
    data class Editor(val existing: UserProfile?) : ProfileOverlay
}

data class ProfileUiState(
    val profiles: List<UserProfile> = emptyList(),
    val active: UserProfile? = null,
    val overlay: ProfileOverlay = ProfileOverlay.Hidden,
) {
    val canAdd: Boolean get() = profiles.size < UserLibraryRepository.MAX_PROFILES
    val canDelete: Boolean get() = profiles.size > 1
}

class ProfileViewModel(
    private val library: UserLibraryRepository,
) : ViewModel() {

    private val overlay = MutableStateFlow<ProfileOverlay>(ProfileOverlay.Hidden)

    val state: StateFlow<ProfileUiState> = combine(
        library.observeProfiles(),
        library.observeActiveProfile(),
        overlay,
    ) { profiles, active, overlayState ->
        ProfileUiState(profiles = profiles, active = active, overlay = overlayState)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProfileUiState())

    fun openPicker() {
        overlay.value = ProfileOverlay.Picker
    }

    fun close() {
        overlay.value = ProfileOverlay.Hidden
    }

    fun openCreate() {
        if (!state.value.canAdd) return
        overlay.value = ProfileOverlay.Editor(existing = null)
    }

    fun openEdit(profile: UserProfile) {
        overlay.value = ProfileOverlay.Editor(existing = profile)
    }

    fun backToPicker() {
        overlay.value = ProfileOverlay.Picker
    }

    fun switchTo(id: Long) {
        viewModelScope.launch {
            library.switchProfile(id)
            overlay.value = ProfileOverlay.Hidden
        }
    }

    fun save(name: String, avatarKey: String) {
        val editor = overlay.value as? ProfileOverlay.Editor ?: return
        viewModelScope.launch {
            val key = avatarKey.ifBlank { ProfileAvatars.defaultKey }
            val existing = editor.existing
            if (existing == null) {
                val created = library.createProfile(name, key)
                library.switchProfile(created.id)
            } else {
                library.updateProfile(existing.id, name, key)
            }
            overlay.value = ProfileOverlay.Picker
        }
    }

    fun delete(id: Long) {
        if (!state.value.canDelete) return
        viewModelScope.launch {
            library.deleteProfile(id)
            overlay.value = ProfileOverlay.Picker
        }
    }

    class Factory(
        private val library: UserLibraryRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
            ProfileViewModel(library) as T
    }
}
