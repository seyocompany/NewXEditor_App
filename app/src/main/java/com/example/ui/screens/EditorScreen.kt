package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.ui.theme.TimelineBg
import com.example.viewmodel.EditorViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    viewModel: EditorViewModel,
    projectId: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    
    LaunchedEffect(projectId) {
        viewModel.loadProject(projectId)
    }
    
    val exoPlayer = remember { ExoPlayer.Builder(context).build() }
    DisposableEffect(Unit) {
        onDispose { exoPlayer.release() }
    }
    
    LaunchedEffect(uiState.clips) {
        exoPlayer.clearMediaItems()
        uiState.clips.forEach { clip ->
            exoPlayer.addMediaItem(MediaItem.fromUri(Uri.parse(clip.uri)))
        }
        exoPlayer.prepare()
    }

    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.addClip(it.toString(), projectId) }
    }

    var showAiDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .background(MaterialTheme.colorScheme.background)
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = uiState.project?.name ?: "Editor",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 0.5.sp
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { showAiDialog = true }) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = "AI Assistant")
                        }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = { /* Export */ },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            shape = CircleShape,
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("EXPORT", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
                Divider(color = MaterialTheme.colorScheme.outline)
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).background(Color.Black)
        ) {
            // Video Preview Canvas
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color.Black)
            ) {
                AndroidView(
                    factory = {
                        PlayerView(context).apply {
                            player = exoPlayer
                            useController = false // Hide default controls for custom UI feel
                        }
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )
                // Video properties overlay
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(12.dp)
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("1080p | 30fps", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = Color.White)
                }
            }
            
            // Timeline Area
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(256.dp)
                    .background(TimelineBg)
            ) {
                Divider(color = MaterialTheme.colorScheme.outline)
                
                // Time Codes
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("00:00:00:00", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = Color.LightGray)
                    Text("00:00:15:22", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.primary)
                    Text("00:01:04:15", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = Color.LightGray)
                }

                // The Scrubber/Tracks
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 16.dp)
                ) {
                    // Playhead Line
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .fillMaxHeight()
                            .width(2.dp)
                            .background(MaterialTheme.colorScheme.primary)
                    ) {
                        Box(
                            modifier = Modifier
                                .offset(x = (-3).dp, y = (-4).dp)
                                .size(8.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape)
                        )
                    }

                    Column(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // Main Video Track (Clips)
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            items(uiState.clips) { clip ->
                                Box(
                                    modifier = Modifier
                                        .width(96.dp)
                                        .fillMaxHeight()
                                        .background(Color(0xFF8C9EFF), RoundedCornerShape(4.dp))
                                        .border(2.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(4.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("Clip ${clip.orderIndex + 1}", fontSize = 10.sp, color = Color.White)
                                }
                            }
                            item {
                                Box(
                                    modifier = Modifier
                                        .width(48.dp)
                                        .fillMaxHeight()
                                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))
                                        .clickable { pickerLauncher.launch("video/*") },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = "Add Media", tint = Color.White, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                        
                        Spacer(Modifier.height(8.dp))
                        
                        // Audio Track Placeholder
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(32.dp)
                                .background(MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
                        ) {
                             // visual audio waveform mock
                        }
                    }
                }

                // Contextual Toolbar
                Divider(color = MaterialTheme.colorScheme.outline)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.background)
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ToolbarItem(icon = Icons.Default.Tune, label = "EDIT", tint = MaterialTheme.colorScheme.primary)
                    ToolbarItem(icon = Icons.Default.GraphicEq, label = "AUDIO")
                    ToolbarItem(icon = Icons.Default.TextFields, label = "TEXT")
                    ToolbarItem(icon = Icons.Default.AutoAwesome, label = "EFFECTS")
                    ToolbarItem(icon = Icons.Default.Layers, label = "LAYER")
                    ToolbarItem(icon = Icons.Default.ContentCut, label = "SPLIT")
                }
            }
        }
    }

    if (showAiDialog) {
        AlertDialog(
            onDismissRequest = { showAiDialog = false },
            title = { Text("Gemini AI Editor Assistant", fontSize = 18.sp) },
            text = {
                Column {
                    Text("Need ideas on how to edit this video or what music to add?", fontSize = 14.sp)
                    Spacer(Modifier.height(12.dp))
                    var prompt by remember { mutableStateOf("") }
                    OutlinedTextField(
                        value = prompt,
                        onValueChange = { prompt = it },
                        label = { Text("Ask Gemini...") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))
                    if (uiState.isAiLoading) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                    } else if (uiState.aiSuggestion != null) {
                        Text(uiState.aiSuggestion!!, fontSize = 14.sp)
                    }
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { viewModel.getAiSuggestion(prompt) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Get Suggestion")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAiDialog = false }) { Text("Close") }
            }
        )
    }
}

@Composable
fun ToolbarItem(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, tint: Color = Color.LightGray) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.clickable { }
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(24.dp))
        Text(label, fontSize = 10.sp, fontWeight = if (tint != Color.LightGray) FontWeight.Bold else FontWeight.Normal, color = tint)
    }
}

