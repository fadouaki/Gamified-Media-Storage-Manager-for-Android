package compress.joshattic.us.swipetoclean.ui.screens.compressqueue

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import compress.joshattic.us.swipetoclean.data.AppDatabase
import compress.joshattic.us.swipetoclean.data.MediaStatus
import compress.joshattic.us.swipetoclean.data.MediaStoreRepository
import compress.joshattic.us.swipetoclean.ui.screens.reviewbin.BinMediaTile
import compress.joshattic.us.swipetoclean.ui.screens.reviewbin.CompressionPreset
import compress.joshattic.us.swipetoclean.ui.screens.reviewbin.enqueueCompressionWorkers
import compress.joshattic.us.swipetoclean.ui.screens.reviewbin.formatBytes
import compress.joshattic.us.swipetoclean.compression.CompressionRequest
import compress.joshattic.us.swipetoclean.compression.CompressionResult
import compress.joshattic.us.swipetoclean.compression.MediaMetadataReader
import compress.joshattic.us.swipetoclean.compression.VideoCompressor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompressQueueScreen(
    database: AppDatabase,
    onNavigateBack: () -> Unit,
) {
    val dao = database.mediaFileDao()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { MediaStoreRepository(context) }

    // Launcher for Android 11+ deletion permission
    var pendingOriginalUri by remember { mutableStateOf<Uri?>(null) }
    val deleteLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            // Permission granted, original already deleted by system request
            pendingOriginalUri = null
        }
    }

    var showConfirmDialog by remember { mutableStateOf(false) }
    var selectedPreset by remember { mutableStateOf(CompressionPreset.MEDIUM) }
    
    val compressFiles by dao.getByStatus(MediaStatus.PENDING_COMPRESS).collectAsState(initial = emptyList())
    val compressCount = compressFiles.size
    val totalOriginalSize = remember(compressFiles) { compressFiles.sumOf { it.originalSize } }

    var isCompressing by remember { mutableStateOf(false) }
    var currentIndex by remember { mutableIntStateOf(0) }
    var currentFileName by remember { mutableStateOf("") }
    val compressor = remember { VideoCompressor(context) }
    val currentProgress by compressor.progress.collectAsState()

    LaunchedEffect(isCompressing) {
        if (!isCompressing) return@LaunchedEffect
        
        for ((index, file) in compressFiles.withIndex()) {
            if (!isCompressing) break
            
            currentIndex = index
            currentFileName = file.displayName ?: "Video ${index + 1}"
            
            try {
                dao.update(file.copy(status = MediaStatus.COMPRESSING))
                
                val outputDir = withContext(Dispatchers.IO) {
                    File(context.cacheDir, "compressed_videos").apply { mkdirs() }
                }
                val baseName = file.displayName?.substringBeforeLast(".") ?: "Compressed"
                val outputFile = File(outputDir, "${baseName}_compressed.mp4")

                val reader = MediaMetadataReader(context)
                val uriStr = file.fileUri
                withContext(Dispatchers.IO) {
                    reader.readAll(Uri.parse(uriStr))
                }

                val originalSizeMb = file.originalSize / (1024f * 1024f)
                val targetSizeMb = (originalSizeMb * selectedPreset.multiplier).coerceAtLeast(1f)

                val request = CompressionRequest(
                    sourceUri = Uri.parse(uriStr),
                    outputFile = outputFile,
                    targetSizeMb = targetSizeMb,
                    originalSize = file.originalSize,
                    originalWidth = reader.videoWidth,
                    originalHeight = reader.videoHeight,
                    originalBitrate = reader.videoBitrate,
                    originalAudioBitrate = reader.audioBitrate,
                    originalFps = reader.videoFps,
                    durationMs = reader.durationMs,
                    originalVideoMime = reader.videoMime,
                )

                val result = compressor.compress(request)
                val savedUri = withContext(Dispatchers.IO) {
                    saveToMediaStore(context, result)
                }

                if (savedUri != null) {
                    withContext(Dispatchers.IO) {
                        // Delete the original file to reclaim storage space
                        try {
                            val originalUri = Uri.parse(uriStr)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                val intentSender = repository.createDeleteRequest(listOf(originalUri))
                                if (intentSender != null) {
                                    pendingOriginalUri = originalUri
                                    deleteLauncher.launch(
                                        IntentSenderRequest.Builder(intentSender).build()
                                    )
                                }
                            } else {
                                context.contentResolver.delete(originalUri, null, null)
                            }
                        } catch (e: Exception) {
                            // If deletion fails, we still proceed as the new version is confirmed saved
                        }
                    }

                    dao.update(
                        file.copy(
                            status = MediaStatus.COMPLETED,
                            compressedUri = savedUri.toString(),
                            compressedSize = result.outputFileSize,
                        )
                    )
                } else {
                    dao.update(file.copy(status = MediaStatus.PENDING_COMPRESS))
                }

            } catch (e: Exception) {
                dao.update(file.copy(status = MediaStatus.PENDING_COMPRESS))
            }
        }
        
        isCompressing = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Compress Videos") },
                navigationIcon = {
                    IconButton(onClick = {
                        if (isCompressing) {
                            isCompressing = false
                            compressor.cancel()
                        }
                        onNavigateBack()
                    }) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // Live Progress UI
            AnimatedVisibility(visible = isCompressing) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Processing ${currentIndex + 1} of $compressCount",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = currentFileName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        LinearProgressIndicator(
                            progress = { currentProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "${(currentProgress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.align(Alignment.End)
                        )
                    }
                }
            }

            // Stats Header
            if (compressCount > 0 && !isCompressing) {
                Text(
                    text = "Smart Compression reduces video size while keeping high quality.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            // Empty state
            if (compressFiles.isEmpty() && !isCompressing) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "All videos compressed!",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    items(compressFiles, key = { it.fileUri }) { entity ->
                        BinMediaTile(
                            entity = entity,
                            onRestore = {
                                if (!isCompressing) {
                                    scope.launch {
                                        dao.update(entity.copy(status = MediaStatus.UNSEEN))
                                    }
                                }
                            },
                            onRemove = {
                                if (!isCompressing) {
                                    scope.launch {
                                        dao.deleteByUris(listOf(entity.fileUri))
                                    }
                                }
                            },
                        )
                    }
                }
            }

            // Execute button
            if (compressFiles.isNotEmpty() && !isCompressing) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp,
                    shadowElevation = 8.dp,
                ) {
                    Box(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Button(
                            onClick = { showConfirmDialog = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            shape = MaterialTheme.shapes.medium,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                            ),
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Start Compression (${compressFiles.size})",
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                    }
                }
            } else if (isCompressing) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp,
                    shadowElevation = 8.dp,
                ) {
                    Box(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        OutlinedButton(
                            onClick = { 
                                isCompressing = false
                                compressor.cancel()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            shape = MaterialTheme.shapes.medium,
                        ) {
                            Text("Stop Compression", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }
        }
    }

    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text("Select Quality") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Choose compression level for $compressCount videos. Higher quality results in larger file sizes.")
                    
                    CompressionPreset.values().forEach { preset ->
                        val estimatedSavedBytes = (totalOriginalSize * (1f - preset.multiplier)).toLong()
                        val formattedSaved = formatBytes(estimatedSavedBytes)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { selectedPreset = preset }
                                .background(if (selectedPreset == preset) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            androidx.compose.material3.RadioButton(
                                selected = selectedPreset == preset,
                                onClick = { selectedPreset = preset }
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(preset.label, style = MaterialTheme.typography.titleMedium)
                                Text(preset.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = preset.estimation,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Save ~$formattedSaved",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showConfirmDialog = false
                        isCompressing = true
                    },
                ) {
                    Text("Start")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

private fun saveToMediaStore(context: Context, result: CompressionResult): Uri? {
    val values = ContentValues().apply {
        put(MediaStore.Video.Media.DISPLAY_NAME, "compressed_${System.currentTimeMillis()}.mp4")
        put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
        put(MediaStore.Video.Media.DATE_ADDED, System.currentTimeMillis() / 1000)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Video.Media.IS_PENDING, 1)
            put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/SwipeToClean")
        }
    }

    val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    } else {
        MediaStore.Video.Media.EXTERNAL_CONTENT_URI
    }

    val itemUri = context.contentResolver.insert(collection, values) ?: return null

    context.contentResolver.openOutputStream(itemUri)?.use { out ->
        val file = File(result.outputUri.path!!)
        file.inputStream().use { input -> input.copyTo(out) }
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        values.clear()
        values.put(MediaStore.Video.Media.IS_PENDING, 0)
        context.contentResolver.update(itemUri, values, null, null)
    }

    return itemUri
}
