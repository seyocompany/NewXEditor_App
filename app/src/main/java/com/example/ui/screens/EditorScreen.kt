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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Tonality
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.Crop
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
import com.example.data.room.ProjectEntity
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun getEffectsForClip(clip: ClipEntity): List<androidx.media3.common.Effect> {
    val effects = mutableListOf<androidx.media3.common.Effect>()
    
    // 1. Crop
    val cropStr = clip.cropRectString
    if (!cropStr.isNullOrEmpty()) {
        val parts = cropStr.split(",")
        if (parts.size == 4) {
            val left = parts[0].toFloatOrNull() ?: 0f
            val top = parts[1].toFloatOrNull() ?: 0f
            val right = parts[2].toFloatOrNull() ?: 1f
            val bottom = parts[3].toFloatOrNull() ?: 1f
            
            val ndcLeft = -1f + 2f * left
            val ndcRight = -1f + 2f * right
            val ndcBottom = 1f - 2f * bottom
            val ndcTop = 1f - 2f * top
            effects.add(androidx.media3.effect.Crop(ndcLeft, ndcRight, ndcBottom, ndcTop))
        }
    }
    
    // 2. Rotation & Flip
    if (clip.rotationDegrees != 0 || clip.flipHorizontal || clip.flipVertical) {
        val scaleX = if (clip.flipHorizontal) -1f else 1f
        val scaleY = if (clip.flipVertical) -1f else 1f
        effects.add(
            androidx.media3.effect.ScaleAndRotateTransformation.Builder()
                .setRotationDegrees(clip.rotationDegrees.toFloat())
                .setScale(scaleX, scaleY)
                .build()
        )
    }
    
    // 3. Adjustments
    if (clip.brightness != 0f) {
        effects.add(androidx.media3.effect.Brightness(clip.brightness))
    }
    if (clip.contrast != 0f) {
        effects.add(androidx.media3.effect.Contrast(clip.contrast))
    }
    if (clip.saturation != 0f) {
        effects.add(androidx.media3.effect.HslAdjustment.Builder().adjustSaturation(clip.saturation).build())
    }
    
    return effects
}

private enum class AdjustType(
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val range: ClosedFloatingPointRange<Float>
) {
    LIGHTNESS("Lightness", Icons.Default.WbSunny, -1f..1f),
    CONTRAST("Contrast", Icons.Default.Tonality, -1f..1f),
    SATURATION("Saturation", Icons.Default.WaterDrop, -100f..100f)
}

@Composable
fun AdjustPanel(
    clip: ClipEntity,
    exoPlayer: ExoPlayer,
    getEffectsForClip: (ClipEntity) -> List<androidx.media3.common.Effect>,
    onDone: (ClipEntity) -> Unit,
    onCancel: () -> Unit
) {
    var brightness by remember { mutableStateOf(clip.brightness) }
    var contrast by remember { mutableStateOf(clip.contrast) }
    var saturation by remember { mutableStateOf(clip.saturation) }
    var selected by remember { mutableStateOf(AdjustType.LIGHTNESS) }

    LaunchedEffect(brightness, contrast, saturation) {
        val effects = getEffectsForClip(clip).toMutableList()
        effects.removeAll {
            it is androidx.media3.effect.Brightness ||
            it is androidx.media3.effect.Contrast ||
            it is androidx.media3.effect.HslAdjustment
        }
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

    val currentValue = when (selected) {
        AdjustType.LIGHTNESS -> brightness
        AdjustType.CONTRAST -> contrast
        AdjustType.SATURATION -> saturation
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(vertical = 8.dp)
    ) {
        // Top row: cancel (X) - category label - done (check)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onCancel) {
                Icon(Icons.Default.Close, contentDescription = "Cancel")
            }
            Text(
                text = "${selected.label}: ${"%.2f".format(currentValue)}",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onBackground
            )
            IconButton(onClick = {
                onDone(clip.copy(brightness = brightness, contrast = contrast, saturation = saturation))
            }) {
                Icon(Icons.Default.Check, contentDescription = "Done", tint = MaterialTheme.colorScheme.primary)
            }
        }

        // One shared slider - controls whichever category is currently selected below
        Slider(
            value = currentValue,
            onValueChange = { newValue ->
                when (selected) {
                    AdjustType.LIGHTNESS -> brightness = newValue
                    AdjustType.CONTRAST -> contrast = newValue
                    AdjustType.SATURATION -> saturation = newValue
                }
            },
            valueRange = selected.range,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        )

        // Icon row - tap a category to make the slider above control it
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(28.dp),
            contentPadding = PaddingValues(horizontal = 20.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
        ) {
            items(AdjustType.values()) { type ->
                val isSelected = type == selected
                val tintColor = if (isSelected) MaterialTheme.colorScheme.primary
                                 else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clickable { selected = type }
                        .padding(vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = type.icon,
                        contentDescription = type.label,
                        tint = tintColor,
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(text = type.label, fontSize = 10.sp, color = tintColor)
                    Spacer(Modifier.height(2.dp))
                    Box(
                        modifier = Modifier
                            .size(4.dp)
                            .background(
                                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                shape = CircleShape
                            )
                    )
                }
            }
        }
    }
}

