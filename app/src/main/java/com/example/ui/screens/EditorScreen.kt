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
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import android.widget.Toast
import java.io.File
import com.example.ui.theme.TimelineBg
import com.example.viewmodel.EditorViewModel
import com.example.data.room.ClipEntity

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
    val audioPlayer = remember { ExoPlayer.Builder(context).build() }
    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
            audioPlayer.release()
        }
    }
    
    var isPlaying by remember { mutableStateOf(false) }
    
    LaunchedEffect(uiState.project?.audioVolume) {
        audioPlayer.volume = uiState.project?.audioVolume ?: 0.5f
    }

    LaunchedEffect(uiState.project?.audioUri, uiState.project?.audioStartTimeMs, uiState.project?.audioEndTimeMs) {
        val project = uiState.project
        val audioUri = project?.audioUri
        if (project != null && !audioUri.isNullOrEmpty()) {
            audioPlayer.clearMediaItems()
            val audioClippingConfiguration = MediaItem.ClippingConfiguration.Builder()
                .setStartPositionMs(project.audioStartTimeMs)
                .apply {
                    if (project.audioEndTimeMs > 0) {
                        setEndPositionMs(project.audioEndTimeMs)
                    }
                }
                .build()

            val audioMediaItem = MediaItem.Builder()
                .setUri(Uri.parse(audioUri))
                .setClippingConfiguration(audioClippingConfiguration)
                .build()
            
            audioPlayer.setMediaItem(audioMediaItem)
            audioPlayer.prepare()
        } else {
            audioPlayer.clearMediaItems()
        }
    }

    DisposableEffect(exoPlayer, audioPlayer, uiState.project?.audioUri) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlayingState: Boolean) {
                isPlaying = isPlayingState
                if (uiState.project?.audioUri != null) {
                    if (isPlayingState) {
                        audioPlayer.seekTo(exoPlayer.currentPosition)
                        audioPlayer.play()
                    } else {
                        audioPlayer.pause()
                    }
                }
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                if (uiState.project?.audioUri != null) {
                    audioPlayer.seekTo(newPosition.positionMs)
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
        }
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

    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val project = uiState.project
            if (project != null) {
                viewModel.updateProject(project.copy(audioUri = it.toString()))
            }
        }
    }

    var showAiDialog by remember { mutableStateOf(false) }
    var selectedClip by remember { mutableStateOf<ClipEntity?>(null) }
    var showTrimDialog by remember { mutableStateOf(false) }
    var showAdjustDialog by remember { mutableStateOf(false) }
    var showAudioDialog by remember { mutableStateOf(false) }
    var isExporting by remember { mutableStateOf(false) }

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
                            onClick = {
                                if (uiState.clips.isEmpty() || isExporting) return@Button
                                isExporting = true
                                
                                val editedMediaItems = uiState.clips.map { clip ->
                                    val clippingConfiguration = MediaItem.ClippingConfiguration.Builder()
                                        .setStartPositionMs(clip.startTimeMs)
                                        .apply {
                                            if (clip.endTimeMs > 0) {
                                                setEndPositionMs(clip.endTimeMs)
                                            }
                                        }
                                        .build()
                                        
                                    val mediaItem = MediaItem.Builder()
                                        .setUri(Uri.parse(clip.uri))
                                        .setClippingConfiguration(clippingConfiguration)
                                        .build()
                                        
                                    val videoEffects = mutableListOf<androidx.media3.common.Effect>()
                                    if (clip.brightness != 0f) videoEffects.add(androidx.media3.effect.Brightness(clip.brightness))
                                    if (clip.contrast != 0f) videoEffects.add(androidx.media3.effect.Contrast(clip.contrast))
                                    if (clip.saturation != 0f) videoEffects.add(androidx.media3.effect.HslAdjustment.Builder().adjustSaturation(clip.saturation).build())
                                        
                                    EditedMediaItem.Builder(mediaItem)
                                        .setEffects(androidx.media3.transformer.Effects(emptyList(), videoEffects))
                                        .build()
                                }
                                
                                val videoSequence = EditedMediaItemSequence(editedMediaItems)
                                
                                val project = uiState.project
                                val audioUriStr = project?.audioUri
                                
                                val composition = if (!audioUriStr.isNullOrEmpty()) {
                                    val audioClippingConfiguration = MediaItem.ClippingConfiguration.Builder()
                                        .setStartPositionMs(project.audioStartTimeMs)
                                        .apply {
                                            if (project.audioEndTimeMs > 0) {
                                                setEndPositionMs(project.audioEndTimeMs)
                                            }
                                        }
                                        .build()

                                    val audioMediaItem = MediaItem.Builder()
                                        .setUri(Uri.parse(audioUriStr))
                                        .setClippingConfiguration(audioClippingConfiguration)
                                        .build()

                                    val volumeAudioProcessor = com.example.util.VolumeAudioProcessor(project.audioVolume)
                                    
                                    val editedAudioItem = EditedMediaItem.Builder(audioMediaItem)
                                        .setEffects(androidx.media3.transformer.Effects(listOf(volumeAudioProcessor), emptyList()))
                                        .build()

                                    val audioSequence = EditedMediaItemSequence(listOf(editedAudioItem))
                                    Composition.Builder(listOf(videoSequence, audioSequence)).build()
                                } else {
                                    Composition.Builder(videoSequence).build()
                                }
                                
                                val outputFile = File(context.cacheDir, "exported_video_${System.currentTimeMillis()}.mp4")
                                
                                val transformer = Transformer.Builder(context)
                                    .addListener(object : Transformer.Listener {
                                        override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                                            isExporting = false
                                            Toast.makeText(context, "Exported to ${outputFile.absolutePath}", Toast.LENGTH_LONG).show()
                                        }
                                        override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                                            isExporting = false
                                            Toast.makeText(context, "Export failed: ${exportException.message}", Toast.LENGTH_LONG).show()
                                        }
                                    })
                                    .build()
                                    
                                transformer.start(composition, outputFile.absolutePath)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            shape = CircleShape,
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            if (isExporting) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                                Text("EXPORTING", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            } else {
                                Text("EXPORT", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
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
                
                // Play/Pause Button
                IconButton(
                    onClick = {
                        if (isPlaying) {
                            exoPlayer.pause()
                        } else {
                            exoPlayer.play()
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(64.dp)
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
                
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
                                        .background(
                                            if (selectedClip?.id == clip.id) Color(0xFF536DFE) else Color(0xFF8C9EFF), 
                                            RoundedCornerShape(4.dp)
                                        )
                                        .border(2.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                        .clickable { selectedClip = clip },
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
                        
                        // Audio Track UI
                        val hasAudio = uiState.project?.audioUri != null
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(36.dp)
                                .background(
                                    if (hasAudio) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                    RoundedCornerShape(6.dp)
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (hasAudio) MaterialTheme.colorScheme.secondary else Color.Transparent,
                                    shape = RoundedCornerShape(6.dp)
                                )
                                .clickable {
                                    showAudioDialog = true
                                }
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                                .testTag("audio_track_box"),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.GraphicEq,
                                        contentDescription = "Audio track",
                                        tint = if (hasAudio) MaterialTheme.colorScheme.onSecondaryContainer else Color.LightGray,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = if (hasAudio) {
                                            "BGM: ${uiState.project?.audioUri?.substringAfterLast("/") ?: "Added Music"}"
                                        } else {
                                            "No Background Music (Tap to Add)"
                                        },
                                        fontSize = 11.sp,
                                        color = if (hasAudio) MaterialTheme.colorScheme.onSecondaryContainer else Color.LightGray,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1
                                    )
                                }
                                if (hasAudio) {
                                    Text(
                                        text = "Trim: ${uiState.project?.audioStartTimeMs}ms - " +
                                                if ((uiState.project?.audioEndTimeMs ?: -1L) > 0) "${uiState.project?.audioEndTimeMs}ms" else "End",
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                                    )
                                }
                            }
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
                    ToolbarItem(icon = Icons.Default.Tune, label = "ADJUST", tint = MaterialTheme.colorScheme.primary, onClick = {
                        if (selectedClip != null) showAdjustDialog = true
                    })
                    ToolbarItem(icon = Icons.Default.GraphicEq, label = "AUDIO", onClick = {
                        showAudioDialog = true
                    })
                    ToolbarItem(icon = Icons.Default.TextFields, label = "TEXT")
                    ToolbarItem(icon = Icons.Default.AutoAwesome, label = "EFFECTS")
                    ToolbarItem(icon = Icons.Default.Layers, label = "LAYER")
                    ToolbarItem(icon = Icons.Default.ContentCut, label = "TRIM", onClick = {
                        if (selectedClip != null) showTrimDialog = true
                    })
                }
            }
        }
    }

    if (showAdjustDialog && selectedClip != null) {
        val clip = selectedClip!!
        var brightness by remember { mutableStateOf(clip.brightness) }
        var contrast by remember { mutableStateOf(clip.contrast) }
        var saturation by remember { mutableStateOf(clip.saturation) }
        
        LaunchedEffect(brightness, contrast, saturation) {
            val effects = mutableListOf<androidx.media3.common.Effect>()
            if (brightness != 0f) {
                effects.add(androidx.media3.effect.Brightness(brightness))
            }
            if (contrast != 0f) {
                effects.add(androidx.media3.effect.Contrast(contrast))
            }
            if (saturation != 0f) {
                effects.add(androidx.media3.effect.HslAdjustment.Builder().adjustSaturation(saturation).build())
            }
            exoPlayer.setVideoEffects(effects)
        }
        
        AlertDialog(
            onDismissRequest = { 
                showAdjustDialog = false 
                val effects = mutableListOf<androidx.media3.common.Effect>()
                if (clip.brightness != 0f) effects.add(androidx.media3.effect.Brightness(clip.brightness))
                if (clip.contrast != 0f) effects.add(androidx.media3.effect.Contrast(clip.contrast))
                if (clip.saturation != 0f) effects.add(androidx.media3.effect.HslAdjustment.Builder().adjustSaturation(clip.saturation).build())
                exoPlayer.setVideoEffects(effects)
            },
            title = { Text("Adjust", fontSize = 18.sp) },
            text = {
                Column {
                    Text("Brightness: ${"%.2f".format(brightness)}")
                    Slider(
                        value = brightness,
                        onValueChange = { brightness = it },
                        valueRange = -1f..1f
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("Contrast: ${"%.2f".format(contrast)}")
                    Slider(
                        value = contrast,
                        onValueChange = { contrast = it },
                        valueRange = -1f..1f
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("Saturation: ${"%.0f".format(saturation)}")
                    Slider(
                        value = saturation,
                        onValueChange = { saturation = it },
                        valueRange = -100f..100f
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { 
                    viewModel.updateClip(clip.copy(
                        brightness = brightness,
                        contrast = contrast,
                        saturation = saturation
                    ))
                    showAdjustDialog = false 
                }) { 
                    Text("Save") 
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    showAdjustDialog = false 
                    val effects = mutableListOf<androidx.media3.common.Effect>()
                    if (clip.brightness != 0f) effects.add(androidx.media3.effect.Brightness(clip.brightness))
                    if (clip.contrast != 0f) effects.add(androidx.media3.effect.Contrast(clip.contrast))
                    if (clip.saturation != 0f) effects.add(androidx.media3.effect.HslAdjustment.Builder().adjustSaturation(clip.saturation).build())
                    exoPlayer.setVideoEffects(effects)
                }) { 
                    Text("Cancel") 
                }
            }
        )
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

    if (showTrimDialog && selectedClip != null) {
        val clip = selectedClip!!
        var startMs by remember { mutableStateOf(clip.startTimeMs.toString()) }
        var endMs by remember { mutableStateOf(if (clip.endTimeMs > 0) clip.endTimeMs.toString() else "") }
        
        AlertDialog(
            onDismissRequest = { showTrimDialog = false },
            title = { Text("Trim Clip", fontSize = 18.sp) },
            text = {
                Column {
                    Text("Set the start and end positions in milliseconds.", fontSize = 14.sp)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = startMs,
                        onValueChange = { startMs = it },
                        label = { Text("Start Time (ms)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = endMs,
                        onValueChange = { endMs = it },
                        label = { Text("End Time (ms, empty for end)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { 
                    val newStart = startMs.toLongOrNull() ?: 0L
                    val newEnd = endMs.toLongOrNull() ?: -1L
                    viewModel.updateClip(clip.copy(
                        startTimeMs = newStart,
                        endTimeMs = newEnd
                    ))
                    showTrimDialog = false 
                }) { 
                    Text("Save") 
                }
            },
            dismissButton = {
                TextButton(onClick = { showTrimDialog = false }) { 
                    Text("Cancel") 
                }
            }
        )
    }

    if (showAudioDialog && uiState.project != null) {
        val project = uiState.project!!
        var audioVolume by remember { mutableStateOf(project.audioVolume) }
        var audioStartMs by remember { mutableStateOf(project.audioStartTimeMs.toString()) }
        var audioEndMs by remember { mutableStateOf(if (project.audioEndTimeMs > 0) project.audioEndTimeMs.toString() else "") }
        
        AlertDialog(
            onDismissRequest = { showAudioDialog = false },
            title = { Text("Background Music", fontSize = 18.sp) },
            text = {
                Column {
                    if (project.audioUri == null) {
                        Text("No background music added yet.", fontSize = 14.sp)
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = {
                                audioPickerLauncher.launch("audio/*")
                            },
                            modifier = Modifier.fillMaxWidth().testTag("choose_audio_file_button")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Add")
                            Spacer(Modifier.width(8.dp))
                            Text("Choose Audio File")
                        }
                    } else {
                        Text("Selected: ${project.audioUri.substringAfterLast("/")}", fontSize = 12.sp, maxLines = 1)
                        Spacer(Modifier.height(12.dp))
                        
                        Text("Volume: ${"%.0f%%".format(audioVolume * 100)}")
                        Slider(
                            value = audioVolume,
                            onValueChange = { 
                                audioVolume = it
                                audioPlayer.volume = it
                            },
                            valueRange = 0f..1f,
                            modifier = Modifier.testTag("audio_volume_slider")
                        )
                        Spacer(Modifier.height(8.dp))
                        
                        OutlinedTextField(
                            value = audioStartMs,
                            onValueChange = { audioStartMs = it },
                            label = { Text("Start position (ms)") },
                            modifier = Modifier.fillMaxWidth().testTag("audio_start_input")
                        )
                        Spacer(Modifier.height(8.dp))
                        
                        OutlinedTextField(
                            value = audioEndMs,
                            onValueChange = { audioEndMs = it },
                            label = { Text("End position (ms, empty for end)") },
                            modifier = Modifier.fillMaxWidth().testTag("audio_end_input")
                        )
                        Spacer(Modifier.height(16.dp))
                        
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            TextButton(
                                onClick = {
                                    audioPickerLauncher.launch("audio/*")
                                },
                                modifier = Modifier.testTag("change_audio_file_button")
                            ) {
                                Text("Change file")
                            }
                            
                            TextButton(
                                onClick = {
                                    viewModel.updateProject(project.copy(audioUri = null))
                                    showAudioDialog = false
                                },
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                modifier = Modifier.testTag("remove_audio_button")
                            ) {
                                Text("Remove")
                            }
                        }
                    }
                }
            },
            confirmButton = {
                if (project.audioUri != null) {
                    TextButton(onClick = {
                        val newStart = audioStartMs.toLongOrNull() ?: 0L
                        val newEnd = audioEndMs.toLongOrNull() ?: -1L
                        viewModel.updateProject(project.copy(
                            audioVolume = audioVolume,
                            audioStartTimeMs = newStart,
                            audioEndTimeMs = newEnd
                        ))
                        showAudioDialog = false
                    }, modifier = Modifier.testTag("save_audio_button")) {
                        Text("Save")
                    }
                } else {
                    TextButton(onClick = { showAudioDialog = false }) {
                        Text("Close")
                    }
                }
            },
            dismissButton = {
                if (project.audioUri != null) {
                    TextButton(onClick = { showAudioDialog = false }, modifier = Modifier.testTag("cancel_audio_button")) {
                        Text("Cancel")
                    }
                }
            }
        )
    }
}

@Composable
fun ToolbarItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector, 
    label: String, 
    tint: Color = Color.LightGray,
    onClick: () -> Unit = {}
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(24.dp))
        Text(label, fontSize = 10.sp, fontWeight = if (tint != Color.LightGray) FontWeight.Bold else FontWeight.Normal, color = tint)
    }
}

