package com.nuvio.tv.ui.screens.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nuvio.tv.data.xtream.XtreamProviderCatalogRepository
import com.nuvio.tv.domain.model.CatalogRow
import com.nuvio.tv.domain.model.stableKey
import com.nuvio.tv.ui.components.CatalogRowSection
import com.nuvio.tv.ui.components.EmptyScreenState
import com.nuvio.tv.ui.components.LoadingIndicator
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class CatalogUiState(
    val loading: Boolean = true,
    val rows: List<CatalogRow> = emptyList(),
    val error: String? = null,
)

@HiltViewModel
class CatalogViewModel @Inject constructor(
    private val repository: XtreamProviderCatalogRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(CatalogUiState())
    val state = _state.asStateFlow()

    fun load() {
        if (!_state.value.loading && _state.value.rows.isNotEmpty()) return
        viewModelScope.launch {
            _state.value = CatalogUiState(loading = true)
            runCatching { repository.allCategoryRows() }
                .onSuccess { _state.value = CatalogUiState(loading = false, rows = it) }
                .onFailure { _state.value = CatalogUiState(loading = false, error = "Nao foi possivel carregar o catalogo.") }
        }
    }
}

@Composable
fun CatalogScreen(
    onNavigateToDetail: (String, String, String) -> Unit,
    viewModel: CatalogViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(Unit) { viewModel.load() }
    when {
        state.loading -> LoadingIndicator(modifier = Modifier.fillMaxSize())
        state.rows.isEmpty() -> EmptyScreenState(
            icon = Icons.Default.GridView,
            title = state.error ?: "Catalogo vazio",
            subtitle = "Configure ou atualize o servidor para ver os titulos disponiveis.",
            modifier = Modifier.fillMaxSize(),
        )
        else -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 36.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            items(state.rows, key = CatalogRow::stableKey) { row ->
                CatalogRowSection(
                    catalogRow = row,
                    onItemClick = onNavigateToDetail,
                    showSeeAll = false,
                    showAddonName = false,
                    showCatalogTypeSuffix = true,
                )
            }
        }
    }
}
