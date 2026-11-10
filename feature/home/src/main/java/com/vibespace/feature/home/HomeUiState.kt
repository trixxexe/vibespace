package com.vibespace.feature.home

import com.vibespace.core.workspace.model.Project

data class HomeUiState(
    val isLoading: Boolean = false,
    val recentProjects: List<Project> = emptyList(),
    val errorMessage: String? = null
)
