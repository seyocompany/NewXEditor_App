package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.effect.*
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.transformer.*
import androidx.media3.ui.PlayerView
import com.example.data.room.ClipEntity
import com.example.data.room.TextEntity
import com.example.ui.theme.TimelineBg
import com.example.viewmodel.EditorViewModel
import com.example.viewmodel.PanelType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

// ---------- HELPER: Build effects for a clip ----------
fun buildEffectsForClip(clip: ClipEntity): List<Effect> {
    val effects = mutableListOf<Effect>()

    // 1. Crop
    clip.cropRectString?.let { cropStr ->
        val parts = cropStr.split(",")
        if (parts.size == 4) {
            val l = parts[0].toFloatOrNull() ?: 0f
            val t = parts[1].toFloatOrNull() ?: 0f
            val r = parts[2].toFloatOrNull() ?: 1f
            val b = parts[3].toFloatOrNull() ?: 1f
            val ndcL = -1f + 2f * l
            val ndcR = -1f + 2f * r
            val ndcB = 1f - 2f * b
            val ndcT = 1f - 2f * t
            effects.add(Crop(ndcL, ndcR, ndcB, ndcT))
        }
    }

    // 2. Rotation & Flip
    if (clip.rotationDegrees != 0 || clip.flipHorizontal || clip.flipVertical) {
        val sx = if (clip.flipHorizontal) -1f else 1f
        val sy = if (clip.flipVertical) -1f else 1f
        effects.add(
            ScaleAndRotateTransformation.Builder()
                .setRotationDegrees(clip.rotationDegrees.toFloat())
                .setScale(sx, sy)
                .build()
        )
    }

    // 3. Color Adjustments (Brightness, Contrast, Saturation, Warmth, Fade)
    val matrix = floatArrayOf(
        1f, 0f, 0f, 0f, clip.brightness,
        0f, 1f, 0f, 0f, clip.brightness,
        0f, 0f, 1f, 0f, clip.brightness,
        0f, 0f, 0f, 1f, 0f
    )
    // Contrast
    if (clip.contrast != 0f) {
        val c = 1f + clip.contrast
        val t = (1f - c) / 2f
        for (i in 0..2) {
            matrix[i * 5] = c
            matrix[i * 5 + 4] = t
        }
    }
    // Saturation
    if (clip.saturation != 0f) {
        val sat = 1f + clip.saturation
        val lumR = 0.299f
        val lumG = 0.587f
        val lumB = 0.114f
        val r = (1 - sat) * lumR
        val g = (1 - sat) * lumG
        val b = (1 - sat) * lumB
        val m = floatArrayOf(
            r + sat, g, b, 0f, 0f,
            r, g + sat, b, 0f, 0f,
            r, g, b + sat, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )
        for (i in 0..19) matrix[i] = matrix[i] * 0.5f + m[i] * 0.5f
    }
    // Warmth (tint)
    if (clip.warmth != 0f) {
        val warm = clip.warmth
        matrix[0] += warm * 0.3f
        matrix[2] -= warm * 0.3f
    }
    // Fade
    if (clip.fade > 0f) {
        val f = clip.fade
        for (i in 0..2) {
            matrix[i * 5 + 4] = matrix[i * 5 + 4] * (1 - f) + 0.5f * f
        }
    }

    val isAdjustActive = clip.brightness != 0f || clip.contrast != 0f || clip.saturation != 0f ||
            clip.warmth != 0f || clip.fade > 0f
    if (isAdjustActive) {
        effects.add(RgbFilter(matrix))
    }

    // 4. Preset Filter (LUT via color matrix)
    clip.filterName?.let { filter ->
        getFilterMatrix(filter)?.let { mat ->
            effects.add(RgbFilter(mat))
        }
    }

    return effects
}

