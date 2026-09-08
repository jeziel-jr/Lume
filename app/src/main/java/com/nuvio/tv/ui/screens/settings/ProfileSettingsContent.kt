@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.nuvio.tv.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ExperimentalTvMaterial3Api
import com.nuvio.tv.R
import com.nuvio.tv.ui.theme.NuvioTheme

@Composable
internal fun ProfileSettingsContent(
    initialFocusRequester: FocusRequester? = null
) {
    val viewModel: ProfileSettingsViewModel = hiltViewModel()
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val activeProfileId by viewModel.activeProfileId.collectAsStateWithLifecycle()
    var showAddDialog by remember { mutableStateOf(false) }
    var pendingDeleteId by remember { mutableStateOf<Int?>(null) }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.md)
    ) {
        SettingsDetailHeader(
            title = stringResource(R.string.profile_title),
            subtitle = stringResource(R.string.profile_subtitle)
        )
        SettingsGroupCard(modifier = Modifier.fillMaxWidth()) {
            profiles.forEach { profile ->
                val isActive = profile.id == activeProfileId
                val isPrimary = profile.isPrimary
                SettingsActionRow(
                    title = if (isPrimary) {
                        stringResource(R.string.profile_primary_label)
                    } else {
                        profile.name
                    },
                    subtitle = null,
                    onClick = {
                        if (!isActive) {
                            viewModel.setActiveProfile(profile.id)
                        }
                    },
                    modifier = if (initialFocusRequester != null &&
                        profile.id == profiles.firstOrNull()?.id
                    ) {
                        Modifier.focusRequester(initialFocusRequester)
                    } else {
                        Modifier
                    }
                )
                // Deletable when not primary; deleting the active secondary profile
                // is safe because ProfileManager refuses the primary id and the data
                // store falls back to the primary profile on active deletion.
                if (!isPrimary) {
                    SettingsActionRow(
                        title = stringResource(R.string.profile_delete_btn),
                        subtitle = null,
                        onClick = { pendingDeleteId = profile.id }
                    )
                }
            }
            if (viewModel.canAddProfile) {
                SettingsActionRow(
                    title = stringResource(R.string.profile_add_new),
                    subtitle = null,
                    onClick = { showAddDialog = true }
                )
            }
        }
    }

    if (showAddDialog) {
        AddProfileDialog(
            onConfirm = { name ->
                viewModel.createProfile(
                    name = name,
                    avatarColorHex = "#1E88E5",
                    usesPrimaryAddons = false,
                    usesPrimaryPlugins = false
                )
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false }
        )
    }

    pendingDeleteId?.let { profileId ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { pendingDeleteId = null },
            title = { Text(stringResource(R.string.profile_delete_confirm_title)) },
            text = { Text(stringResource(R.string.profile_delete_confirm_subtitle)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteProfile(profileId)
                    pendingDeleteId = null
                }) {
                    Text(stringResource(R.string.profile_delete_btn))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteId = null }) {
                    Text(stringResource(R.string.profile_cancel))
                }
            }
        )
    }
}

@Composable
private fun AddProfileDialog(
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
