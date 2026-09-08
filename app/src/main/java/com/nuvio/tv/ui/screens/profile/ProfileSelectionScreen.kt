@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.nuvio.tv.ui.screens.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.nuvio.tv.R
import com.nuvio.tv.core.profile.ProfileManager
import com.nuvio.tv.domain.model.UserProfile
import com.nuvio.tv.ui.components.ProfileAvatarCircle
import com.nuvio.tv.ui.theme.NuvioTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class ProfileSelectionViewModel @Inject constructor(
    private val profileManager: ProfileManager
) : ViewModel() {

    val profiles: StateFlow<List<UserProfile>> = profileManager.profiles

    val activeProfileId: StateFlow<Int> = profileManager.activeProfileId

    val canAddProfile: Boolean
        get() = profileManager.canCreateProfile

    /** Persists the selection; false when the profile no longer exists. */
    suspend fun selectProfile(id: Int): Boolean {
        if (profiles.value.none { it.id == id }) return false
        profileManager.setActiveProfile(id)
        return true
    }

    /** Creates a profile and activates it; returns the new profile id on success. */
    suspend fun createProfileAndSelect(name: String): Int? {
        val previousIds = profiles.value.map { it.id }.toSet()
        val created = profileManager.createProfile(
            name = name,
            avatarColorHex = NEW_PROFILE_AVATAR_COLOR
        )
        if (!created) return null
        val newProfile = profiles.value.firstOrNull { it.id !in previousIds } ?: return null
        profileManager.setActiveProfile(newProfile.id)
        return newProfile.id
    }

    private companion object {
        // Same fixed color the Settings "Add profile" flow uses.
        const val NEW_PROFILE_AVATAR_COLOR = "#1E88E5"
    }
}

/**
 * Full-screen profile picker shown on cold start when more than one profile exists
 * and "remember last profile" is not in effect. Selecting or creating a profile
 * activates it and continues to Home. Management stays in Settings > Perfis.
 */
@Composable
fun ProfileSelectionScreen(
    onProfileSelected: () -> Unit,
    viewModel: ProfileSelectionViewModel = hiltViewModel()
) {
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val activeProfileId by viewModel.activeProfileId.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var showAddDialog by remember { mutableStateOf(false) }

    val canAddProfile = viewModel.canAddProfile
    val totalItems = profiles.size + if (canAddProfile) 1 else 0
    val focusRequesters = remember(totalItems) {
        List(totalItems) { FocusRequester() }
    }

    // Land d-pad focus on the active profile so a single OK press continues.
    LaunchedEffect(totalItems, profiles, activeProfileId) {
        val activeIndex = profiles.indexOfFirst { it.id == activeProfileId }
        val initialIndex = activeIndex.takeIf { it >= 0 } ?: 0
        repeat(2) { withFrameNanos { } }
        if (initialIndex in focusRequesters.indices) {
            runCatching { focusRequesters[initialIndex].requestFocus() }
        }
    }

    fun selectAndContinue(profile: UserProfile) {
        scope.launch {
            if (viewModel.selectProfile(profile.id)) {
                onProfileSelected()
            }
        }
    }

    fun createAndContinue(name: String) {
        scope.launch {
            if (viewModel.createProfileAndSelect(name) != null) {
                onProfileSelected()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 64.dp, vertical = NuvioTheme.spacing.xxxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.profile_selection_title),
            style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
            color = NuvioTheme.colors.TextPrimary,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(NuvioTheme.spacing.md))
        Text(
            text = stringResource(R.string.profile_selection_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = NuvioTheme.colors.TextSecondary,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(NuvioTheme.spacing.xxxl))

        if (totalItems == 0) {
            Text(
                text = stringResource(R.string.profile_selection_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = NuvioTheme.colors.TextSecondary
            )
        } else {
            ProfileRow(
                profiles = profiles,
                activeProfileId = activeProfileId,
                canAddProfile = canAddProfile,
                focusRequesters = focusRequesters,
                onProfileSelected = ::selectAndContinue,
                onAddProfileClick = { showAddDialog = true }
            )
        }
    }

    if (showAddDialog) {
        AddProfileNameDialog(
            onConfirm = { name ->
                showAddDialog = false
                createAndContinue(name)
            },
            onDismiss = { showAddDialog = false }
        )
    }
}

@Composable
private fun ProfileRow(
    profiles: List<UserProfile>,
    activeProfileId: Int,
    canAddProfile: Boolean,
    focusRequesters: List<FocusRequester>,
    onProfileSelected: (UserProfile) -> Unit,
    onAddProfileClick: () -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        val itemGap = 20.dp
        val gapCount = (profiles.size + if (canAddProfile) 1 else 0) - 1
        val maxItemWidth = if (gapCount > 0) {
            (maxWidth - itemGap * gapCount) / (gapCount + 1)
        } else {
            maxWidth
        }
        val itemWidth = minOf(TileWidth, maxItemWidth)

        Row(
            horizontalArrangement = Arrangement.spacedBy(itemGap),
            verticalAlignment = Alignment.Top
        ) {
            profiles.forEachIndexed { index, profile ->
                ProfileTile(
                    profile = profile,
                    isActive = profile.id == activeProfileId,
                    focusRequester = focusRequesters[index],
                    tileWidth = itemWidth,
                    onClick = { onProfileSelected(profile) },
                    modifier = Modifier.width(itemWidth)
                )
            }
            if (canAddProfile) {
                AddProfileTile(
                    focusRequester = focusRequesters[profiles.size],
                    tileWidth = itemWidth,
                    onClick = onAddProfileClick,
                    modifier = Modifier.width(itemWidth)
                )
            }
        }
    }
}

@Composable
private fun ProfileTile(
    profile: UserProfile,
    isActive: Boolean,
    focusRequester: FocusRequester,
    tileWidth: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }

    Card(
        onClick = onClick,
        modifier = modifier
            .focusRequester(focusRequester)
            .onFocusChanged { isFocused = it.isFocused },
        colors = CardDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color.Transparent
        ),
        border = CardDefaults.border(
            border = Border.None,
            focusedBorder = Border(
                border = BorderStroke(NuvioTheme.spacing.xxs, NuvioTheme.colors.FocusRing),
                shape = RoundedCornerShape(NuvioTheme.radii.xl)
            )
        ),
        shape = CardDefaults.shape(RoundedCornerShape(NuvioTheme.radii.xl)),
        scale = CardDefaults.scale(focusedScale = 1.05f)
    ) {
        Column(
            modifier = Modifier
                .width(tileWidth)
                .padding(horizontal = NuvioTheme.spacing.sm, vertical = NuvioTheme.spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ProfileAvatarCircle(
                name = profile.name,
                colorHex = profile.avatarColorHex,
                size = AvatarSize,
                isSelected = isActive
            )
            Spacer(modifier = Modifier.height(NuvioTheme.spacing.md))
            Text(
                text = profile.name,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = if (isFocused || isActive) FontWeight.SemiBold else FontWeight.Medium
                ),
                color = if (isFocused || isActive) {
                    NuvioTheme.colors.TextPrimary
                } else {
                    NuvioTheme.colors.TextSecondary
                },
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(NuvioTheme.spacing.xs))
            Box(modifier = Modifier.height(MetaSlotHeight)) {
                if (profile.isPrimary) {
                    Text(
                        text = stringResource(R.string.profile_selection_primary_badge),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = PrimaryBadgeColor
                    )
                }
            }
        }
    }
}