// ---------- FILTER PRESETS ----------
private fun getFilterMatrix(name: String): FloatArray? {
    return when (name) {
        "vintage" -> floatArrayOf(
            0.8f, 0.2f, 0.1f, 0f, 0.1f,
            0.1f, 0.7f, 0.2f, 0f, 0.05f,
            0.05f, 0.1f, 0.7f, 0f, 0.0f,
            0f, 0f, 0f, 1f, 0f
        )
        "bw" -> floatArrayOf(
            0.299f, 0.587f, 0.114f, 0f, 0f,
            0.299f, 0.587f, 0.114f, 0f, 0f,
            0.299f, 0.587f, 0.114f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )
        "vivid" -> floatArrayOf(
            1.2f, 0f, 0f, 0f, -0.1f,
            0f, 1.2f, 0f, 0f, -0.1f,
            0f, 0f, 1.2f, 0f, -0.1f,
            0f, 0f, 0f, 1f, 0f
        )
        "cool" -> floatArrayOf(
            1.0f, 0f, 0f, 0f, 0.1f,
            0f, 1.0f, 0f, 0f, 0.1f,
            0f, 0f, 1.2f, 0f, 0.0f,
            0f, 0f, 0f, 1f, 0f
        )
        "warm" -> floatArrayOf(
            1.2f, 0f, 0f, 0f, 0.0f,
            0f, 1.0f, 0f, 0f, 0.0f,
            0f, 0f, 0.8f, 0f, 0.0f,
            0f, 0f, 0f, 1f, 0f
        )
        "sepia" -> floatArrayOf(
            0.393f, 0.769f, 0.189f, 0f, 0f,
            0.349f, 0.686f, 0.168f, 0f, 0f,
            0.272f, 0.534f, 0.131f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )
        "glitch" -> floatArrayOf(
            1.0f, 0.5f, 0.0f, 0f, 0.1f,
            0.0f, 1.0f, 0.5f, 0f, 0.1f,
            0.5f, 0.0f, 1.0f, 0f, 0.1f,
            0f, 0f, 0f, 1f, 0f
        )
        else -> null
    }
}

