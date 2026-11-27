package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.repository.HookRepository
import com.example.domain.model.CreatorCadenceSummary
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CadenceViewModel(
    private val repository: HookRepository
) : ViewModel() {

    val cadenceState: StateFlow<CreatorCadenceSummary?> = repository.observeCadenceSummary()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    fun setTargetUploadsPerWeek(target: Int) {
        viewModelScope.launch {
            repository.setTargetUploadsPerWeek(target)
        }
    }

    fun logExternalUpload() {
        viewModelScope.launch {
            repository.logExternalUpload("Published video without Hooked")
        }
    }

    companion object {
        fun provideFactory(repository: HookRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { CadenceViewModel(repository) }
        }
    }
}
