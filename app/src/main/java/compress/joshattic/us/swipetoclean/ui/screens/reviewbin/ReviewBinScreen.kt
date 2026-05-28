package compress.joshattic.us.swipetoclean.ui.screens.reviewbin

import android.app.Activity
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import coil.compose.AsyncImage
import coil.request.ImageRequest
import compress.joshattic.us.swipetoclean.data.AppDatabase
import compress.joshattic.us.swipetoclean.data.MediaFileEntity
import compress.joshattic.us.swipetoclean.data.MediaStatus
import compress.joshattic.us.swipetoclean.data.MediaStoreRepository
import compress.joshattic.us.swipetoclean.ui.theme.CompressBlue
import compress.joshattic.us.swipetoclean.ui.theme.TrashRed
import compress.joshattic.us.swipetoclean.ui.screens.reviewbin.workers.CompressionWorker
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewBinScreen(
    database: AppDatabase,
    initialFilter: String = "all",
    onNavigateBack: () -> Unit,
) {
    val dao = database.mediaFileDao()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { MediaStoreRepository(context) }

    var selectedFilter by remember { mutableStateOf(initialFilter) }
    var showConfirmDialog by remember { mutableStateOf(false) }
    var pendingDeleteUris by remember { mutableStateOf<List<String>>(emptyList()) }
    var pendingCompressFiles by remember { mutableStateOf<List<MediaFileEntity>>(emptyList()) }

    // System delete request launcher (API 30+)
    val deleteLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            scope.launch {
                // Delete succeeded - remove from Room
                dao.deleteByUris(pendingDeleteUris)
                // Enqueue compression workers
                enqueueCompressionWorkers(context, pendingCompressFiles)
            }
        }
    }

    val binnedFiles by dao.getBinnedFiles().collectAsState(initial = emptyList())

    val filteredFiles = when (selectedFilter) {
        "delete" -> binnedFiles.filter { it.status == MediaStatus.TRASHED }
        "compress" -> binnedFiles.filter { it.status == MediaStatus.PENDING_COMPRESS }
        else -> binnedFiles
    }

    val trashFiles = binnedFiles.filter { it.status == MediaStatus.TRASHED }
    val compressFiles = binnedFiles.filter { it.status == MediaStatus.PENDING_COMPRESS }
    val trashCount = trashFiles.size
    val compressCount = compressFiles.size
    val totalTrashedBytes = trashFiles.sumOf { it.originalSize }

    fun executeDeleteAndCompress() {
        if (trashFiles.isEmpty() && compressFiles.isEmpty()) return

        if (trashFiles.isNotEmpty()) {
            pendingDeleteUris = trashFiles.map { it.fileUri }
            pendingCompressFiles = compressFiles

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Use system delete request (shows confirmation dialog)
                val uris = trashFiles.map { Uri.parse(it.fileUri) }
                val intentSender = repository.createDeleteRequest(uris)
                if (intentSender != null) {
                    deleteLauncher.launch(
                        IntentSenderRequest.Builder(intentSender).build()
                    )
                    return
                }
            }

            // Fallback: direct delete for API < 30
            scope.launch {
                val result = repository.deleteFiles(trashFiles.map { it.fileUri })
                if (result.isSuccess) {
                    dao.deleteByUris(trashFiles.map { it.fileUri })
                }
                enqueueCompressionWorkers(context, compressFiles)
            }
        } else {
            enqueueCompressionWorkers(context, compressFiles)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Review Bin") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
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
            // Filter chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = selectedFilter == "all",
                    onClick = { selectedFilter = "all" },
                    label = { Text("All (${binnedFiles.size})") },
                )
                FilterChip(
                    selected = selectedFilter == "delete",
                    onClick = { selectedFilter = "delete" },
                    label = { Text("To Delete ($trashCount)") },
                )
                FilterChip(
                    selected = selectedFilter == "compress",
                    onClick = { selectedFilter = "compress" },
                    label = { Text("To Compress ($compressCount)") },
                )
            }

            // Stats
            if (totalTrashedBytes > 0 || compressCount > 0) {
                Text(
                    text = "Deleting ${trashCount} files frees ${formatBytes(totalTrashedBytes)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Empty state
            if (filteredFiles.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "No files in this category",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                // Grid
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    items(filteredFiles, key = { it.fileUri }) { entity ->
                        BinMediaTile(
                            entity = entity,
                            onRestore = {
                                scope.launch {
                                    dao.update(entity.copy(status = MediaStatus.UNSEEN))
                                }
                            },
                            onRemove = {
                                scope.launch {
                                    dao.deleteByUris(listOf(entity.fileUri))
                                }
                            },
                        )
                    }
                }
            }

            // Execute button
            if (binnedFiles.isNotEmpty()) {
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
                                containerColor = if (compressCount == 0 && trashCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            ),
                        ) {
                            Icon(
                                if (compressCount == 0 && trashCount > 0) Icons.Default.Delete else Icons.Default.AutoAwesome,
                                contentDescription = null
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Execute Actions (${binnedFiles.size})",
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                    }
                }
            }
        }
    }

    // Confirmation dialog
    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text("Confirm Actions") },
            text = {
                Text(
                    buildString {
                        if (trashCount > 0) {
                            append("$trashCount files will be permanently deleted")
                            append(" (${formatBytes(totalTrashedBytes)} freed).")
                        }
                        if (compressCount > 0) {
                            if (trashCount > 0) append("\n\n")
                            append("$compressCount files will be compressed in the background.")
                        }
                        append("\n\nThis cannot be undone.")
                    }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showConfirmDialog = false
                        executeDeleteAndCompress()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Text("Execute")
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

@Composable
fun BinMediaTile(
    entity: MediaFileEntity,
    onRestore: () -> Unit,
    onRemove: () -> Unit,
) {
    var showOverlay by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable { showOverlay = !showOverlay },
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(Uri.parse(entity.fileUri))
                .crossfade(true)
                .size(150)
                .build(),
            contentDescription = null,
            modifier = Modifier.fillMaxWidth(),
            contentScale = ContentScale.Crop,
        )

        // Status badge
        val badgeColor = when (entity.status) {
            MediaStatus.TRASHED -> TrashRed
            MediaStatus.PENDING_COMPRESS -> CompressBlue
            else -> Color.Gray
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(badgeColor.copy(alpha = 0.8f))
                .padding(horizontal = 6.dp, vertical = 2.dp),
        ) {
            Text(
                text = if (entity.status == MediaStatus.TRASHED) "DEL" else "CMP",
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        // Size overlay
        val sizeMb = entity.originalSize / (1024.0 * 1024.0)
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(4.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color.Black.copy(alpha = 0.6f))
                .padding(horizontal = 4.dp, vertical = 2.dp),
        ) {
            Text(
                text = if (sizeMb >= 1) "%.1f MB".format(sizeMb) else "${entity.originalSize / 1024}KB",
                color = Color.White,
                fontSize = 10.sp,
            )
        }

        // Action overlay
        if (showOverlay) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    IconButton(onClick = {
                        showOverlay = false
                        onRestore()
                    }) {
                        Icon(Icons.Default.Restore, "Restore", tint = Color.White)
                    }
                    IconButton(onClick = {
                        showOverlay = false
                        onRemove()
                    }) {
                        Icon(Icons.Default.Delete, "Remove", tint = TrashRed)
                    }
                }
            }
        }
    }
}