// ---------- MAIN EDITOR SCREEN ----------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    viewModel: EditorViewModel,
    projectId: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    // ---------- LOAD DATA ----------
    LaunchedEffect(projectId) {
        viewModel.loadProject(projectId)
    }

    // ---------- EXOPLAYER ----------
    val exoPlayer = remember { ExoPlayer.Builder(context).build() }
    val audioPlayer = remember { ExoPlayer.Builder(context).build() }
    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
            audioPlayer.release()
        }
    }

    var isPlaying by remember { mutableStateOf(false) }
    var currentPositionMs by remember { mutableStateOf(0L) }
    var totalDurationMs by remember { mutableStateOf(0L) }

    // ---------- PLAYER SYNC ----------
    LaunchedEffect(uiState.project?.audioVolume) {
        audioPlayer.volume = uiState.project?.audioVolume ?: 0.5f
    }

    LaunchedEffect(uiState.project?.audioUri, uiState.project?.audioStartTimeMs, uiState.project?.audioEndTimeMs) {
        val p = uiState.project ?: return@LaunchedEffect
        p.audioUri?.let { uri ->
            audioPlayer.clearMediaItems()
            val clipping = MediaItem.ClippingConfiguration.Builder()
                .setStartPositionMs(p.audioStartTimeMs)
                .apply { if (p.audioEndTimeMs > 0) setEndPositionMs(p.audioEndTimeMs) }
                .build()
            audioPlayer.setMediaItem(
                MediaItem.Builder()
                    .setUri(Uri.parse(uri))
                    .setClippingConfiguration(clipping)
                    .build()
            )
            audioPlayer.prepare()
        }
    }

    LaunchedEffect(uiState.clips) {
        exoPlayer.clearMediaItems()
        uiState.clips.forEach { clip ->
            exoPlayer.addMediaItem(MediaItem.fromUri(Uri.parse(clip.uri)))
        }
        exoPlayer.prepare()
        updateCurrentClipEffects(exoPlayer, uiState.clips, exoPlayer.currentMediaItemIndex)
    }

    DisposableEffect(exoPlayer, audioPlayer, uiState.clips, uiState.project) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                isPlaying = isPlaying
                if (uiState.project?.audioUri != null) {
                    if (isPlaying) { audioPlayer.seekTo(exoPlayer.currentPosition); audioPlayer.play() }
                    else audioPlayer.pause()
                }
            }
            override fun onPositionDiscontinuity(old: Player.PositionInfo, new: Player.PositionInfo, reason: Int) {
                if (uiState.project?.audioUri != null) audioPlayer.seekTo(new.positionMs)
            }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val idx = exoPlayer.currentMediaItemIndex
                updateCurrentClipEffects(exoPlayer, uiState.clips, idx)
                val clip = uiState.clips.getOrNull(idx)
                if (clip != null) exoPlayer.setPlaybackParameters(androidx.media3.common.PlaybackParameters(clip.speed))
            }
        }
        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener) }
    }

    // Position updater
    LaunchedEffect(isPlaying, uiState.clips) {
        while (true) {
            try {
                val idx = exoPlayer.currentMediaItemIndex
                var pos = 0L
                for (i in 0 until idx) {
                    val c = uiState.clips.getOrNull(i) ?: continue
                    val dur = getClipDuration(c, context)
                    pos += if (c.endTimeMs > 0) (c.endTimeMs - c.startTimeMs).coerceAtLeast(0)
                    else (dur - c.startTimeMs).coerceAtLeast(0)
                }
                val active = uiState.clips.getOrNull(idx)
                val curPos = exoPlayer.currentPosition
                if (active != null) {
                    val rel = (curPos - active.startTimeMs).coerceAtLeast(0)
                    currentPositionMs = pos + rel
                } else {
                    currentPositionMs = pos + curPos
                }
                totalDurationMs = uiState.clips.sumOf { c ->
                    val d = getClipDuration(c, context)
                    if (c.endTimeMs > 0) (c.endTimeMs - c.startTimeMs).coerceAtLeast(0)
                    else (d - c.startTimeMs).coerceAtLeast(0)
                }
            } catch (e: Exception) { /* ignore */ }
            delay(if (isPlaying) 30L else 200L)
        }
    }

    // ---------- MEDIA PICKERS ----------
    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { viewModel.addClip(it.toString(), projectId) }
    }
    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            viewModel.updateProject(uiState.project?.copy(audioUri = it.toString()) ?: return@let)
        }
    }

    // ---------- UI ----------
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(uiState.project?.name ?: "Editor", maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
                },
                actions = {
                    IconButton(onClick = { viewModel.openPanel(PanelType.TEXT); if (uiState.texts.isEmpty()) viewModel.addText(projectId) }) {
                        Icon(Icons.Default.TextFields, "Text")
                    }
                    IconButton(onClick = { viewModel.openPanel(PanelType.MUSIC) }) {
                        Icon(Icons.Default.MusicNote, "Music")
                    }
                    IconButton(onClick = { viewModel.openPanel(PanelType.NONE) }) {
                        Icon(Icons.Default.MoreVert, "More")
                    }
                    Button(
                        onClick = { exportVideo(context, uiState, viewModel) },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = CircleShape,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Text("EXPORT", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black)
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            // ---------- VIDEO PREVIEW ----------
            Column(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    AndroidView(
                        factory = { PlayerView(context).apply { player = exoPlayer; useController = false } },
                        update = { if (it.player != exoPlayer) it.player = exoPlayer },
                        modifier = Modifier.fillMaxSize()
                    )
                    // Play/Pause
                    IconButton(
                        onClick = { if (isPlaying) exoPlayer.pause() else exoPlayer.play() },
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(64.dp)
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    ) {
                        Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = Color.White)
                    }
                    // Text overlays (preview)
                    uiState.texts.filter { it.isVisible }.forEach { textEntity ->
                        TextOverlay(textEntity, Modifier.fillMaxSize())
                    }
                }

                // ---------- TIMELINE (simplified) ----------
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .background(TimelineBg)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .horizontalScroll(rememberScrollState())
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        uiState.clips.forEach { clip ->
                            val dur = getClipDuration(clip, context)
                            val width = (dur.toFloat() / 1000 * 20).coerceAtLeast(60f).dp
                            Box(
                                modifier = Modifier
                                    .width(width)
                                    .fillMaxHeight(0.8f)
                                    .background(Color.DarkGray, RoundedCornerShape(4.dp))
                                    .clickable { viewModel.selectClip(clip.id) }
                                    .border(
                                        width = if (uiState.selectedClipId == clip.id) 2.dp else 0.dp,
                                        color = MaterialTheme.colorScheme.primary,
                                        shape = RoundedCornerShape(4.dp)
                                    )
                            ) {
                                Text("${clip.orderIndex+1}", modifier = Modifier.align(Alignment.Center), color = Color.White)
                            }
                        }
                        // Add clip button
                        Box(
                            modifier = Modifier
                                .width(56.dp)
                                .fillMaxHeight(0.8f)
                                .background(Color.Gray.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                                .clickable { videoPicker.launch("video/*") },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Add, null, tint = Color.White)
                        }
                    }
                }

                // ---------- BOTTOM TOOLBAR (CapCut style) ----------
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1C1B1F))
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    ToolButton(Icons.Default.Tune, "Adjust") { viewModel.openPanel(PanelType.ADJUST) }
                    ToolButton(Icons.Default.Crop, "Crop") { viewModel.openPanel(PanelType.CROP) }
                    ToolButton(Icons.Default.ContentCut, "Trim") { viewModel.openPanel(PanelType.TRIM) }
                    ToolButton(Icons.Default.Palette, "Filter") { viewModel.openPanel(PanelType.FILTER) }
                    ToolButton(Icons.Default.TextFields, "Text") {
                        viewModel.openPanel(PanelType.TEXT)
                        if (uiState.texts.isEmpty()) viewModel.addText(projectId)
                    }
                    ToolButton(Icons.Default.MusicNote, "Music") { viewModel.openPanel(PanelType.MUSIC) }
                }
            }

            // ---------- BOTTOM SHEETS (CAPCUT STYLE) ----------
            when (uiState.activePanel) {
                PanelType.ADJUST -> AdjustBottomSheet(viewModel, uiState, exoPlayer)
                PanelType.CROP -> CropBottomSheet(viewModel, uiState, exoPlayer)
                PanelType.TRIM -> TrimBottomSheet(viewModel, uiState, exoPlayer, context)
                PanelType.FILTER -> FilterBottomSheet(viewModel, uiState, exoPlayer)
                PanelType.TEXT -> TextBottomSheet(viewModel, uiState)
                PanelType.MUSIC -> MusicBottomSheet(viewModel, uiState, audioPicker)
                else -> Unit
            }
        }
    }
}

