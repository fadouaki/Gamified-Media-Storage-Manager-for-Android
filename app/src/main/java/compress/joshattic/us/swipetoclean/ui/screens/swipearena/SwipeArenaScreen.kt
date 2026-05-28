package compress.joshattic.us.swipetoclean.ui.screens.swipearena

import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import compress.joshattic.us.swipetoclean.data.AppDatabase
import compress.joshattic.us.swipetoclean.data.MediaStatus
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

enum class SwipeAction { KEEP, TRASH, COMPRESS }

data class CardState(
    val entity: compress.joshattic.us.swipetoclean.data.MediaFileEntity,
    val isVideo: Boolean,
)

@Composable
fun SwipeArenaScreen(
    database: AppDatabase,
    startIndex: Int = 0,
    onNavigateBack: () -> Unit,
    onAllDone: () -> Unit,
) {
    val dao = database.mediaFileDao()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val prefs = remember { context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE) }

    var allFiles by remember { mutableStateOf<List<CardState>>(emptyList()) }
    var currentIndex by remember { mutableIntStateOf(startIndex) }
    var previousIndex by remember { mutableIntStateOf(-1) }

    // Motion State
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    val animOffsetX = remember { Animatable(0f) }
    val animOffsetY = remember { Animatable(0f) }
    var cardSize by remember { mutableStateOf(IntSize(0, 0)) }
    var isAnimating by remember { mutableStateOf(false) }

    val rotation by animateFloatAsState(
        targetValue = (offsetX / 60f).coerceIn(-15f, 15f),
        animationSpec = tween(100),
        label = "card_rotation"
    )

    // Splash-matching background
    val splashBackground = Brush.radialGradient(
        colors = listOf(
            Color(0xFF1A1A2E),
            Color(0xFF16213E),
            Color(0xFF0F3460),
        ),
        center = Offset(400f, 800f),
        radius = 1200f,
    )

    LaunchedEffect(Unit) {
        dao.getReviewableFiles().collectLatest { files ->
            if (allFiles.isEmpty() && files.isNotEmpty()) {
                allFiles = files.map { CardState(it, it.isVideo) }
                currentIndex = startIndex.coerceAtMost(allFiles.size - 1)
            }
        }
    }

    suspend fun performSwipe(action: SwipeAction) {
        if (isAnimating) return
        val entity = allFiles.getOrNull(currentIndex) ?: return
        isAnimating = true

        try {
            val targetX = when (action) {
                SwipeAction.KEEP -> cardSize.width * 1.5f
                SwipeAction.TRASH -> -cardSize.width * 1.5f
                else -> 0f
            }
            val targetY = when (action) {
                SwipeAction.COMPRESS -> -cardSize.height * 1.5f
                else -> 0f
            }

            // IMPORTANT: Snap to current position so animation starts from where the finger is
            animOffsetX.snapTo(offsetX)
            animOffsetY.snapTo(offsetY)

            // Run animations in parallel
            val jobX = scope.launch { 
                animOffsetX.animateTo(targetX, tween(300, easing = LinearOutSlowInEasing)) { 
                    offsetX = value 
                } 
            }
            val jobY = scope.launch { 
                animOffsetY.animateTo(targetY, tween(300, easing = LinearOutSlowInEasing)) { 
                    offsetY = value 
                } 
            }

            val newStatus = when (action) {
                SwipeAction.KEEP -> MediaStatus.KEPT
                SwipeAction.TRASH -> MediaStatus.TRASHED
                SwipeAction.COMPRESS -> MediaStatus.PENDING_COMPRESS
            }
            
            dao.getByUri(entity.entity.fileUri)?.let { e -> dao.update(e.copy(status = newStatus)) }
            
            // Wait for animations to finish before moving to next card
            jobX.join()
            jobY.join()
            
            previousIndex = currentIndex
            currentIndex++
            prefs.edit().putInt("last_swipe_index", currentIndex).apply()

            // Reset for next card
            offsetX = 0f; offsetY = 0f
            animOffsetX.snapTo(0f); animOffsetY.snapTo(0f)
        } finally {
            isAnimating = false
        }
    }

    suspend fun undoLastAction() {
        if (previousIndex < 0) return
        val previous = allFiles[previousIndex]
        dao.getByUri(previous.entity.fileUri)?.let { dao.update(it.copy(status = MediaStatus.UNSEEN)) }
        currentIndex = previousIndex
        previousIndex = -1
        prefs.edit().putInt("last_swipe_index", currentIndex).apply()
    }

    val currentEntity = allFiles.getOrNull(currentIndex)
    val nextEntity = allFiles.getOrNull(currentIndex + 1)
    val remainingCount = (allFiles.size - currentIndex).coerceAtLeast(0)

    if (currentEntity == null) {
        EmptyStateView(onAllDone)
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(splashBackground)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // 1. Stack Layer (More Padding for bottom buttons)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 100.dp)
                .onSizeChanged { cardSize = it }
        ) {
            if (nextEntity != null) {
                MediaCard(entity = nextEntity.entity, isVideo = nextEntity.isVideo, isBackground = true)
            }

            key(currentEntity.entity.fileUri) {
                InteractiveCard(
                    currentEntity = currentEntity,
                    offsetX = offsetX,
                    offsetY = offsetY,
                    rotation = rotation,
                    onDrag = { dx, dy -> 
                        offsetX += dx
                        offsetY += dy
                    },
                    onDragEnd = {
                        val thresholdX = cardSize.width * 0.25f // More sensitive
                        val thresholdY = cardSize.height * 0.15f
                        scope.launch {
                            when {
                                offsetX > thresholdX -> performSwipe(SwipeAction.KEEP)
                                offsetX < -thresholdX -> performSwipe(SwipeAction.TRASH)
                                offsetY < -thresholdY && currentEntity.isVideo -> performSwipe(SwipeAction.COMPRESS)
                                else -> {
                                    scope.launch { animOffsetX.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy)) { offsetX = value } }
                                    scope.launch { animOffsetY.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy)) { offsetY = value } }
                                }
                            }
                        }
                    }
                )
            }
        }

        // 2. Controls & Overlays
        TopHeader(remainingCount, onNavigateBack)
        
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
        ) {
            BottomActions(
                canUndo = previousIndex >= 0,
                isVideo = currentEntity.isVideo,
                onUndo = { scope.launch { undoLastAction() } },
                onAction = { action -> scope.launch { performSwipe(action) } }
            )
        }
    }
}

