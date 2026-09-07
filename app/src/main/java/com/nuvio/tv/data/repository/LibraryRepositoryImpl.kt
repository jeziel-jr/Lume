package com.nuvio.tv.data.repository

import com.nuvio.tv.data.local.LibraryPreferences
import com.nuvio.tv.domain.model.LibraryEntry
import com.nuvio.tv.domain.model.LibraryEntryInput
import com.nuvio.tv.domain.model.LibrarySourceMode
import com.nuvio.tv.domain.model.ListMembershipChanges
import com.nuvio.tv.domain.model.ListMembershipSnapshot
import com.nuvio.tv.domain.model.SavedLibraryItem
import com.nuvio.tv.domain.repository.LibraryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * On-device library backed by [LibraryPreferences].
 */
@Singleton
class LibraryRepositoryImpl @Inject constructor(
    private val libraryPreferences: LibraryPreferences,
) : LibraryRepository {

    override val sourceMode: Flow<LibrarySourceMode> = flowOf(LibrarySourceMode.LOCAL)

    override val isSyncing: Flow<Boolean> = flowOf(false)

    override val libraryItems: Flow<List<LibraryEntry>> = libraryPreferences.libraryItems
        .map { items ->
            items.map { saved ->
                LibraryEntry(
                    id = saved.id,
                    type = saved.type,
                    name = saved.name,
                    poster = saved.poster,
                    posterShape = saved.posterShape,
                    background = saved.background,
                    logo = saved.logo,
                    description = saved.description,
                    releaseInfo = saved.releaseInfo,
                    imdbRating = saved.imdbRating,
                    genres = saved.genres,
                    addonBaseUrl = saved.addonBaseUrl,
                    listedAt = saved.addedAt
                )
            }
        }
        .distinctUntilChanged()

    override fun isInLibrary(itemId: String, itemType: String): Flow<Boolean> {
        return libraryPreferences.isInLibrary(itemId = itemId, itemType = itemType)
            .distinctUntilChanged()
    }

    override fun isInWatchlist(itemId: String, itemType: String): Flow<Boolean> {
        return libraryPreferences.isInLibrary(itemId = itemId, itemType = itemType)
            .distinctUntilChanged()
    }

    override suspend fun toggleDefault(item: LibraryEntryInput) {
        val isInLocal = libraryPreferences.isInLibrary(item.itemId, item.itemType).first()
        if (isInLocal) {
            libraryPreferences.removeItem(itemId = item.itemId, itemType = item.itemType)
        } else {
            libraryPreferences.addItem(item.toSavedLibraryItem())
        }
    }

    override suspend fun getMembershipSnapshot(item: LibraryEntryInput): ListMembershipSnapshot {
        val inLocal = libraryPreferences.isInLibrary(item.itemId, item.itemType).first()
        return ListMembershipSnapshot(
            listMembership = mapOf(LOCAL_LIST_KEY to inLocal)
        )
    }

    override suspend fun applyMembershipChanges(item: LibraryEntryInput, changes: ListMembershipChanges) {
        val desired = changes.desiredMembership
        val localDesired = desired[LOCAL_LIST_KEY] == true
        val currentlyInLocal = libraryPreferences.isInLibrary(item.itemId, item.itemType).first()
        if (localDesired != currentlyInLocal) {
            if (localDesired) {
                libraryPreferences.addItem(item.toSavedLibraryItem())
            } else {
                libraryPreferences.removeItem(itemId = item.itemId, itemType = item.itemType)
            }
        }
    }

    private fun LibraryEntryInput.toSavedLibraryItem(): SavedLibraryItem {
        return SavedLibraryItem(
            id = itemId,
            type = itemType,
            name = title,
            poster = poster,
            posterShape = posterShape,
            background = background,
            description = description,
            releaseInfo = releaseInfo,
            imdbRating = imdbRating,
            genres = genres,
            addonBaseUrl = addonBaseUrl,
            logo = logo
        )
    }

    companion object {
        private const val LOCAL_LIST_KEY = "local"
    }
}