@Composable
private fun AddProfileTile(
    focusRequester: FocusRequester,
    tileWidth: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }

    Card(
        onClick = onClick,
        modifier = modifier
            .focusRequester(focusRequester)
            .onFocusChanged { isFocused = it.isFocused },
        colors = CardDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color.Transparent
        ),
        border = CardDefaults.border(
            border = Border.None,
            focusedBorder = Border(
                border = BorderStroke(NuvioTheme.spacing.xxs, NuvioTheme.colors.FocusRing),
                shape = RoundedCornerShape(NuvioTheme.radii.xl)
            )
        ),
        shape = CardDefaults.shape(RoundedCornerShape(NuvioTheme.radii.xl)),
        scale = CardDefaults.scale(focusedScale = 1.05f)
    ) {
        Column(
            modifier = Modifier
                .width(tileWidth)
                .padding(horizontal = NuvioTheme.spacing.sm, vertical = NuvioTheme.spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(AvatarSize)
                    .clip(CircleShape)
                    .border(
                        width = NuvioTheme.spacing.xxs,
                        color = if (isFocused) {
                            NuvioTheme.colors.FocusRing
                        } else {
                            NuvioTheme.colors.Border
                        },
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    tint = if (isFocused) {
                        NuvioTheme.colors.TextPrimary
                    } else {
                        NuvioTheme.colors.TextSecondary
                    },
                    modifier = Modifier.size(AvatarIconSize)
                )
            }
            Spacer(modifier = Modifier.height(NuvioTheme.spacing.md))
            Text(
                text = stringResource(R.string.profile_add),
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = if (isFocused) FontWeight.SemiBold else FontWeight.Medium
                ),
                color = if (isFocused) {
                    NuvioTheme.colors.TextPrimary
                } else {
                    NuvioTheme.colors.TextSecondary
                },
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(NuvioTheme.spacing.xs))
            Spacer(modifier = Modifier.height(MetaSlotHeight))
        }
    }
}

/** Name-only dialog, styled like the Add Profile dialog used in Settings > Perfis. */
@Composable
private fun AddProfileNameDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.profile_create_title)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text(stringResource(R.string.profile_name_placeholder)) }
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.trim()) },
                enabled = name.isNotBlank()
            ) {
                Text(stringResource(R.string.profile_create_btn))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.profile_cancel))
            }
        }
    )
}

private val TileWidth = 148.dp
private val AvatarSize = 92.dp
private val AvatarIconSize = 40.dp
private val MetaSlotHeight = 18.dp
private val PrimaryBadgeColor = Color(0xFFFFB300)
