package com.example.ui.viewmodel

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.repository.HookRepository
import com.example.domain.model.CreatorCadenceSummary
import com.example.domain.model.VideoAnalysis
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val TAG = "HomeViewModel"

sealed interface HomeBannerState {
    data object Idle : HomeBannerState
    data object Importing : HomeBannerState
    data class Error(val message: String) : HomeBannerState
}

data class HomeUiState(
    val history: List<VideoAnalysis> = emptyList(),
    val cadenceSummary: CreatorCadenceSummary? = null,
    val bannerState: HomeBannerState = HomeBannerState.Idle
)

class HomeViewModel(
    private val repository: HookRepository
) : ViewModel() {

    private val _bannerState = MutableStateFlow<HomeBannerState>(HomeBannerState.Idle)
    val bannerState: StateFlow<HomeBannerState> = _bannerState.asStateFlow()

    val uiState: StateFlow<HomeUiState> = combine(
        repository.observeHistory(),
        repository.observeCadenceSummary(),
        _bannerState
    ) { history, cadence, banner ->
        HomeUiState(
            history = history,
            cadenceSummary = cadence,
            bannerState = banner
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HomeUiState()
    )

    fun onVideoPicked(uri: Uri, onAnalysisStarted: (String) -> Unit) {
        viewModelScope.launch {
            _bannerState.value = HomeBannerState.Importing
            try {
                val videoId = repository.importAndAnalyzeVideo(uri)
                _bannerState.value = HomeBannerState.Idle
                onAnalysisStarted(videoId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to import or analyze video", e)
                _bannerState.value = HomeBannerState.Error(
                    e.localizedMessage ?: "Unable to process selected video."
                )
            }
        }
    }

    fun retryAnalysis(videoId: String) {
        viewModelScope.launch {
            try {
                repository.retryAnalysis(videoId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Retry failed", e)
                _bannerState.value = HomeBannerState.Error(
                    e.localizedMessage ?: "Retry failed."
                )
            }
        }
    }

    fun deleteVideo(videoId: String) {
        viewModelScope.launch {
            try {
                repository.deleteVideo(videoId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Delete failed", e)
            }
        }
    }

    fun dismissBannerError() {
        _bannerState.value = HomeBannerState.Idle
    }

    companion object {
        fun provideFactory(repository: HookRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { HomeViewModel(repository) }
        }
    }
}