// ---------- TOOL BUTTON ----------
@Composable
fun ToolButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClick = onClick)) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(24.dp))
        Text(label, fontSize = 10.sp, color = Color.White)
    }
}

// ---------- TEXT OVERLAY (for preview) ----------
@Composable
fun TextOverlay(textEntity: TextEntity, modifier: Modifier = Modifier) {
    Box(modifier = modifier) {
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            val paint = Paint().apply {
                color = textEntity.color
                textSize = textEntity.fontSize * density
                textAlign = Paint.Align.CENTER
                isAntiAlias = true
                typeface = android.graphics.Typeface.DEFAULT
            }
            val x = size.width * textEntity.positionX
            val y = size.height * textEntity.positionY - paint.descent() / 2
            drawContext.canvas.nativeCanvas.drawText(textEntity.text, x, y, paint)
        }
    }
}

// ---------- HELPERS ----------
fun updateCurrentClipEffects(player: ExoPlayer, clips: List<ClipEntity>, index: Int) {
    val clip = clips.getOrNull(index)
    if (clip != null) {
        player.setVideoEffects(buildEffectsForClip(clip))
    }
}

fun getClipDuration(clip: ClipEntity, context: android.content.Context): Long {
    return try {
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(context, Uri.parse(clip.uri))
        val dur = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 5000L
        retriever.release()
        dur
    } catch (e: Exception) { 5000L }
}

