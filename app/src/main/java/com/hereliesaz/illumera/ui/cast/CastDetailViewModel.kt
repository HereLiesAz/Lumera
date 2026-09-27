package com.hereliesaz.illumera.ui.cast

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hereliesaz.illumera.data.tmdb.TmdbMetadataService
import com.hereliesaz.illumera.data.tmdb.TmdbPersonDetail
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class CastDetailState {
    data object Loading : CastDetailState()
    data class Success(val person: TmdbPersonDetail) : CastDetailState()
    data class Error(val message: String) : CastDetailState()
}

/** One cast page's person, given by its back-stack entry (CastKey) through [Factory]. */
@HiltViewModel(assistedFactory = CastDetailViewModel.Factory::class)
class CastDetailViewModel @AssistedInject constructor(
    private val tmdbMetadataService: TmdbMetadataService,
    @Assisted val personId: Int
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(personId: Int): CastDetailViewModel
    }

    private val _state = MutableStateFlow<CastDetailState>(CastDetailState.Loading)
    val state: StateFlow<CastDetailState> = _state

    init {
        loadPersonDetail()
    }

    fun retry() {
        loadPersonDetail()
    }

    private fun loadPersonDetail() {
        _state.value = CastDetailState.Loading
        viewModelScope.launch {
            val detail = tmdbMetadataService.fetchPersonDetail(personId)
            if (detail != null) {
                _state.value = CastDetailState.Success(detail)
            } else {
                _state.value = CastDetailState.Error("Failed to load person details")
            }
        }
    }
}
