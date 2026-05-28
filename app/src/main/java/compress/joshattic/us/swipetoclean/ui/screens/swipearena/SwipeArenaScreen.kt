package compress.joshattic.us.swipetoclean.ui.screens.swipearena

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
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
import compress.joshattic.us.swipetoclean.ui.theme.CompressBlue
import compress.joshattic.us.swipetoclean.ui.theme.KeepGreen
import compress.joshattic.us.swipetoclean.ui.theme.TrashRed
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

enum class SwipeAction { KEEP, TRASH, COMPRESS }

data class CardState(
    val entity: compress.joshattic.us.swipetoclean.data.MediaFileEntity,
    val isVideo: Boolean,
)

@OptIn(androidx.media3.common.util.UnstableApi::class)
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

    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    
    val animOffsetX = remember { Animatable(0f) }
    val animOffsetY = remember { Animatable(0f) }
    
    val rotation by animateFloatAsState(
        targetValue = if (abs(offsetX) > 20f) offsetX / 20f else 0f,
        animationSpec = tween(100),
        label = "rotation",
    )

    var swipeAction by remember { mutableStateOf<SwipeAction?>(null) }
    var cardSize by remember { mutableStateOf(IntSize(0, 0)) }
    var isAnimating by remember { mutableStateOf(false) }
    var previousIndex by remember { mutableIntStateOf(-1) }
    var hapticTriggered by remember { mutableStateOf(false) }

    val density = LocalDensity.current
    val swipeThresholdX = with(density) { (cardSize.width * 0.3f) }
    val swipeThresholdY = with(density) { (cardSize.height * 0.2f) }

    LaunchedEffect(Unit) {
        dao.getReviewableFiles().collectLatest { files ->
            if (allFiles.isEmpty() && files.isNotEmpty()) {
                allFiles = files.map { CardState(it, it.isVideo) }
                if (startIndex < allFiles.size) {
                    currentIndex = startIndex
                }
            }
        }
    }

    LaunchedEffect(offsetX, offsetY) {
        val reachedX = abs(offsetX) > swipeThresholdX
        val reachedY = offsetY < -swipeThresholdY && allFiles.getOrNull(currentIndex)?.isVideo == true
        
        if ((reachedX || reachedY) && !hapticTriggered) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            hapticTriggered = true
        } else if (!reachedX && !reachedY) {
            hapticTriggered = false
        }
    }

    suspend fun performSwipe(action: SwipeAction) {
        val entity = allFiles.getOrNull(currentIndex) ?: return
        if (isAnimating) return
        isAnimating = true
        swipeAction = action

        val targetX = when (action) {
            SwipeAction.KEEP -> cardSize.width * 1.5f
            SwipeAction.TRASH -> -cardSize.width * 1.5f
            else -> 0f
        }
        val targetY = when (action) {
            SwipeAction.COMPRESS -> -cardSize.height * 1.5f
            else -> 0f
        }

        animOffsetX.snapTo(offsetX)
        animOffsetY.snapTo(offsetY)
        
        scope.launch { 
            animOffsetX.animateTo(targetX, tween(300)) { offsetX = value }
        }
        scope.launch { 
            animOffsetY.animateTo(targetY, tween(300)) { offsetY = value }
        }

        val newStatus = when (action) {
            SwipeAction.KEEP -> MediaStatus.KEPT
            SwipeAction.TRASH -> MediaStatus.TRASHED
            SwipeAction.COMPRESS -> MediaStatus.PENDING_COMPRESS
        }
        dao.getByUri(entity.entity.fileUri)?.let { e ->
            dao.update(e.copy(status = newStatus))
        }

        kotlinx.coroutines.delay(300)

        previousIndex = currentIndex
        currentIndex++
        prefs.edit().putInt("last_swipe_index", currentIndex).apply()

        offsetX = 0f
        offsetY = 0f
        animOffsetX.snapTo(0f)
        animOffsetY.snapTo(0f)
        swipeAction = null
        isAnimating = false
        hapticTriggered = false
    }

    suspend fun undoLastAction() {
        if (previousIndex < 0) return
        val previous = allFiles[previousIndex]
        dao.getByUri(previous.entity.fileUri)?.let { entity ->
            dao.update(entity.copy(status = MediaStatus.UNSEEN))
        }
        currentIndex = previousIndex
        previousIndex = -1
        prefs.edit().putInt("last_swipe_index", currentIndex).apply()
        offsetX = 0f
        offsetY = 0f
        animOffsetX.snapTo(0f)
        animOffsetY.snapTo(0f)
        swipeAction = null
    }

    val currentEntity = allFiles.getOrNull(currentIndex)
    val nextEntity = allFiles.getOrNull(currentIndex + 1)
    val remainingCount = (allFiles.size - currentIndex).coerceAtLeast(0)

    if (currentEntity == null) {
        Box(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text(text = "🎉", fontSize = 64.sp)
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = "All Done!", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = "You've reviewed everything in your library.", style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = 0.7f), textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(32.dp))
                Button(onClick = onAllDone) { Text("Back to Dashboard") }
            }
        }
        return
    }

    Box(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        if (nextEntity != null) {
            MediaCard(entity = nextEntity.entity, isVideo = nextEntity.isVideo, containerSize = cardSize, isBackground = true, swipeAction = null)
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .onSizeChanged { cardSize = it }
                .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                .rotate(rotation)
        ) {
            MediaCard(entity = currentEntity.entity, isVideo = currentEntity.isVideo, containerSize = cardSize, isBackground = false, swipeAction = swipeAction)

            val overlayAlpha = when {
                offsetY < -20f && currentEntity.isVideo -> (abs(offsetY) / swipeThresholdY).coerceIn(0f, 0.4f)
                abs(offsetX) > 20f -> (abs(offsetX) / swipeThresholdX).coerceIn(0f, 0.4f)
                else -> 0f
            }
            val overlayColor = when {
                offsetY < -20f && currentEntity.isVideo -> CompressBlue
                offsetX > 20f -> KeepGreen
                offsetX < -20f -> TrashRed
                else -> Color.Transparent
            }

            Box(modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)).background(overlayColor.copy(alpha = overlayAlpha)))

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(currentEntity) {
                        detectDragGestures(
                            onDragEnd = {
                                scope.launch {
                                    val hProgress = abs(offsetX) / swipeThresholdX.coerceAtLeast(1f)
                                    val vProgress = abs(offsetY) / swipeThresholdY.coerceAtLeast(1f)
                                    val isUpSwipe = offsetY < -swipeThresholdY && vProgress >= hProgress

                                    when {
                                        isUpSwipe && currentEntity.isVideo -> performSwipe(SwipeAction.COMPRESS)
                                        offsetX > swipeThresholdX -> performSwipe(SwipeAction.KEEP)
                                        offsetX < -swipeThresholdX -> performSwipe(SwipeAction.TRASH)
                                        else -> {
                                            animOffsetX.snapTo(offsetX)
                                            animOffsetY.snapTo(offsetY)
                                            launch { animOffsetX.animateTo(0f, spring()) { offsetX = value } }
                                            launch { animOffsetY.animateTo(0f, spring()) { offsetY = value } }
                                        }
                                    }
                                }
                            },
                            onDragCancel = {
                                scope.launch {
                                    animOffsetX.snapTo(offsetX)
                                    animOffsetY.snapTo(offsetY)
                                    launch { animOffsetX.animateTo(0f, spring()) { offsetX = value } }
                                    launch { animOffsetY.animateTo(0f, spring()) { offsetY = value } }
                                }
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                offsetX += dragAmount.x
                                offsetY += dragAmount.y
                            },
                        )
                    }
            )

            val showHints = abs(offsetX) < 20f && abs(offsetY) < 20f && !isAnimating && currentIndex < 3
            if (showHints) {
                Box(modifier = Modifier.align(Alignment.CenterStart).padding(start = 16.dp).size(40.dp).clip(RoundedCornerShape(20)).background(TrashRed.copy(alpha = 0.7f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.KeyboardArrowLeft, "Swipe left to trash", tint = Color.White, modifier = Modifier.size(28.dp))
                }
                Box(modifier = Modifier.align(Alignment.CenterEnd).padding(end = 16.dp).size(40.dp).clip(RoundedCornerShape(20)).background(KeepGreen.copy(alpha = 0.7f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.KeyboardArrowRight, "Swipe right to keep", tint = Color.White, modifier = Modifier.size(28.dp))
                }
                if (currentEntity.isVideo) {
                    Box(modifier = Modifier.align(Alignment.TopCenter).padding(top = 16.dp).size(40.dp).clip(RoundedCornerShape(20)).background(CompressBlue.copy(alpha = 0.7f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.AutoAwesome, "Swipe up to compress", tint = Color.White, modifier = Modifier.size(24.dp))
                    }
                }
            }
        }

        if (offsetY < -swipeThresholdY / 2 && currentEntity.isVideo) {
            Box(modifier = Modifier.align(Alignment.TopCenter).padding(top = 80.dp).clip(RoundedCornerShape(50)).background(CompressBlue.copy(alpha = 0.85f)).padding(horizontal = 20.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AutoAwesome, null, tint = Color.White, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("COMPRESS", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        if (abs(offsetX) > 50f) {
            val isRight = offsetX > 0
            Box(
                modifier = Modifier
                    .align(if (isRight) Alignment.CenterEnd else Alignment.CenterStart)
                    .padding(16.dp)
                    .size(80.dp)
                    .scale((abs(offsetX) / swipeThresholdX).coerceIn(0.8f, 1.2f))
                    .clip(CircleShape)
                    .background(if (isRight) KeepGreen.copy(alpha = 0.9f) else TrashRed.copy(alpha = 0.9f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = if (isRight) Icons.Default.Check else Icons.Default.Delete, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
            }
        }

        Row(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                if (previousIndex >= 0) {
                    IconButton(onClick = { scope.launch { undoLastAction() } }, modifier = Modifier.size(56.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
                        Icon(Icons.Default.ArrowBack, "Undo")
                    }
                }
            }
            Box(modifier = Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                IconButton(onClick = { scope.launch { performSwipe(SwipeAction.TRASH) } }, modifier = Modifier.size(64.dp).clip(CircleShape).background(TrashRed)) {
                    Icon(Icons.Default.Delete, "Trash", tint = Color.White, modifier = Modifier.size(32.dp))
                }
            }
            Box(modifier = Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                if (currentEntity.isVideo) {
                    IconButton(onClick = { scope.launch { performSwipe(SwipeAction.COMPRESS) } }, modifier = Modifier.size(64.dp).clip(CircleShape).background(CompressBlue)) {
                        Icon(Icons.Default.AutoAwesome, "Compress", tint = Color.White, modifier = Modifier.size(32.dp))
                    }
                } else {
                    Box(modifier = Modifier.size(64.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.AutoAwesome, null, tint = Color.White.copy(alpha = 0.3f), modifier = Modifier.size(32.dp))
                    }
                }
            }
            Box(modifier = Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                IconButton(onClick = { scope.launch { performSwipe(SwipeAction.KEEP) } }, modifier = Modifier.size(64.dp).clip(CircleShape).background(KeepGreen)) {
                    Icon(Icons.Default.Check, "Keep", tint = Color.White, modifier = Modifier.size(32.dp))
                }
            }
        }

        Row(modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onNavigateBack) { Icon(Icons.Default.Close, "Close") }
            Text(text = "$remainingCount remaining", style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.7f), modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            Spacer(modifier = Modifier.width(48.dp))
        }
    }
}

@Composable
fun MediaCard(
    entity: compress.joshattic.us.swipetoclean.data.MediaFileEntity,
    isVideo: Boolean,
    containerSize: IntSize,
    isBackground: Boolean,
    swipeAction: SwipeAction?,
) {
    val context = LocalContext.current
    val scale = if (isBackground) 0.9f else 1f
    val bgAlpha = if (isBackground) 0.7f else 1f

    var isPlaying by remember { mutableStateOf(false) }
    val exoPlayer = remember {
        if (isVideo && !isBackground) ExoPlayer.Builder(context).build() else null
    }

    LaunchedEffect(isPlaying) {
        if (isPlaying && exoPlayer != null) {
            val uri = Uri.parse(entity.fileUri)
            exoPlayer.setMediaItem(MediaItem.fromUri(uri))
            exoPlayer.prepare()
            exoPlayer.play()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer?.release()
        }
    }

    LaunchedEffect(isBackground) {
        if (isBackground && isPlaying) {
            exoPlayer?.stop()
            isPlaying = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .scale(scale)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black)
            .then(
                if (isBackground) Modifier.graphicsLayer(alpha = bgAlpha)
                else Modifier
            ),
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(Uri.parse(entity.fileUri))
                .crossfade(true)
                .build(),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(alpha = 0.4f)
                .blur(20.dp),
            contentScale = ContentScale.Crop,
        )

        if (isPlaying && exoPlayer != null) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = true
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(Uri.parse(entity.fileUri))
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )

            if (isVideo && !isBackground) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable { isPlaying = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = "Play video",
                            tint = Color.White,
                            modifier = Modifier.size(40.dp),
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text("VIDEO", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                    )
                )
                .padding(16.dp),
        ) {
            Column {
                entity.displayName?.let { name ->
                    Text(
                        text = name,
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                    )
                }
                val sizeMb = entity.originalSize / (1024.0 * 1024.0)
                Text(
                    text = if (sizeMb >= 1) "%.1f MB".format(sizeMb) else "${entity.originalSize / 1024} KB",
                    color = Color.White.copy(alpha = 0.9f),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
