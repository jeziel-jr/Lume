package com.nuvio.tv.data.xtream

import com.nuvio.tv.domain.model.MetaPreview
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class CatalogAvailabilityTracker(
    scope: CoroutineScope,
    private val service: XtreamCatalogAvailabilityService,
) {
    private val items = MutableStateFlow<List<MetaPreview>>(emptyList())
    private val _availability = MutableStateFlow<Map<String, CatalogPlaybackAvailability>>(emptyMap())
    val availability: StateFlow<Map<String, CatalogPlaybackAvailability>> = _availability.asStateFlow()

    init {
        scope.launch {
            combine(
                items,
                service.catalogState,
                service.availabilityRevision,
                service.aliasRevision,
            ) { trackedItems, catalogState, _, _ ->
                trackedItems to catalogState
            }.collectLatest { (trackedItems, catalogState) ->
                _availability.value = when {
                    trackedItems.isEmpty() -> emptyMap()
                    catalogState == XtreamCatalogState.Loading -> trackedItems.associate {
                        it.catalogAvailabilityKey() to CatalogPlaybackAvailability.UNKNOWN
                    }
                    catalogState is XtreamCatalogState.Error -> emptyMap()
                    else -> service.classify(trackedItems)
                }
            }
        }
    }

    fun submit(newItems: List<MetaPreview>) {
        items.value = newItems
            .asSequence()
            .filterNot { it.id.startsWith("__placeholder_") }
            .distinctBy(MetaPreview::catalogAvailabilityKey)
            .toList()
    }
}