@Composable
fun CropOverlay(
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    onCropChanged: (Float, Float, Float, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val currentLeft = rememberUpdatedState(left)
    val currentTop = rememberUpdatedState(top)
    val currentRight = rememberUpdatedState(right)
    val currentBottom = rememberUpdatedState(bottom)

    BoxWithConstraints(modifier = modifier) {
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()
        
        if (widthPx > 0 && heightPx > 0) {
            val leftPx = left * widthPx
            val topPx = top * heightPx
            val rightPx = right * widthPx
            val bottomPx = bottom * heightPx
            
            androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                val outerWidth = size.width
                val outerHeight = size.height
                
                // Top overlay
                drawRect(
                    color = Color.Black.copy(alpha = 0.6f),
                    topLeft = androidx.compose.ui.geometry.Offset(0f, 0f),
                    size = androidx.compose.ui.geometry.Size(outerWidth, topPx)
                )
                // Bottom overlay
                drawRect(
                    color = Color.Black.copy(alpha = 0.6f),
                    topLeft = androidx.compose.ui.geometry.Offset(0f, bottomPx),
                    size = androidx.compose.ui.geometry.Size(outerWidth, outerHeight - bottomPx)
                )
                // Left overlay
                drawRect(
                    color = Color.Black.copy(alpha = 0.6f),
                    topLeft = androidx.compose.ui.geometry.Offset(0f, topPx),
                    size = androidx.compose.ui.geometry.Size(leftPx, bottomPx - topPx)
                )
                // Right overlay
                drawRect(
                    color = Color.Black.copy(alpha = 0.6f),
                    topLeft = androidx.compose.ui.geometry.Offset(rightPx, topPx),
                    size = androidx.compose.ui.geometry.Size(outerWidth - rightPx, bottomPx - topPx)
                )
                
                // White outline
                drawRect(
                    color = Color.White,
                    topLeft = androidx.compose.ui.geometry.Offset(leftPx, topPx),
                    size = androidx.compose.ui.geometry.Size(rightPx - leftPx, bottomPx - topPx),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
                )
            }
            
            val handleSize = 32.dp
            val handleDensity = LocalDensity.current.density
            
            // Top-Left Handle
            Box(
                modifier = Modifier
                    .offset(
                        x = (leftPx / handleDensity).dp - handleSize / 2,
                        y = (topPx / handleDensity).dp - handleSize / 2
                    )
                    .size(handleSize)
                    .background(Color.White, CircleShape)
                    .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            val curLeftPx = currentLeft.value * widthPx
                            val curTopPx = currentTop.value * heightPx
                            val newLeft = ((curLeftPx + dragAmount.x) / widthPx).coerceIn(0f, currentRight.value - 0.1f)
                            val newTop = ((curTopPx + dragAmount.y) / heightPx).coerceIn(0f, currentBottom.value - 0.1f)
                            onCropChanged(newLeft, newTop, currentRight.value, currentBottom.value)
                        }
                    }
            )
            
            // Top-Right Handle
            Box(
                modifier = Modifier
                    .offset(
                        x = (rightPx / handleDensity).dp - handleSize / 2,
                        y = (topPx / handleDensity).dp - handleSize / 2
                    )
                    .size(handleSize)
                    .background(Color.White, CircleShape)
                    .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            val curRightPx = currentRight.value * widthPx
                            val curTopPx = currentTop.value * heightPx
                            val newRight = ((curRightPx + dragAmount.x) / widthPx).coerceIn(currentLeft.value + 0.1f, 1f)
                            val newTop = ((curTopPx + dragAmount.y) / heightPx).coerceIn(0f, currentBottom.value - 0.1f)
                            onCropChanged(currentLeft.value, newTop, newRight, currentBottom.value)
                        }
                    }
            )
            
            // Bottom-Left Handle
            Box(
                modifier = Modifier
                    .offset(
                        x = (leftPx / handleDensity).dp - handleSize / 2,
                        y = (bottomPx / handleDensity).dp - handleSize / 2
                    )
                    .size(handleSize)
                    .background(Color.White, CircleShape)
                    .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            val curLeftPx = currentLeft.value * widthPx
                            val curBottomPx = currentBottom.value * heightPx
                            val newLeft = ((curLeftPx + dragAmount.x) / widthPx).coerceIn(0f, currentRight.value - 0.1f)
                            val newBottom = ((curBottomPx + dragAmount.y) / heightPx).coerceIn(currentTop.value + 0.1f, 1f)
                            onCropChanged(newLeft, currentTop.value, currentRight.value, newBottom)
                        }
                    }
            )
            
            // Bottom-Right Handle
            Box(
                modifier = Modifier
                    .offset(
                        x = (rightPx / handleDensity).dp - handleSize / 2,
                        y = (bottomPx / handleDensity).dp - handleSize / 2
                    )
                    .size(handleSize)
                    .background(Color.White, CircleShape)
                    .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            val curRightPx = currentRight.value * widthPx
                            val curBottomPx = currentBottom.value * heightPx
                            val newRight = ((curRightPx + dragAmount.x) / widthPx).coerceIn(currentLeft.value + 0.1f, 1f)
                            val newBottom = ((curBottomPx + dragAmount.y) / heightPx).coerceIn(currentTop.value + 0.1f, 1f)
                            onCropChanged(currentLeft.value, currentTop.value, newRight, newBottom)
                        }
                    }
            )
        }
    }
}

