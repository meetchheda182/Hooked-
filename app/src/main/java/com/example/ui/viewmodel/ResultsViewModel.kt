package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.repository.HookRepository
import com.example.domain.model.VideoAnalysis
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val TAG = "ResultsViewModel"

sealed interface ClipExportState {
    data object Idle : ClipExportState
    data class Exporting(val progressPercent: Int) : ClipExportState
    data class Exported(val clipPath: String) : ClipExportState
    data class Error(val message: String) : ClipExportState
}

sealed interface ThumbnailGenState {
    data object Idle : ThumbnailGenState
    data object Generating : ThumbnailGenState
    data class Error(val message: String) : ThumbnailGenState
}

data class ResultsUiState(
    val analysis: VideoAnalysis? = null,
    val activeHighlightId: String? = null,
    val clipExportStates: Map<String, ClipExportState> = emptyMap(),
    val thumbnailGenStates: Map<String, ThumbnailGenState> = emptyMap()
)

class ResultsViewModel(
    private val videoId: String,
    private val repository: HookRepository
) : ViewModel() {

    private val _activeHighlightId = MutableStateFlow<String?>(null)
    private val _clipExportStates = MutableStateFlow<Map<String, ClipExportState>>(emptyMap())
    private val _thumbnailGenStates = MutableStateFlow<Map<String, ThumbnailGenState>>(emptyMap())
    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    val uiState: StateFlow<ResultsUiState> = combine(
        repository.observeAnalysis(videoId),
        _activeHighlightId,
        _clipExportStates,
        _thumbnailGenStates
    ) { analysis, activeId, exportMap, thumbMap ->
        ResultsUiState(
            analysis = analysis,
            activeHighlightId = activeId,
            clipExportStates = exportMap,
            thumbnailGenStates = thumbMap
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ResultsUiState()
    )

    fun selectHighlight(highlightId: String) {
        _activeHighlightId.value = highlightId
    }

    fun exportHighlightClip(highlightId: String) {
        val currentState = _clipExportStates.value[highlightId]
        if (currentState is ClipExportState.Exporting) return

        viewModelScope.launch {
            _clipExportStates.update { it + (highlightId to ClipExportState.Exporting(0)) }
            try {
                val clipPath = repository.exportHighlightClip(
                    videoId = videoId,
                    highlightId = highlightId,
                    onProgress = { pct ->
                        _clipExportStates.update {
                            it + (highlightId to ClipExportState.Exporting(pct))
                        }
                    }
                )
                _clipExportStates.update { it + (highlightId to ClipExportState.Exported(clipPath)) }
                _toastMessage.value = "Highlight clip ready to share"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Clip export failed", e)
                _clipExportStates.update {
                    it + (highlightId to ClipExportState.Error(e.localizedMessage ?: "Export failed."))
                }
            }
        }
    }

    fun shareExportedClip(clipPath: String) {
        repository.shareClip(clipPath)
    }

    fun generateThumbnail(ideaId: String) {
        val currentState = _thumbnailGenStates.value[ideaId]
        if (currentState is ThumbnailGenState.Generating) return

        viewModelScope.launch {
            _thumbnailGenStates.update { it + (ideaId to ThumbnailGenState.Generating) }
            try {
                repository.generateThumbnailForIdea(videoId = videoId, ideaId = ideaId)
                _thumbnailGenStates.update { it + (ideaId to ThumbnailGenState.Idle) }
                _toastMessage.value = "Thumbnail generated"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Thumbnail generation failed", e)
                _thumbnailGenStates.update {
                    it + (ideaId to ThumbnailGenState.Error(
                        e.localizedMessage ?: "Failed to generate thumbnail."
                    ))
                }
            }
        }
    }

    fun shareThumbnail(imagePath: String) {
        repository.shareThumbnail(imagePath)
    }

    fun onTitleCopied(titleText: String) {
        viewModelScope.launch {
            repository.recordTitleCopied(videoId, titleText)
            _toastMessage.value = "Title copied to clipboard"
        }
    }

    fun retryAnalysis() {
        viewModelScope.launch {
            try {
                repository.retryAnalysis(videoId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Retry analysis failed", e)
                _toastMessage.value = e.localizedMessage ?: "Retry failed"
            }
        }
    }

    fun consumeToast() {
        _toastMessage.value = null
    }

    companion object {
        fun provideFactory(
            videoId: String,
            repository: HookRepository
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { ResultsViewModel(videoId, repository) }
        }
    }
}