// ============================================================
//  BOTTOM SHEETS
// ============================================================

// 1. ADJUST
@Composable
fun AdjustBottomSheet(viewModel: EditorViewModel, uiState: EditorUiState, player: ExoPlayer) {
    val clipId = uiState.selectedClipId ?: uiState.clips.firstOrNull()?.id ?: return
    var clip by remember { mutableStateOf(uiState.clips.find { it.id == clipId } ?: return) }

    val brightness = remember { mutableFloatStateOf(clip.brightness) }
    val contrast = remember { mutableFloatStateOf(clip.contrast) }
    val saturation = remember { mutableFloatStateOf(clip.saturation) }
    val warmth = remember { mutableFloatStateOf(clip.warmth) }
    val fade = remember { mutableFloatStateOf(clip.fade) }

    LaunchedEffect(brightness.floatValue, contrast.floatValue, saturation.floatValue, warmth.floatValue, fade.floatValue) {
        val updated = clip.copy(
            brightness = brightness.floatValue,
            contrast = contrast.floatValue,
            saturation = saturation.floatValue,
            warmth = warmth.floatValue,
            fade = fade.floatValue
        )
        player.setVideoEffects(buildEffectsForClip(updated))
    }

    ModalBottomSheet(onDismissRequest = { viewModel.closePanel() }) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Adjust", style = MaterialTheme.typography.titleLarge, color = Color.White)
            SliderWithLabel("Brightness", brightness.floatValue, -1f..1f) { brightness.floatValue = it }
            SliderWithLabel("Contrast", contrast.floatValue, -1f..1f) { contrast.floatValue = it }
            SliderWithLabel("Saturation", saturation.floatValue, -1f..1f) { saturation.floatValue = it }
            SliderWithLabel("Warmth", warmth.floatValue, -1f..1f) { warmth.floatValue = it }
            SliderWithLabel("Fade", fade.floatValue, 0f..1f) { fade.floatValue = it }
            Button(onClick = {
                viewModel.updateClip(clip.copy(
                    brightness = brightness.floatValue,
                    contrast = contrast.floatValue,
                    saturation = saturation.floatValue,
                    warmth = warmth.floatValue,
                    fade = fade.floatValue
                ))
                viewModel.closePanel()
            }) { Text("Apply") }
        }
    }
}

// 2. CROP
@Composable
fun CropBottomSheet(viewModel: EditorViewModel, uiState: EditorUiState, player: ExoPlayer) {
    val clipId = uiState.selectedClipId ?: uiState.clips.firstOrNull()?.id ?: return
    var clip by remember { mutableStateOf(uiState.clips.find { it.id == clipId } ?: return) }

    var left by remember { mutableFloatStateOf(clip.cropRectString?.split(",")?.get(0)?.toFloatOrNull() ?: 0f) }
    var top by remember { mutableFloatStateOf(clip.cropRectString?.split(",")?.get(1)?.toFloatOrNull() ?: 0f) }
    var right by remember { mutableFloatStateOf(clip.cropRectString?.split(",")?.get(2)?.toFloatOrNull() ?: 1f) }
    var bottom by remember { mutableFloatStateOf(clip.cropRectString?.split(",")?.get(3)?.toFloatOrNull() ?: 1f) }

    var rotation by remember { mutableIntStateOf(clip.rotationDegrees) }
    var flipH by remember { mutableStateOf(clip.flipHorizontal) }
    var flipV by remember { mutableStateOf(clip.flipVertical) }

    fun applyCrop() {
        val cropStr = if (left == 0f && top == 0f && right == 1f && bottom == 1f) null else "$left,$top,$right,$bottom"
        val updated = clip.copy(cropRectString = cropStr, rotationDegrees = rotation, flipHorizontal = flipH, flipVertical = flipV)
        viewModel.updateClip(updated)
        player.setVideoEffects(buildEffectsForClip(updated))
    }

    LaunchedEffect(left, top, right, bottom, rotation, flipH, flipV) {
        val testClip = clip.copy(cropRectString = "$left,$top,$right,$bottom", rotationDegrees = rotation, flipHorizontal = flipH, flipVertical = flipV)
        player.setVideoEffects(buildEffectsForClip(testClip))
    }

    ModalBottomSheet(onDismissRequest = { viewModel.closePanel() }) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Crop & Transform", style = MaterialTheme.typography.titleLarge, color = Color.White)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AspectChip("Free") { left = 0f; top = 0f; right = 1f; bottom = 1f }
                AspectChip("1:1") { val s = 0.5f; left = 0.25f; top = 0f; right = 0.75f; bottom = 1f }
                AspectChip("16:9") { left = 0f; top = 0.1f; right = 1f; bottom = 0.9f }
                AspectChip("9:16") { left = 0.1f; top = 0f; right = 0.9f; bottom = 1f }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { rotation = (rotation + 90) % 360 }) { Text("Rotate") }
                Button(onClick = { flipH = !flipH }) { Text("Flip H") }
                Button(onClick = { flipV = !flipV }) { Text("Flip V") }
            }

            Button(onClick = { applyCrop(); viewModel.closePanel() }) { Text("Apply") }
        }
    }
}

