package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.room.Room
import com.example.data.ProjectRepository
import com.example.data.room.AppDatabase
import com.example.ui.screens.EditorScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.Routes
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SplashScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.EditorViewModel
import com.example.viewmodel.HomeViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        val database = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java, "xeditor-db"
        ).fallbackToDestructiveMigration().build()
        val repository = ProjectRepository(database.projectDao())
        
        val factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(HomeViewModel::class.java)) {
                    @Suppress("UNCHECKED_CAST")
                    return HomeViewModel(repository) as T
                }
                if (modelClass.isAssignableFrom(EditorViewModel::class.java)) {
                    @Suppress("UNCHECKED_CAST")
                    return EditorViewModel(repository) as T
                }
                throw IllegalArgumentException("Unknown ViewModel class")
            }
        }

        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    
                    NavHost(navController = navController, startDestination = Routes.SPLASH) {
                        composable(Routes.SPLASH) {
                            SplashScreen(
                                onNavigateToHome = {
                                    navController.navigate(Routes.HOME) {
                                        popUpTo(Routes.SPLASH) { inclusive = true }
                                    }
                                }
                            )
                        }
                        composable(Routes.HOME) {
                            val homeViewModel: HomeViewModel = viewModel(factory = factory)
                            HomeScreen(
                                viewModel = homeViewModel,
                                onNavigateToSettings = { navController.navigate(Routes.SETTINGS) },
                                onNavigateToEditor = { id -> navController.navigate(Routes.editorRoute(id)) }
                            )
                        }
                        composable(Routes.SETTINGS) {
                            SettingsScreen(onBack = { navController.popBackStack() })
                        }
                        composable(Routes.EDITOR) { backStackEntry ->
                            val projectId = backStackEntry.arguments?.getString("projectId") ?: return@composable
                            val editorViewModel: EditorViewModel = viewModel(factory = factory)
                            EditorScreen(
                                viewModel = editorViewModel,
                                projectId = projectId,
                                onBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
}
