# app/src/main/java/com/nuvio/tv/ui/screens/profile/

## Responsibility

TV profile selection and profile management. The same screen supports normal active-profile selection and primary-profile management, including create, edit, delete, avatar selection, profile PIN locking, and focus-safe TV overlays.

## Design

- `ProfileSelectionScreen` is a large Compose state machine around `ProfileSelectionMode`, profile cards, transient editor dialogs, delete confirmation, and a sealed `ProfilePinOverlayState`. It adapts the card grid to available width, restores focus to the active profile, handles D-pad center and long-press actions, and uses a hidden numeric text field for four-digit PIN entry.
- `ProfileSelectionViewModel` exposes `ProfileManager` flows directly for profiles and active ID, while keeping avatar catalog, operation flags, PIN state, and remote-operation callbacks in flows. Local UI state controls only overlays, draft fields, focus colors, keyboard visibility, and messages.
- PIN operations are staged: create and confirm a new PIN, verify an existing PIN before unlock/change/remove/delete, then update the remote lock service and reconcile `ProfileLockStateDataStore`. Errors reset input and provide retry or lockout feedback.

## Flow

1. The ViewModel loads the avatar catalog once and refreshes remote PIN-enabled state with bounded retries. The screen collects profile, active ID, avatar, saving, and lock flows.
2. In selection mode, choosing an unlocked profile calls `ProfileManager.setActiveProfile` and invokes `onProfileSelected`; a locked profile first enters the unlock overlay. In management mode, selection or long press opens edit and profile actions instead of switching profiles.
3. Create and edit drafts update the local profile manager, then push profile data to the remote sync service. Delete removes local profile data, deletes remote profile data, pushes the resulting profile set, and refreshes lock state.
4. Avatar selection uses the remote catalog's image URL and background color. A selected avatar is copied into the profile; clearing or changing it adjusts the stored avatar ID and URL. Primary profiles cannot be deleted, and the UI exposes the add card only when the manager allows another profile.

## Integration

The package integrates `ProfileManager` for local profile persistence and active-profile changes, `ProfileSyncService` for remote profile and PIN operations, `ProfileLockStateDataStore` for cached lock flags, `AvatarRepository` and `AvatarPickerGrid` for catalog imagery, and shared avatar/dialog components. Selection completion returns to the Activity through a callback, while profile state remains local first and remote synchronization is best effort through the ViewModel.