// 3. TRIM
@Composable
fun TrimBottomSheet(viewModel: EditorViewModel, uiState: EditorUiState, player: ExoPlayer, context: android.content.Context) {
    val clipId = uiState.selectedClipId ?: uiState.clips.firstOrNull()?.id ?: return
    var clip by remember { mutableStateOf(uiState.clips.find { it.id == clipId } ?: return) }
    val duration = getClipDuration(clip, context)

    var start by remember { mutableFloatStateOf(clip.startTimeMs.toFloat()) }
    var end by remember { mutableFloatStateOf(if (clip.endTimeMs > 0) clip.endTimeMs.toFloat() else duration.toFloat()) }

    LaunchedEffect(start, end) {
        val testClip = clip.copy(startTimeMs = start.toLong(), endTimeMs = if (end >= duration) -1L else end.toLong())
        player.seekTo(uiState.clips.indexOf(clip), start.toLong())
    }

    ModalBottomSheet(onDismissRequest = { viewModel.closePanel() }) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Trim", style = MaterialTheme.typography.titleLarge, color = Color.White)
            Text("Start: ${start.toInt()}ms  End: ${if (end >= duration) "End" else end.toInt() + "ms"}", color = Color.White)
            RangeSlider(
                value = start..end,
                onValueChange = { range -> start = range.start; end = range.endInclusive },
                valueRange = 0f..duration.toFloat(),
                steps = 20,
                modifier = Modifier.fillMaxWidth()
            )
            Button(onClick = {
                viewModel.updateClip(clip.copy(startTimeMs = start.toLong(), endTimeMs = if (end >= duration) -1L else end.toLong()))
                viewModel.closePanel()
            }) { Text("Apply Trim") }
        }
    }
}

// 4. FILTER
@Composable
fun FilterBottomSheet(viewModel: EditorViewModel, uiState: EditorUiState, player: ExoPlayer) {
    val clipId = uiState.selectedClipId ?: uiState.clips.firstOrNull()?.id ?: return
    var clip by remember { mutableStateOf(uiState.clips.find { it.id == clipId } ?: return) }

    val filters = listOf("None", "vintage", "bw", "vivid", "cool", "warm", "sepia", "glitch")

    ModalBottomSheet(onDismissRequest = { viewModel.closePanel() }) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Filters", style = MaterialTheme.typography.titleLarge, color = Color.White)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(filters) { name ->
                    FilterChip(name, selected = clip.filterName == name, onClick = {
                        val updated = clip.copy(filterName = if (name == "None") null else name)
                        viewModel.updateClip(updated)
                        player.setVideoEffects(buildEffectsForClip(updated))
                    })
                }
            }
            Button(onClick = { viewModel.closePanel() }) { Text("Done") }
        }
    }
}