@Composable
fun CropOverlayLocked(
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    onPanChanged: (Float, Float, Float, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val currentLeft = rememberUpdatedState(left)
    val currentTop = rememberUpdatedState(top)
    val currentRight = rememberUpdatedState(right)
    val currentBottom = rememberUpdatedState(bottom)

    BoxWithConstraints(modifier = modifier) {
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()

        if (widthPx > 0 && heightPx > 0) {
            val leftPx = left * widthPx
            val topPx = top * heightPx
            val rightPx = right * widthPx
            val bottomPx = bottom * heightPx

            androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                val outerWidth = size.width
                val outerHeight = size.height
                drawRect(color = Color.Black.copy(alpha = 0.6f), topLeft = androidx.compose.ui.geometry.Offset(0f, 0f), size = androidx.compose.ui.geometry.Size(outerWidth, topPx))
                drawRect(color = Color.Black.copy(alpha = 0.6f), topLeft = androidx.compose.ui.geometry.Offset(0f, bottomPx), size = androidx.compose.ui.geometry.Size(outerWidth, outerHeight - bottomPx))
                drawRect(color = Color.Black.copy(alpha = 0.6f), topLeft = androidx.compose.ui.geometry.Offset(0f, topPx), size = androidx.compose.ui.geometry.Size(leftPx, bottomPx - topPx))
                drawRect(color = Color.Black.copy(alpha = 0.6f), topLeft = androidx.compose.ui.geometry.Offset(rightPx, topPx), size = androidx.compose.ui.geometry.Size(outerWidth - rightPx, bottomPx - topPx))
                drawRect(color = Color.White, topLeft = androidx.compose.ui.geometry.Offset(leftPx, topPx), size = androidx.compose.ui.geometry.Size(rightPx - leftPx, bottomPx - topPx), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()))
            }

            // The whole frame is draggable - moves as one fixed-shape block, doesn't resize
            Box(
                modifier = Modifier
                    .offset(x = (leftPx / LocalDensity.current.density).dp, y = (topPx / LocalDensity.current.density).dp)
                    .size(
                        width = ((rightPx - leftPx) / LocalDensity.current.density).dp,
                        height = ((bottomPx - topPx) / LocalDensity.current.density).dp
                    )
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            val rectWidthFrac = currentRight.value - currentLeft.value
                            val rectHeightFrac = currentBottom.value - currentTop.value
                            val deltaFracX = dragAmount.x / widthPx
                            val deltaFracY = dragAmount.y / heightPx
                            val newLeft = (currentLeft.value + deltaFracX).coerceIn(0f, (1f - rectWidthFrac).coerceAtLeast(0f))
                            val newTop = (currentTop.value + deltaFracY).coerceIn(0f, (1f - rectHeightFrac).coerceAtLeast(0f))
                            onPanChanged(newLeft, newTop, newLeft + rectWidthFrac, newTop + rectHeightFrac)
                        }
                    }
            )
        }
    }
}

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

    DisposableEffect(exoPlayer, audioPlayer, uiState.project?.audioUri, uiState.clips) {
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

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val index = exoPlayer.currentMediaItemIndex
                val clip = uiState.clips.getOrNull(index)
                if (clip != null) {
                    exoPlayer.setPlaybackParameters(androidx.media3.common.PlaybackParameters(clip.speed))
                    exoPlayer.setVideoEffects(getEffectsForClip(clip))
                }
            }
        }
        exoPlayer.addListener(listener)
        
        // Initial clip configuration
        val initialIndex = exoPlayer.currentMediaItemIndex
        val initialClip = uiState.clips.getOrNull(initialIndex)
        if (initialClip != null) {
            exoPlayer.setPlaybackParameters(androidx.media3.common.PlaybackParameters(initialClip.speed))
            exoPlayer.setVideoEffects(getEffectsForClip(initialClip))
        }
        
        onDispose {
            exoPlayer.removeListener(listener)
        }
    }

    // Only rebuild the player's media item list when the actual set/order of clips changes -
    // NOT every time a clip's brightness/crop/trim/etc. is edited. Keying on uiState.clips
    // directly caused a full clearMediaItems()+prepare() on every single edit, which is what
    // was freezing playback until you left and re-entered the project.
    val clipIdentitySignature = uiState.clips.joinToString("|") { "${it.id}:${it.uri}" }
    LaunchedEffect(clipIdentitySignature) {
        exoPlayer.clearMediaItems()
        uiState.clips.forEach { clip ->
            exoPlayer.addMediaItem(MediaItem.fromUri(Uri.parse(clip.uri)))
        }
        exoPlayer.prepare()
        val index = exoPlayer.currentMediaItemIndex
        val clip = uiState.clips.getOrNull(index)
        if (clip != null) {
            exoPlayer.setPlaybackParameters(androidx.media3.common.PlaybackParameters(clip.speed))
            exoPlayer.setVideoEffects(getEffectsForClip(clip))
        }
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
    var showTransformDialog by remember { mutableStateOf(false) }
    var playerTrigger by remember { mutableStateOf(0) }
    var showAudioDialog by remember { mutableStateOf(false) }
    var isExporting by remember { mutableStateOf(false) }
    val exportScope = rememberCoroutineScope()

    // Hoisted Transform state - shared between the crop overlay drawn on the main
    // preview and the rotate/flip/speed controls drawn in the bottom panel.
    var transformLeft by remember { mutableStateOf(0f) }
    var transformTop by remember { mutableStateOf(0f) }
    var transformRight by remember { mutableStateOf(1f) }
    var transformBottom by remember { mutableStateOf(1f) }
    var transformRotation by remember { mutableStateOf(0) }
    var transformFlipH by remember { mutableStateOf(false) }
    var transformFlipV by remember { mutableStateOf(false) }
    var transformSpeed by remember { mutableStateOf(1f) }
    // null = free-form 4-corner crop. Non-null = a locked aspect ratio (label to show as selected);
    // in that mode the crop rect keeps a fixed shape and the user pans it instead of resizing corners.
    var lockedAspectLabel by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(showTransformDialog, selectedClip) {
        val clip = selectedClip
        if (showTransformDialog && clip != null) {
            lockedAspectLabel = null
            val cropStr = clip.cropRectString
            if (!cropStr.isNullOrEmpty()) {
                val parts = cropStr.split(",")
                if (parts.size == 4) {
                    transformLeft = parts[0].toFloatOrNull() ?: 0f
                    transformTop = parts[1].toFloatOrNull() ?: 0f
                    transformRight = parts[2].toFloatOrNull() ?: 1f
                    transformBottom = parts[3].toFloatOrNull() ?: 1f
                }
            } else {
                transformLeft = 0f
                transformTop = 0f
                transformRight = 1f
                transformBottom = 1f
            }
            transformRotation = clip.rotationDegrees
            transformFlipH = clip.flipHorizontal
            transformFlipV = clip.flipVertical
            transformSpeed = clip.speed
        }
    }

    LaunchedEffect(showTransformDialog, transformLeft, transformTop, transformRight, transformBottom, transformRotation, transformFlipH, transformFlipV) {
        val clip = selectedClip
        if (showTransformDialog && clip != null) {
            val effects = mutableListOf<androidx.media3.common.Effect>()
            if (transformLeft > 0f || transformTop > 0f || transformRight < 1f || transformBottom < 1f) {
                val ndcLeft = -1f + 2f * transformLeft
                val ndcRight = -1f + 2f * transformRight
                val ndcBottom = 1f - 2f * transformBottom
                val ndcTop = 1f - 2f * transformTop
                effects.add(androidx.media3.effect.Crop(ndcLeft, ndcRight, ndcBottom, ndcTop))
            }
            if (transformRotation != 0 || transformFlipH || transformFlipV) {
                val scaleX = if (transformFlipH) -1f else 1f
                val scaleY = if (transformFlipV) -1f else 1f
                effects.add(
                    androidx.media3.effect.ScaleAndRotateTransformation.Builder()
                        .setRotationDegrees(transformRotation.toFloat())
                        .setScale(scaleX, scaleY)
                        .build()
                )
            }
            if (clip.brightness != 0f) effects.add(androidx.media3.effect.Brightness(clip.brightness))
            if (clip.contrast != 0f) effects.add(androidx.media3.effect.Contrast(clip.contrast))
            if (clip.saturation != 0f) effects.add(androidx.media3.effect.HslAdjustment.Builder().adjustSaturation(clip.saturation).build())
            exoPlayer.setVideoEffects(effects)
        }
    }

    LaunchedEffect(showTransformDialog, transformSpeed) {
        if (showTransformDialog) {
            exoPlayer.setPlaybackParameters(androidx.media3.common.PlaybackParameters(transformSpeed))
        }
    }

    // Hoisted Trim-panel state - drives the RangeSlider in the dedicated Trim panel.
    // This is independent of, and in addition to, dragging the handles directly on the timeline.
    var trimPanelStart by remember { mutableStateOf(0L) }
    var trimPanelEnd by remember { mutableStateOf(0L) }

    val thumbnails = remember { mutableStateMapOf<String, List<Bitmap>>() }
    val clipDurations = remember { mutableStateMapOf<String, Long>() }
    val clipDimensions = remember { mutableStateMapOf<String, Pair<Int, Int>>() } // clipId -> (width, height)

    LaunchedEffect(showTrimDialog, selectedClip) {
        val clip = selectedClip
        if (showTrimDialog && clip != null) {
            val realDur = clipDurations[clip.id] ?: 5000L
            trimPanelStart = clip.startTimeMs
            trimPanelEnd = if (clip.endTimeMs > 0) clip.endTimeMs else realDur
        }
    }

    var currentPositionMs by remember { mutableStateOf(0L) }
    val localDensity = LocalDensity.current
    val density = localDensity.density
    val MS_TO_DP = 0.02f // 20.dp per second (1000ms)

    LaunchedEffect(uiState.clips) {
        uiState.clips.forEach { clip ->
            if (!thumbnails.containsKey(clip.id)) {
                withContext(Dispatchers.IO) {
                    val retriever = MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(context, Uri.parse(clip.uri))
                        val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                        val durationMs = durationStr?.toLongOrNull() ?: 5000L
                        clipDurations[clip.id] = durationMs

                        val rawWidth = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 1920
                        val rawHeight = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 1080
                        val rotationDeg = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
                        // Rotation metadata of 90/270 means width/height are swapped visually
                        val displayWidth = if (rotationDeg == 90 || rotationDeg == 270) rawHeight else rawWidth
                        val displayHeight = if (rotationDeg == 90 || rotationDeg == 270) rawWidth else rawHeight
                        clipDimensions[clip.id] = displayWidth to displayHeight
                        
                        val bitmapList = mutableListOf<Bitmap>()
                        val seconds = (durationMs / 1000L).coerceIn(1L, 15L)
                        val stepMs = durationMs / seconds
                        for (i in 0 until seconds) {
                            val timeUs = (i * stepMs) * 1000L
                            val bitmap = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                            if (bitmap != null) {
                                val scaled = Bitmap.createScaledBitmap(bitmap, 120, 90, false)
                                bitmapList.add(scaled)
                            }
                        }
                        if (bitmapList.isNotEmpty()) {
                            withContext(Dispatchers.Main) {
                                thumbnails[clip.id] = bitmapList
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        if (!clipDurations.containsKey(clip.id)) {
                            clipDurations[clip.id] = 5000L
                        }
                    } finally {
                        try {
                            retriever.release()
                        } catch (ex: Exception) {}
                    }
                }
            }
        }
    }

    LaunchedEffect(isPlaying, uiState.clips) {
        while (true) {
            try {
                if (exoPlayer.playbackState != Player.STATE_IDLE) {
                    val currentIndex = exoPlayer.currentMediaItemIndex
                    var pos = 0L
                    for (i in 0 until currentIndex) {
                        if (i < uiState.clips.size) {
                            val clip = uiState.clips[i]
                            val realDur = clipDurations[clip.id] ?: 5000L
                            pos += if (clip.endTimeMs > 0) {
                                (clip.endTimeMs - clip.startTimeMs).coerceAtLeast(0L)
                            } else {
                                (realDur - clip.startTimeMs).coerceAtLeast(0L)
                            }
                        }
                    }
                    val activeClip = uiState.clips.getOrNull(currentIndex)
                    val clipCurrentPos = exoPlayer.currentPosition
                    if (activeClip != null) {
                        val relativePos = (clipCurrentPos - activeClip.startTimeMs).coerceAtLeast(0L)
                        currentPositionMs = pos + relativePos
                    } else {
                        currentPositionMs = pos + clipCurrentPos
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            kotlinx.coroutines.delay(if (isPlaying) 30L else 200L)
        }
    }

    val totalDurationMs = uiState.clips.sumOf { clip ->
        val realDur = clipDurations[clip.id] ?: 5000L
        if (clip.endTimeMs > 0) {
            (clip.endTimeMs - clip.startTimeMs).coerceAtLeast(0L)
        } else {
            (realDur - clip.startTimeMs).coerceAtLeast(0L)
        }
    }

    val seekToGlobalPosition = { positionMs: Long ->
        var remainingMs = positionMs
        for (i in uiState.clips.indices) {
            val clip = uiState.clips[i]
            val realDur = clipDurations[clip.id] ?: 5000L
            val clipPlayDur = if (clip.endTimeMs > 0) {
                (clip.endTimeMs - clip.startTimeMs).coerceAtLeast(0L)
            } else {
                (realDur - clip.startTimeMs).coerceAtLeast(0L)
            }
            if (remainingMs <= clipPlayDur) {
                val targetPositionMs = clip.startTimeMs + remainingMs
                exoPlayer.seekTo(i, targetPositionMs)
                break
            }
            remainingMs -= clipPlayDur
        }
    }

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
                                        
                                    val videoEffects = getEffectsForClip(clip)
                                    val audioProcessors = mutableListOf<androidx.media3.common.audio.AudioProcessor>()
                                    if (clip.speed != 1f) {
                                        val speedProvider = object : androidx.media3.common.audio.SpeedProvider {
                                            override fun getSpeed(timeUs: Long): Float = clip.speed
                                            override fun getNextSpeedChangeTimeUs(timeUs: Long): Long = androidx.media3.common.C.TIME_UNSET
                                        }
                                        audioProcessors.add(androidx.media3.common.audio.SpeedChangingAudioProcessor(speedProvider))
                                    }
                                        
                                    EditedMediaItem.Builder(mediaItem)
                                        .setEffects(androidx.media3.transformer.Effects(audioProcessors, videoEffects))
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
                                
                                val outputFile = java.io.File(context.getExternalFilesDir(android.os.Environment.DIRECTORY_MOVIES), "exported_video_${System.currentTimeMillis()}.mp4")
                                
                                val transformer = Transformer.Builder(context)
                                    .addListener(object : Transformer.Listener {
                                        override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                                            isExporting = false

                                            exportScope.launch(Dispatchers.IO) {
                                                val values = android.content.ContentValues().apply {
                                                    put(android.provider.MediaStore.Video.Media.DISPLAY_NAME, outputFile.name)
                                                    put(android.provider.MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                                                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                                                        put(android.provider.MediaStore.Video.Media.RELATIVE_PATH, "Movies/XEditor")
                                                        put(android.provider.MediaStore.Video.Media.IS_PENDING, 1)
                                                    }
                                                }

                                                val resolver = context.contentResolver
                                                val uri = resolver.insert(android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)

                                                if (uri != null) {
                                                    try {
                                                        resolver.openOutputStream(uri)?.use { outputStream ->
                                                            java.io.FileInputStream(outputFile).use { inputStream ->
                                                                inputStream.copyTo(outputStream)
                                                            }
                                                        }

                                                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                                                            values.clear()
                                                            values.put(android.provider.MediaStore.Video.Media.IS_PENDING, 0)
                                                            resolver.update(uri, values, null, null)
                                                        }

                                                        outputFile.delete() // Clean up the external files dir file
                                                        withContext(Dispatchers.Main) {
                                                            Toast.makeText(context, "Saved to Movies/XEditor", Toast.LENGTH_LONG).show()
                                                        }
                                                    } catch (e: Exception) {
                                                        resolver.delete(uri, null, null)
                                                        withContext(Dispatchers.Main) {
                                                            Toast.makeText(context, "Failed to save to Gallery: ${e.message}", Toast.LENGTH_LONG).show()
                                                        }
                                                    }
                                                } else {
                                                    withContext(Dispatchers.Main) {
                                                        Toast.makeText(context, "Failed to create MediaStore entry", Toast.LENGTH_LONG).show()
                                                    }
                                                }
                                            }
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
                    update = { playerView ->
                        val trigger = playerTrigger
                        if (playerView.player != exoPlayer) {
                            playerView.player = exoPlayer
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

                // Crop handles drawn directly on the main preview while the Transform panel is open
                if (showTransformDialog && selectedClip != null) {
                    if (lockedAspectLabel == null) {
                        CropOverlay(
                            left = transformLeft,
                            top = transformTop,
                            right = transformRight,
                            bottom = transformBottom,
                            onCropChanged = { newL, newT, newR, newB ->
                                transformLeft = newL
                                transformTop = newT
                                transformRight = newR
                                transformBottom = newB
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        CropOverlayLocked(
                            left = transformLeft,
                            top = transformTop,
                            right = transformRight,
                            bottom = transformBottom,
                            onPanChanged = { newL, newT, newR, newB ->
                                transformLeft = newL
                                transformTop = newT
                                transformRight = newR
                                transformBottom = newB
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
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
                
                // Time Codes / Ruler
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val currentStr = formatMs(currentPositionMs)
                    val totalStr = formatMs(totalDurationMs)
                    Text("Playhead: $currentStr", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.primary)
                    Text("Total: $totalStr", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = Color.LightGray)
                }

                // Scrollable ruler, tracks and playhead
                val scrollState = rememberScrollState()
                
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(bottom = 8.dp)
                ) {
                    val viewportWidthPx = with(LocalDensity.current) { maxWidth.toPx() }

                    // Compute total visual width in dp based on real durations of all clips
                    val totalClipsWidthDp = uiState.clips.sumOf { clip ->
                        val realDur = clipDurations[clip.id] ?: 5000L
                        (realDur * MS_TO_DP).toDouble()
                    }.dp
                    
                    val timelineContentWidthDp = totalClipsWidthDp + 120.dp
                    
                    // 3. Playhead Line Calculation
                    var playheadXDp = 0.dp
                    var accumulatedX = 0f
                    var remainingPlayMs = currentPositionMs
                    var foundPlayhead = false
                    for (clip in uiState.clips) {
                        val realDur = clipDurations[clip.id] ?: 5000L
                        val clipPlayDur = if (clip.endTimeMs > 0) {
                            (clip.endTimeMs - clip.startTimeMs).coerceAtLeast(0L)
                        } else {
                            (realDur - clip.startTimeMs).coerceAtLeast(0L)
                        }
                        
                        if (!foundPlayhead && remainingPlayMs <= clipPlayDur) {
                            val offsetInClip = clip.startTimeMs + remainingPlayMs
                            playheadXDp = (accumulatedX + offsetInClip * MS_TO_DP).dp
                            foundPlayhead = true
                        }
                        accumulatedX += realDur * MS_TO_DP
                        remainingPlayMs -= clipPlayDur
                    }
                    if (!foundPlayhead && uiState.clips.isNotEmpty()) {
                        val lastClip = uiState.clips.last()
                        val realDur = clipDurations[lastClip.id] ?: 5000L
                        val endTime = if (lastClip.endTimeMs > 0) lastClip.endTimeMs else realDur
                        playheadXDp = (accumulatedX - realDur * MS_TO_DP + endTime * MS_TO_DP).dp
                    }

                    // Auto-scroll timeline during playback to keep playhead centered
                    LaunchedEffect(currentPositionMs, isPlaying) {
                        if (isPlaying) {
                            val playheadXPx = with(localDensity) { playheadXDp.toPx() }
                            val targetScroll = (playheadXPx - viewportWidthPx / 2).toInt().coerceAtLeast(0)
                            scrollState.scrollTo(targetScroll)
                        }
                    }
                    
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .horizontalScroll(scrollState)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(timelineContentWidthDp)
                                .padding(horizontal = 16.dp)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // 1. Main Video Track (Clips)
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(72.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    uiState.clips.forEach { clip ->
                                        val realDur = clipDurations[clip.id] ?: 5000L
                                        val clipWidthDp = (realDur * MS_TO_DP).coerceAtLeast(100f).dp
                                        
                                        val startTime = clip.startTimeMs
                                        val endTime = if (clip.endTimeMs > 0) clip.endTimeMs else realDur
                                        
                                        val leftPercent = if (realDur > 0) startTime.toFloat() / realDur else 0f
                                        val rightPercent = if (realDur > 0) endTime.toFloat() / realDur else 1f
                                        val currentClip = rememberUpdatedState(clip)
                                        
                                        Box(
                                            modifier = Modifier
                                                .width(clipWidthDp)
                                                .fillMaxHeight()
                                                .background(Color.DarkGray, RoundedCornerShape(6.dp))
                                                .border(
                                                    width = if (selectedClip?.id == clip.id) 2.dp else 1.dp,
                                                    color = if (selectedClip?.id == clip.id) MaterialTheme.colorScheme.primary else Color.Gray,
                                                    shape = RoundedCornerShape(6.dp)
                                                )
                                                .clickable { selectedClip = clip }
                                        ) {
                                            // Filmstrip
                                            Row(modifier = Modifier.fillMaxSize()) {
                                                val clipThumbs = thumbnails[clip.id] ?: emptyList()
                                                if (clipThumbs.isNotEmpty()) {
                                                    clipThumbs.forEach { bitmap ->
                                                        androidx.compose.foundation.Image(
                                                            bitmap = bitmap.asImageBitmap(),
                                                            contentDescription = null,
                                                            contentScale = ContentScale.Crop,
                                                            modifier = Modifier
                                                                .fillMaxHeight()
                                                                .weight(1f)
                                                        )
                                                    }
                                                } else {
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxSize()
                                                            .background(Color(0xFF3F51B5).copy(alpha = 0.2f)),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                                    }
                                                }
                                            }
                                            
                                            // Dim overlays for trimmed parts
                                            Row(modifier = Modifier.fillMaxSize()) {
                                                if (leftPercent > 0.001f) {
                                                    Box(modifier = Modifier.fillMaxHeight().weight(leftPercent).background(Color.Black.copy(alpha = 0.6f)))
                                                }
                                                Box(modifier = Modifier.fillMaxHeight().weight((rightPercent - leftPercent).coerceAtLeast(0.001f)))
                                                if (rightPercent < 0.999f) {
                                                    Box(modifier = Modifier.fillMaxHeight().weight(1f - rightPercent).background(Color.Black.copy(alpha = 0.6f)))
                                                }
                                            }
                                            
                                            // Label Overlay
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.TopStart)
                                                    .padding(4.dp)
                                                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(2.dp))
                                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                                            ) {
                                                Text("Clip ${clip.orderIndex + 1}", fontSize = 9.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                            }
                                            
                                            // Drag Trim Handles (only if selected)
                                            if (selectedClip?.id == clip.id) {
                                                // Left Trim Handle
                                                Box(
                                                    modifier = Modifier
                                                        .align(Alignment.CenterStart)
                                                        .offset(x = (leftPercent * (realDur * MS_TO_DP)).dp - 12.dp)
                                                        .width(24.dp)
                                                        .fillMaxHeight()
                                                        .pointerInput(clip.id) {
                                                            detectDragGestures { change, dragAmount ->
                                                                change.consume()
                                                                val liveClip = currentClip.value
                                                                val liveEnd = if (liveClip.endTimeMs > 0) liveClip.endTimeMs else realDur
                                                                val deltaMs = ((dragAmount.x / density) / MS_TO_DP).toLong()
                                                                val newStart = (liveClip.startTimeMs + deltaMs).coerceIn(0L, liveEnd - 500L)
                                                                viewModel.updateClip(liveClip.copy(startTimeMs = newStart))
                                                            }
                                                        },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .width(6.dp)
                                                            .fillMaxHeight()
                                                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
                                                    )
                                                    Box(
                                                        modifier = Modifier
                                                            .size(10.dp)
                                                            .background(Color.White, CircleShape)
                                                            .border(1.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                                    )
                                                }
                                                
                                                // Right Trim Handle
                                                Box(
                                                    modifier = Modifier
                                                        .align(Alignment.CenterStart)
                                                        .offset(x = (rightPercent * (realDur * MS_TO_DP)).dp - 12.dp)
                                                        .width(24.dp)
                                                        .fillMaxHeight()
                                                        .pointerInput(clip.id) {
                                                            detectDragGestures { change, dragAmount ->
                                                                change.consume()
                                                                val liveClip = currentClip.value
                                                                val liveEnd = if (liveClip.endTimeMs > 0) liveClip.endTimeMs else realDur
                                                                val deltaMs = ((dragAmount.x / density) / MS_TO_DP).toLong()
                                                                val newEnd = (liveEnd + deltaMs).coerceIn(liveClip.startTimeMs + 500L, realDur)
                                                                viewModel.updateClip(liveClip.copy(endTimeMs = if (newEnd >= realDur) -1L else newEnd))
                                                            }
                                                        },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .width(6.dp)
                                                            .fillMaxHeight()
                                                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
                                                    )
                                                    Box(
                                                        modifier = Modifier
                                                            .size(10.dp)
                                                            .background(Color.White, CircleShape)
                                                            .border(1.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    
                                    // Add Media Clip Button
                                    Box(
                                        modifier = Modifier
                                            .width(56.dp)
                                            .fillMaxHeight()
                                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp))
                                            .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                                            .clickable { pickerLauncher.launch("video/*") },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = "Add Media", tint = Color.White, modifier = Modifier.size(20.dp))
                                    }
                                }
                                
                                // 2. Audio Track UI
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
                            
                            // 3. Playhead Line
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .width(2.dp)
                                    .offset(x = playheadXDp)
                                    .background(MaterialTheme.colorScheme.primary)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .offset(x = (-5).dp, y = 0.dp)
                                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                                        .pointerInput(Unit) {
                                            detectDragGestures { change, dragAmount ->
                                                change.consume()
                                                val deltaMs = ((dragAmount.x / density) / MS_TO_DP).toLong()
                                                val newPos = (currentPositionMs + deltaMs).coerceIn(0L, totalDurationMs)
                                                currentPositionMs = newPos
                                                seekToGlobalPosition(newPos)
                                            }
                                        }
                                )
                            }
                        }
                    }
                }

                // Contextual Toolbar
                Divider(color = MaterialTheme.colorScheme.outline)
                when {
                    showAdjustDialog && selectedClip != null -> {
                        AdjustPanel(
                            clip = selectedClip!!,
                            exoPlayer = exoPlayer,
                            getEffectsForClip = { c -> getEffectsForClip(c) },
                            onDone = { updatedClip ->
                                viewModel.updateClip(updatedClip)
                                showAdjustDialog = false
                            },
                            onCancel = {
                                showAdjustDialog = false
                                exoPlayer.setVideoEffects(getEffectsForClip(selectedClip!!))
                            }
                        )
                    }
                    showTransformDialog && selectedClip != null -> {
                        val dims = clipDimensions[selectedClip!!.id] ?: (1920 to 1080)
                        TransformPanel(
                            rotation = transformRotation,
                            flipH = transformFlipH,
                            flipV = transformFlipV,
                            speed = transformSpeed,
                            clipWidth = dims.first,
                            clipHeight = dims.second,
                            lockedAspectLabel = lockedAspectLabel,
                            onRotate = { transformRotation = (transformRotation + 90) % 360 },
                            onFlipHChange = { transformFlipH = it },
                            onFlipVChange = { transformFlipV = it },
                            onSpeedChange = { transformSpeed = it },
                            onResetCrop = {
                                transformLeft = 0f
                                transformTop = 0f
                                transformRight = 1f
                                transformBottom = 1f
                                lockedAspectLabel = null
                            },
                            onAspectSelected = { label, l, t, r, b ->
                                lockedAspectLabel = label
                                transformLeft = l
                                transformTop = t
                                transformRight = r
                                transformBottom = b
                            },
                            onDone = {
                                val clip = selectedClip!!
                                val cropStr = "$transformLeft,$transformTop,$transformRight,$transformBottom"
                                viewModel.updateClip(clip.copy(
                                    cropRectString = if (transformLeft == 0f && transformTop == 0f && transformRight == 1f && transformBottom == 1f) null else cropStr,
                                    rotationDegrees = transformRotation,
                                    flipHorizontal = transformFlipH,
                                    flipVertical = transformFlipV,
                                    speed = transformSpeed
                                ))
                                showTransformDialog = false
                                lockedAspectLabel = null
                                playerTrigger++
                            },
                            onCancel = {
                                showTransformDialog = false
                                lockedAspectLabel = null
                                playerTrigger++
                                val clip = selectedClip!!
                                exoPlayer.setVideoEffects(getEffectsForClip(clip))
                                exoPlayer.setPlaybackParameters(androidx.media3.common.PlaybackParameters(clip.speed))
                            }
                        )
                    }
                    showTrimDialog && selectedClip != null -> {
                        val clip = selectedClip!!
                        val realDur = clipDurations[clip.id] ?: 5000L
                        TrimPanel(
                            startMs = trimPanelStart,
                            endMs = trimPanelEnd,
                            durationMs = realDur,
                            onRangeChange = { newStart, newEnd ->
                                trimPanelStart = newStart
                                trimPanelEnd = newEnd
                                exoPlayer.seekTo(newStart)
                            },
                            onDone = {
                                viewModel.updateClip(clip.copy(
                                    startTimeMs = trimPanelStart,
                                    endTimeMs = if (trimPanelEnd >= realDur) -1L else trimPanelEnd
                                ))
                                showTrimDialog = false
                            },
                            onCancel = { showTrimDialog = false }
                        )
                    }
                    showAudioDialog && uiState.project != null -> {
                        AudioPanel(
                            project = uiState.project!!,
                            audioPlayer = audioPlayer,
                            onPickFile = { audioPickerLauncher.launch("audio/*") },
                            onRemove = {
                                viewModel.updateProject(uiState.project!!.copy(audioUri = null))
                                showAudioDialog = false
                            },
                            onDone = { volume, startMs, endMs ->
                                viewModel.updateProject(uiState.project!!.copy(
                                    audioVolume = volume,
                                    audioStartTimeMs = startMs,
                                    audioEndTimeMs = endMs
                                ))
                                showAudioDialog = false
                            },
                            onCancel = { showAudioDialog = false }
                        )
                    }
                    else -> {
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
                            ToolbarItem(icon = Icons.Default.Crop, label = "TRANSFORM", onClick = {
                                if (selectedClip != null) showTransformDialog = true
                            })
                            ToolbarItem(icon = Icons.Default.GraphicEq, label = "AUDIO", onClick = {
                                showAudioDialog = true
                            })
                            ToolbarItem(icon = Icons.Default.TextFields, label = "TEXT")
                            ToolbarItem(icon = Icons.Default.AutoAwesome, label = "EFFECTS")
                            ToolbarItem(icon = Icons.Default.Layers, label = "LAYER")
                            ToolbarItem(icon = Icons.Default.ContentCut, label = "TRIM", onClick = {
                                if (selectedClip != null) showTrimDialog = true
                                else Toast.makeText(context, "Select a clip first", Toast.LENGTH_SHORT).show()
                            })
                        }
                    }
                }
            }
        }
    }

    // Adjust is now rendered inline via AdjustPanel() above, in the contextual toolbar area.


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

private data class AspectPreset(val label: String, val ratio: Float?) // ratio = width/height, null = No Frame (full, free-form)

private val ASPECT_PRESETS = listOf(
    AspectPreset("No Frame", null),
    AspectPreset("1:1", 1f / 1f),
    AspectPreset("4:5", 4f / 5f),
    AspectPreset("3:4", 3f / 4f),
    AspectPreset("4:3", 4f / 3f),
    AspectPreset("9:16", 9f / 16f),
    AspectPreset("16:9", 16f / 9f)
)

@Composable
fun TransformPanel(
    rotation: Int,
    flipH: Boolean,
    flipV: Boolean,
    speed: Float,
    clipWidth: Int,
    clipHeight: Int,
    lockedAspectLabel: String?,
    onRotate: () -> Unit,
    onFlipHChange: (Boolean) -> Unit,
    onFlipVChange: (Boolean) -> Unit,
    onSpeedChange: (Float) -> Unit,
    onResetCrop: () -> Unit,
    onAspectSelected: (label: String?, left: Float, top: Float, right: Float, bottom: Float) -> Unit,
    onDone: () -> Unit,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onCancel) {
                Icon(Icons.Default.Close, contentDescription = "Cancel")
            }
            Text("Transform", fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground)
            IconButton(onClick = onDone) {
                Icon(Icons.Default.Check, contentDescription = "Done", tint = MaterialTheme.colorScheme.primary)
            }
        }

        Text(
            text = if (lockedAspectLabel == null) "Drag the corners on the preview above to crop"
                   else "Drag the frame on the preview above to reposition",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )

        // Canvas-style aspect ratio presets - tap to lock the crop to that shape, centered on
        // the clip's real dimensions, then drag on the preview to choose which part shows.
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
        ) {
            items(ASPECT_PRESETS) { preset ->
                val isSelected = preset.label == (lockedAspectLabel ?: "No Frame")
                Box(
                    modifier = Modifier
                        .border(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(6.dp)
                        )
                        .clip(RoundedCornerShape(6.dp))
                        .clickable {
                            if (preset.ratio == null) {
                                onAspectSelected(null, 0f, 0f, 1f, 1f)
                            } else {
                                val safeW = clipWidth.coerceAtLeast(1)
                                val safeH = clipHeight.coerceAtLeast(1)
                                val videoAr = safeW.toFloat() / safeH.toFloat()
                                val targetAr = preset.ratio
                                var l = 0f; var t = 0f; var r = 1f; var b = 1f
                                if (targetAr > videoAr) {
                                    // Target is relatively wider than the source -> keep full width, crop height
                                    val keepHeightFrac = videoAr / targetAr
                                    t = (1f - keepHeightFrac) / 2f
                                    b = t + keepHeightFrac
                                } else {
                                    // Target is relatively taller/narrower -> keep full height, crop width
                                    val keepWidthFrac = targetAr / videoAr
                                    l = (1f - keepWidthFrac) / 2f
                                    r = l + keepWidthFrac
                                }
                                onAspectSelected(preset.label, l, t, r, b)
                            }
                        }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = preset.label,
                        fontSize = 12.sp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onRotate,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
            ) {
                Text("Rotate ${rotation}°", fontSize = 12.sp)
            }
            OutlinedIconToggleButton(checked = flipH, onCheckedChange = onFlipHChange, modifier = Modifier.weight(1f)) {
                Text("Flip H", fontSize = 12.sp, color = if (flipH) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
            }
            OutlinedIconToggleButton(checked = flipV, onCheckedChange = onFlipVChange, modifier = Modifier.weight(1f)) {
                Text("Flip V", fontSize = 12.sp, color = if (flipV) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
            }
            TextButton(onClick = onResetCrop) {
                Text("Reset", fontSize = 12.sp)
            }
        }

        Spacer(Modifier.height(4.dp))
        Text(
            text = "Speed: ${"%.1f".format(speed)}x",
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Slider(
            value = speed,
            onValueChange = onSpeedChange,
            valueRange = 0.5f..2.0f,
            steps = 14,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
        )
    }
}

@Composable
fun TrimPanel(
    startMs: Long,
    endMs: Long,
    durationMs: Long,
    onRangeChange: (Long, Long) -> Unit,
    onDone: () -> Unit,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onCancel) {
                Icon(Icons.Default.Close, contentDescription = "Cancel")
            }
            Text("Trim", fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground)
            IconButton(onClick = onDone) {
                Icon(Icons.Default.Check, contentDescription = "Done", tint = MaterialTheme.colorScheme.primary)
            }
        }

        Text(
            text = "Start: ${formatMs(startMs)}   End: ${formatMs(endMs)}   Length: ${formatMs(endMs - startMs)}",
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        val safeDuration = durationMs.coerceAtLeast(1000L)
        RangeSlider(
            value = startMs.toFloat()..endMs.toFloat(),
            onValueChange = { range ->
                val newStart = range.start.toLong().coerceIn(0L, safeDuration - 500L)
                val newEnd = range.endInclusive.toLong().coerceIn(newStart + 500L, safeDuration)
                onRangeChange(newStart, newEnd)
            },
            valueRange = 0f..safeDuration.toFloat(),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        )
    }
}

@Composable
fun AudioPanel(
    project: ProjectEntity,
    audioPlayer: ExoPlayer,
    onPickFile: () -> Unit,
    onRemove: () -> Unit,
    onDone: (Float, Long, Long) -> Unit,
    onCancel: () -> Unit
) {
    var audioVolume by remember { mutableStateOf(project.audioVolume) }
    var audioStartMs by remember { mutableStateOf(project.audioStartTimeMs) }
    var audioEndMs by remember {
        mutableStateOf(if (project.audioEndTimeMs > 0) project.audioEndTimeMs else project.audioStartTimeMs + 30000L)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onCancel) {
                Icon(Icons.Default.Close, contentDescription = "Cancel")
            }
            Text("Music", fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground)
            IconButton(
                onClick = { onDone(audioVolume, audioStartMs, audioEndMs) },
                enabled = project.audioUri != null
            ) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = "Done",
                    tint = if (project.audioUri != null) MaterialTheme.colorScheme.primary else Color.Gray
                )
            }
        }

        if (project.audioUri == null) {
            Button(
                onClick = onPickFile,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .testTag("choose_audio_file_button")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add")
                Spacer(Modifier.width(8.dp))
                Text("Choose Audio File")
            }
        } else {
            Text(
                text = "Selected: ${project.audioUri.substringAfterLast("/")}",
                fontSize = 12.sp,
                maxLines = 1,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(Modifier.height(4.dp))
            Text("Volume: ${"%.0f%%".format(audioVolume * 100)}", fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp))
            Slider(
                value = audioVolume,
                onValueChange = {
                    audioVolume = it
                    audioPlayer.volume = it
                },
                valueRange = 0f..1f,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .testTag("audio_volume_slider")
            )
            Text(
                text = "Start: ${formatMs(audioStartMs)}   End: ${formatMs(audioEndMs)}",
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            RangeSlider(
                value = audioStartMs.toFloat()..audioEndMs.toFloat(),
                onValueChange = { range ->
                    audioStartMs = range.start.toLong().coerceAtLeast(0L)
                    audioEndMs = range.endInclusive.toLong().coerceAtLeast(audioStartMs + 500L)
                },
                valueRange = 0f..600000f, // 10 minute window; adjust if your music files run longer
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                TextButton(onClick = onPickFile, modifier = Modifier.testTag("change_audio_file_button")) {
                    Text("Change file")
                }
                TextButton(
                    onClick = onRemove,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("remove_audio_button")
                ) {
                    Text("Remove")
                }
            }
        }
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

private fun formatMs(ms: Long): String {
    val totalSec = ms / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    val millis = (ms % 1000) / 100
    return "%02d:%02d.%01d".format(min, sec, millis)
}