package com.example.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ProjectRepository
import com.example.data.room.ClipEntity
import com.example.data.room.ProjectEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.example.data.AiAssistant

data class EditorUiState(
    val project: ProjectEntity? = null,
    val clips: List<ClipEntity> = emptyList(),
    val isLoading: Boolean = true,
    val aiSuggestion: String? = null,
    val isAiLoading: Boolean = false
)

class EditorViewModel(
    private val repository: ProjectRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    fun loadProject(projectId: String) {
        viewModelScope.launch {
            repository.getProject(projectId).collectLatest { proj ->
                _uiState.update { it.copy(project = proj) }
            }
        }
        viewModelScope.launch {
            repository.getClipsForProject(projectId).collectLatest { cls ->
                _uiState.update { it.copy(clips = cls, isLoading = false) }
            }
        }
    }

    fun addClip(uri: String, projectId: String) {
        viewModelScope.launch {
            repository.addClip(
                ClipEntity(
                    projectId = projectId,
                    uri = uri,
                    orderIndex = _uiState.value.clips.size
                )
            )
        }
    }
    
    fun updateClip(clip: ClipEntity) {
        viewModelScope.launch {
            repository.updateClip(clip)
        }
    }

    fun updateProject(project: ProjectEntity) {
        viewModelScope.launch {
            repository.updateProject(project)
        }
    }
    
    fun deleteClip(clip: ClipEntity) {
        viewModelScope.launch {
            repository.deleteClip(clip)
        }
    }

    fun getAiSuggestion(prompt: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isAiLoading = true) }
            val response = AiAssistant.getIdea(prompt)
            _uiState.update { it.copy(aiSuggestion = response, isAiLoading = false) }
        }
    }
}
