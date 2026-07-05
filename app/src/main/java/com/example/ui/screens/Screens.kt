package com.example.ui.screens

object Routes {
    const val SPLASH = "splash"
    const val HOME = "home"
    const val SETTINGS = "settings"
    const val EDITOR = "editor/{projectId}"
    const val EXPORT = "export/{projectId}"
    
    fun editorRoute(id: String) = "editor/$id"
    fun exportRoute(id: String) = "export/$id"
}
