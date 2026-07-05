package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.data.AuthManager
import kotlinx.coroutines.launch
import com.google.firebase.auth.FirebaseAuth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isSigningIn by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Account", style = MaterialTheme.typography.titleLarge)
            
            if (AuthManager.isFirebaseAvailable) {
                val user = FirebaseAuth.getInstance().currentUser
                if (user != null) {
                    Text("Signed in as: ${user.email}")
                    Button(onClick = {
                        AuthManager.signOut()
                        // Force recompose by state or just rely on Firebase auth listener in real app
                    }) {
                        Text("Sign Out")
                    }
                } else {
                    Button(
                        onClick = {
                            isSigningIn = true
                            coroutineScope.launch {
                                AuthManager.signInWithGoogle(context)
                                isSigningIn = false
                            }
                        },
                        enabled = !isSigningIn
                    ) {
                        Text(if (isSigningIn) "Signing in..." else "Sign in with Google")
                    }
                }
            } else {
                Text("Firebase not configured. Please add google-services.json to use Cloud Sync.", color = MaterialTheme.colorScheme.error)
            }
            
            Divider()
            
            Text("About XEditor", style = MaterialTheme.typography.titleLarge)
            Text("Version 1.0", style = MaterialTheme.typography.bodyMedium)
            Text("A smart offline video editor.", style = MaterialTheme.typography.bodyMedium)
        }
    }
}