// 5. TEXT
@Composable
fun TextBottomSheet(viewModel: EditorViewModel, uiState: EditorUiState) {
    var editingText by remember { mutableStateOf<TextEntity?>(null) }
    var tempText by remember { mutableStateOf("") }
    var tempColor by remember { mutableStateOf(Color.White) }
    var tempSize by remember { mutableStateOf(36f) }

    ModalBottomSheet(onDismissRequest = { viewModel.closePanel() }) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Text Overlay", style = MaterialTheme.typography.titleLarge, color = Color.White)

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(uiState.texts) { text ->
                    Chip(
                        onClick = { editingText = text; tempText = text.text; tempColor = Color(text.color); tempSize = text.fontSize },
                        label = { Text(text.text.take(8)) }
                    )
                }
                Button(onClick = { viewModel.addText(uiState.project?.id ?: return@Button) }) {
                    Icon(Icons.Default.Add, null)
                }
            }

            Spacer(Modifier.height(16.dp))
            if (editingText != null) {
                OutlinedTextField(value = tempText, onValueChange = { tempText = it }, label = { Text("Text") })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(Color.Red, Color.Green, Color.Blue, Color.Yellow, Color.White).forEach { c ->
                        Box(modifier = Modifier.size(30.dp).background(c, CircleShape).clickable { tempColor = c })
                    }
                }
                Slider(value = tempSize, onValueChange = { tempSize = it }, valueRange = 20f..80f)
                Button(onClick = {
                    viewModel.updateText(editingText!!.copy(text = tempText, color = tempColor.toArgb(), fontSize = tempSize))
                    editingText = null
                }) { Text("Update Text") }
                Button(onClick = { viewModel.deleteText(editingText!!.id); editingText = null }, colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) {
                    Text("Delete")
                }
            }
            Button(onClick = { viewModel.closePanel() }) { Text("Done") }
        }
    }
}

// 6. MUSIC
@Composable
fun MusicBottomSheet(viewModel: EditorViewModel, uiState: EditorUiState, audioPicker: androidx.activity.result.ActivityResultLauncher<String>) {
    val project = uiState.project ?: return
    var volume by remember { mutableFloatStateOf(project.audioVolume) }
    var start by remember { mutableStateOf(project.audioStartTimeMs.toString()) }
    var end by remember { mutableStateOf(if (project.audioEndTimeMs > 0) project.audioEndTimeMs.toString() else "") }

    ModalBottomSheet(onDismissRequest = { viewModel.closePanel() }) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Background Music", style = MaterialTheme.typography.titleLarge, color = Color.White)

            if (project.audioUri == null) {
                Button(onClick = { audioPicker.launch("audio/*") }) { Text("Choose Audio") }
            } else {
                Text("Current: ${project.audioUri.substringAfterLast("/")}", color = Color.White)
                SliderWithLabel("Volume", volume, 0f..1f) { volume = it }
                OutlinedTextField(value = start, onValueChange = { start = it }, label = { Text("Start (ms)") })
                OutlinedTextField(value = end, onValueChange = { end = it }, label = { Text("End (ms, empty = end)") })
                Row {
                    Button(onClick = {
                        val newStart = start.toLongOrNull() ?: 0L
                        val newEnd = end.toLongOrNull() ?: -1L
                        viewModel.updateProject(project.copy(audioVolume = volume, audioStartTimeMs = newStart, audioEndTimeMs = newEnd))
                        viewModel.closePanel()
                    }) { Text("Save") }
                    Button(onClick = { viewModel.updateProject(project.copy(audioUri = null)); viewModel.closePanel() }, colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) {
                        Text("Remove")
                    }
                }
            }
        }
    }
}

// ---------- UI HELPERS ----------
@Composable
fun SliderWithLabel(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onValueChange: (Float) -> Unit) {
    Column {
        Text("$label: ${"%.2f".format(value)}", color = Color.White)
        Slider(value = value, onValueChange = onValueChange, valueRange = range)
    }
}

@Composable
fun AspectChip(label: String, onClick: () -> Unit) {
    Button(onClick = onClick, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
        Text(label, fontSize = 12.sp)
    }
}

