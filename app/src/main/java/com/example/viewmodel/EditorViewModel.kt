package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ProjectRepository
import com.example.data.room.ClipEntity
import com.example.data.room.ProjectEntity
import com.example.data.room.TextEntity
import com.example.data.AiAssistant   // <-- added import
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EditorUiState(
    val project: ProjectEntity? = null,
    val clips: List<ClipEntity> = emptyList(),
    val texts: List<TextEntity> = emptyList(),
    val isLoading: Boolean = true,
    val selectedClipId: String? = null,
    val selectedTextId: String? = null,
    val activePanel: PanelType = PanelType.NONE,
    val aiSuggestion: String? = null,
    val isAiLoading: Boolean = false
)

enum class PanelType { NONE, ADJUST, CROP, TRIM, FILTER, TEXT, MUSIC }

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
        viewModelScope.launch {
            repository.getTextsForProject(projectId).collectLatest { texts ->
                _uiState.update { it.copy(texts = texts) }
            }
        }
    }

    // ---- Clip actions ----
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

    fun deleteClip(clip: ClipEntity) {
        viewModelScope.launch {
            repository.deleteClip(clip)
        }
    }

    fun selectClip(id: String) {
        _uiState.update { it.copy(selectedClipId = id) }
    }

    // ---- Text actions ----
    fun addText(projectId: String, initialText: String = "New Text") {
        viewModelScope.launch {
            val text = TextEntity(
                projectId = projectId,
                text = initialText,
                orderIndex = _uiState.value.texts.size
            )
            repository.insertText(text)   // now available
            _uiState.update { it.copy(selectedTextId = text.id) }
        }
    }

    fun updateText(text: TextEntity) {
        viewModelScope.launch {
            repository.updateText(text)
        }
    }

    fun deleteText(id: String) {
        viewModelScope.launch {
            repository.deleteTextById(id)
            if (_uiState.value.selectedTextId == id) {
                _uiState.update { it.copy(selectedTextId = null) }
            }
        }
    }

    fun selectText(id: String?) {
        _uiState.update { it.copy(selectedTextId = id) }
    }

    // ---- Project actions ----
    fun updateProject(project: ProjectEntity) {
        viewModelScope.launch {
            repository.updateProject(project)
        }
    }

    // ---- Panel control ----
    fun openPanel(panel: PanelType) {
        _uiState.update { it.copy(activePanel = if (it.activePanel == panel) PanelType.NONE else panel) }
    }

    fun closePanel() {
        _uiState.update { it.copy(activePanel = PanelType.NONE) }
    }

    // ---- AI ----
    fun getAiSuggestion(prompt: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isAiLoading = true) }
            val response = AiAssistant.getIdea(prompt)
            _uiState.update { it.copy(aiSuggestion = response, isAiLoading = false) }
        }
    }
}