enum class CompressionPreset(val label: String, val description: String, val multiplier: Float, val estimation: String) {
    HIGH("High Quality", "Keeps maximum detail", 0.8f, "20% smaller"),
    MEDIUM("Balanced", "Best for social media", 0.5f, "50% smaller"),
    LOW("Smallest Size", "Maximum storage saving", 0.25f, "75% smaller")
}

fun enqueueCompressionWorkers(
    context: android.content.Context,
    files: List<MediaFileEntity>,
    preset: CompressionPreset = CompressionPreset.MEDIUM,
) {
    val compressFiles = files.filter { it.status == MediaStatus.PENDING_COMPRESS }
    if (compressFiles.isEmpty()) return

    val constraints = Constraints.Builder()
        .setRequiresBatteryNotLow(true)
        .setRequiresStorageNotLow(true)
        .build()

    val compressionWorks = compressFiles.map { file ->
        val originalSizeMb = file.originalSize / (1024f * 1024f)
        val targetSizeMb = (originalSizeMb * preset.multiplier).coerceAtLeast(1f) // Ensure at least 1MB target

        OneTimeWorkRequestBuilder<CompressionWorker>()
            .setConstraints(constraints)
            .setInputData(Data.Builder().apply {
                putString("uri", file.fileUri)
                putFloat("targetSizeMb", targetSizeMb)
            }.build())
            .setBackoffCriteria(
                androidx.work.BackoffPolicy.LINEAR,
                30L,
                TimeUnit.SECONDS,
            )
            .build()
    }

    val wm = WorkManager.getInstance(context)
    var chain = wm.beginUniqueWork(
        "swipe_compress_${System.currentTimeMillis()}",
        ExistingWorkPolicy.REPLACE,
        compressionWorks.first(),
    )
    compressionWorks.drop(1).forEach { work ->
        chain = chain.then(work)
    }
    chain.enqueue()
}

fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val gb = bytes / (1024.0 * 1024.0 * 1024.0)
    if (gb >= 1) return "%.1f GB".format(gb)
    val mb = bytes / (1024.0 * 1024.0)
    return "${mb.toInt()} MB"
}