@Composable
fun FilterChip(name: String, selected: Boolean, onClick: () -> Unit) {
    Chip(onClick = onClick, colors = ChipDefaults.chipColors(containerColor = if (selected) MaterialTheme.colorScheme.primary else Color.DarkGray)) {
        Text(name, color = Color.White)
    }
}

// ---------- EXPORT ----------
fun exportVideo(context: android.content.Context, uiState: EditorUiState, viewModel: EditorViewModel) {
    if (uiState.clips.isEmpty()) {
        Toast.makeText(context, "No clips to export", Toast.LENGTH_SHORT).show()
        return
    }
    val project = uiState.project ?: return

    val editedMediaItems = uiState.clips.map { clip ->
        val clipping = MediaItem.ClippingConfiguration.Builder()
            .setStartPositionMs(clip.startTimeMs)
            .apply { if (clip.endTimeMs > 0) setEndPositionMs(clip.endTimeMs) }
            .build()
        val mediaItem = MediaItem.Builder()
            .setUri(Uri.parse(clip.uri))
            .setClippingConfiguration(clipping)
            .build()

        val effects = buildEffectsForClip(clip)
        val audioProcessors = if (clip.speed != 1f) {
            listOf(androidx.media3.common.audio.SpeedChangingAudioProcessor { clip.speed })
        } else emptyList()

        EditedMediaItem.Builder(mediaItem)
            .setEffects(Effects(audioProcessors, effects))
            .build()
    }

    val videoSequence = EditedMediaItemSequence(editedMediaItems)
    var composition = Composition.Builder(videoSequence).build()

    project.audioUri?.let { audioUri ->
        val clipping = MediaItem.ClippingConfiguration.Builder()
            .setStartPositionMs(project.audioStartTimeMs)
            .apply { if (project.audioEndTimeMs > 0) setEndPositionMs(project.audioEndTimeMs) }
            .build()
        val audioItem = MediaItem.Builder()
            .setUri(Uri.parse(audioUri))
            .setClippingConfiguration(clipping)
            .build()
        val volumeProcessor = com.example.util.VolumeAudioProcessor(project.audioVolume)
        val editedAudio = EditedMediaItem.Builder(audioItem)
            .setEffects(Effects(listOf(volumeProcessor), emptyList()))
            .build()
        val audioSequence = EditedMediaItemSequence(listOf(editedAudio))
        composition = Composition.Builder(listOf(videoSequence, audioSequence)).build()
    }

    val outputFile = File(context.getExternalFilesDir(android.os.Environment.DIRECTORY_MOVIES), "export_${System.currentTimeMillis()}.mp4")
    val transformer = Transformer.Builder(context)
        .addListener(object : Transformer.Listener {
            override fun onCompleted(composition: Composition, result: ExportResult) {
                saveToGallery(context, outputFile)
                outputFile.delete()
            }
            override fun onError(composition: Composition, result: ExportResult, exception: ExportException) {
                Toast.makeText(context, "Export failed: ${exception.message}", Toast.LENGTH_LONG).show()
            }
        })
        .build()
    transformer.start(composition, outputFile.absolutePath)
}

fun saveToGallery(context: android.content.Context, file: File) {
    val values = android.content.ContentValues().apply {
        put(android.provider.MediaStore.Video.Media.DISPLAY_NAME, file.name)
        put(android.provider.MediaStore.Video.Media.MIME_TYPE, "video/mp4")
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            put(android.provider.MediaStore.Video.Media.RELATIVE_PATH, "Movies/XEditor")
            put(android.provider.MediaStore.Video.Media.IS_PENDING, 1)
        }
    }
    val resolver = context.contentResolver
    val uri = resolver.insert(android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
    uri?.let {
        resolver.openOutputStream(it)?.use { os ->
            file.inputStream().use { it.copyTo(os) }
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            values.clear()
            values.put(android.provider.MediaStore.Video.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }
        Toast.makeText(context, "Saved to Movies/XEditor", Toast.LENGTH_LONG).show()
    }
}