@Composable
private fun InteractiveCard(
    currentEntity: CardState,
    offsetX: Float,
    offsetY: Float,
    rotation: Float,
    onDrag: (Float, Float) -> Unit,
    onDragEnd: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
            .rotate(rotation)
            .pointerInput(currentEntity.entity.fileUri) {
                detectDragGestures(
                    onDrag = { change, dragAmount ->
                        change.consume()
                        onDrag(dragAmount.x, dragAmount.y)
                    },
                    onDragEnd = onDragEnd
                )
            }
    ) {
        MediaCard(entity = currentEntity.entity, isVideo = currentEntity.isVideo, isBackground = false)
        
        // Dynamic Action Labels
        ActionLabelOverlay(offsetX, offsetY, currentEntity.isVideo)
    }
}

@Composable
private fun MediaCard(
    entity: compress.joshattic.us.swipetoclean.data.MediaFileEntity,
    isVideo: Boolean,
    isBackground: Boolean
) {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(false) }
    var isPaused by remember { mutableStateOf(false) }
    var hasError by remember { mutableStateOf(false) }
    
    val exoPlayer = remember {
        if (isVideo && !isBackground) {
            ExoPlayer.Builder(context).build().apply {
                repeatMode = Player.REPEAT_MODE_ONE
                addListener(object : Player.Listener {
                    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                        hasError = true
                    }
                })
            }
        } else null
    }

    val cardScale by animateFloatAsState(if (isBackground) 0.92f else 1f)
    val cardAlpha by animateFloatAsState(if (isBackground) 0.6f else 1f)

    LaunchedEffect(isPaused) {
        if (isPaused) exoPlayer?.pause() else exoPlayer?.play()
    }

    DisposableEffect(entity.fileUri) {
        onDispose {
            exoPlayer?.release()
        }
    }

    Card(
        modifier = Modifier
            .fillMaxSize()
            .scale(cardScale)
            .graphicsLayer(alpha = cardAlpha),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isBackground) 0.dp else 4.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            // Media Content
            if (isPlaying && exoPlayer != null) {
                if (hasError) {
                    // Error state UI
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Warning, null, tint = Color.Gray, modifier = Modifier.size(48.dp))
                            Text("Video format not supported", color = Color.Gray, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                } else {
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                player = exoPlayer
                                useController = false
                                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                                // IMPORTANT: Disable all touch consumption so gestures pass to parent
                                setClickable(false)
                                setFocusable(false)
                                // Return false to NOT consume the touch, letting it pass to Compose
                                setOnTouchListener { _, _ -> false } 
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                        update = { view ->
                            view.player = exoPlayer
                        }
                    )

                    LaunchedEffect(entity.fileUri) {
                        exoPlayer.setMediaItem(MediaItem.fromUri(Uri.parse(entity.fileUri)))
                        exoPlayer.prepare()
                        exoPlayer.play()
                    }

                    // Pause/Resume Toggle Overlay
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        FilledIconButton(
                            onClick = { isPaused = !isPaused },
                            modifier = Modifier.size(56.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = Color.Black.copy(alpha = 0.4f),
                                contentColor = Color.White
                            )
                        ) {
                            Icon(
                                imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                contentDescription = if (isPaused) "Resume" else "Pause",
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }
            } else {
                AsyncImage(
                    model = ImageRequest.Builder(context).data(entity.fileUri).crossfade(true).build(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
                if (isVideo && !isBackground) {
                    FilledIconButton(
                        onClick = { isPlaying = true },
                        modifier = Modifier.align(Alignment.Center).size(72.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = Color.Black.copy(alpha = 0.5f)
                        )
                    ) {
                        Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(40.dp), tint = Color.White)
                    }
                }
            }

            // Scrims & Metadata
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.4f),
                            0.2f to Color.Transparent,
                            0.7f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.8f)
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(24.dp)
            ) {
                Text(
                    text = entity.displayName ?: "Unnamed File",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${entity.originalSize / (1024 * 1024)} MB",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.8f)
                )
            }
            
            if (isVideo) {
                Surface(
                    modifier = Modifier.align(Alignment.TopEnd).padding(16.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.9f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        "VIDEO",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionLabelOverlay(offsetX: Float, offsetY: Float, isVideo: Boolean) {
    val hAlpha = (abs(offsetX) / 250f).coerceIn(0f, 1f)
    val vAlpha = (abs(offsetY) / 200f).coerceIn(0f, 1f)
    
    Box(modifier = Modifier.fillMaxSize().padding(32.dp)) {
        if (offsetX > 40) {
            ActionTag("KEEP", Color(0xFF4CAF50), Modifier.align(Alignment.TopStart), hAlpha)
        } else if (offsetX < -40) {
            ActionTag("TRASH", MaterialTheme.colorScheme.error, Modifier.align(Alignment.TopEnd), hAlpha)
        }
        
        if (offsetY < -40 && isVideo) {
            ActionTag("COMPRESS", MaterialTheme.colorScheme.tertiary, Modifier.align(Alignment.BottomCenter), vAlpha)
        }
    }
}

@Composable
private fun ActionTag(text: String, color: Color, modifier: Modifier, alpha: Float) {
    Surface(
        modifier = modifier.graphicsLayer(alpha = alpha).rotate(if (text == "KEEP") -15f else 15f),
        color = Color.Transparent,
        border = androidx.compose.foundation.BorderStroke(4.dp, color),
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            color = color,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Black
        )
    }
}

@Composable
private fun BottomActions(
    canUndo: Boolean,
    isVideo: Boolean,
    onUndo: () -> Unit,
    onAction: (SwipeAction) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.2f))
                ),
                shape = RoundedCornerShape(32.dp)
            )
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Undo - Smaller, subtle
        IconButton(
            onClick = onUndo,
            enabled = canUndo,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(if (canUndo) Color.White.copy(alpha = 0.15f) else Color.Transparent)
        ) {
            Icon(
                Icons.AutoMirrored.Filled.Undo,
                "Undo",
                tint = if (canUndo) Color.White else Color.White.copy(alpha = 0.3f),
                modifier = Modifier.size(24.dp)
            )
        }

        // Trash - Tonal Red
        Surface(
            onClick = { onAction(SwipeAction.TRASH) },
            modifier = Modifier.size(56.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f),
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
            tonalElevation = 4.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Delete, "Trash", modifier = Modifier.size(28.dp))
            }
        }

        // Compress - Tonal Blue/Tertiary
        if (isVideo) {
            Surface(
                onClick = { onAction(SwipeAction.COMPRESS) },
                modifier = Modifier.size(64.dp), // Slightly larger as primary unique action
                shape = CircleShape,
                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.8f),
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                tonalElevation = 6.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.AutoAwesome, "Compress", modifier = Modifier.size(32.dp))
                }
            }
        } else {
            // Placeholder to keep spacing consistent
            Spacer(modifier = Modifier.size(64.dp))
        }

        // Keep - Tonal Green
        Surface(
            onClick = { onAction(SwipeAction.KEEP) },
            modifier = Modifier.size(56.dp),
            shape = CircleShape,
            color = Color(0xFF4CAF50).copy(alpha = 0.2f),
            contentColor = Color(0xFF81C784), // Brighter green for contrast
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF4CAF50).copy(alpha = 0.5f)),
            tonalElevation = 4.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Check, "Keep", modifier = Modifier.size(28.dp))
            }
        }
    }
}

@Composable
private fun TopHeader(remaining: Int, onClose: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onClose) {
            Icon(Icons.Default.Close, "Close", tint = Color.White)
        }
        Surface(
            color = Color.White.copy(alpha = 0.15f),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(
                text = "$remaining remaining",
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelLarge,
                color = Color.White
            )
        }
        Spacer(modifier = Modifier.width(48.dp))
    }
}

@Composable
private fun EmptyStateView(onBack: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().background(
        Brush.radialGradient(
            colors = listOf(Color(0xFF1A1A2E), Color(0xFF0F3460)),
            radius = 1200f
        )
    ), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("✨", fontSize = 80.sp)
            Spacer(modifier = Modifier.height(24.dp))
            Text("Gallery Cleaned!", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Color.White)
            Text("You've reviewed all your media.", style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = 0.7f))
            Spacer(modifier = Modifier.height(32.dp))
            Button(onClick = onBack) { Text("Return to Dashboard") }
        }
    }
}
