package compress.joshattic.us.swipetoclean.ui.screens.dashboard

import android.os.Environment
import android.os.StatFs
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Swipe
import androidx.compose.material.icons.filled.SwipeRight
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.background
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import compress.joshattic.us.swipetoclean.ui.theme.CompressBlue
import compress.joshattic.us.swipetoclean.ui.theme.KeepGreen
import compress.joshattic.us.swipetoclean.ui.theme.TrashRed
import compress.joshattic.us.swipetoclean.data.AppDatabase
import compress.joshattic.us.swipetoclean.data.MediaFileEntity
import compress.joshattic.us.swipetoclean.data.MediaStatus
import compress.joshattic.us.swipetoclean.data.MediaStoreRepository
import compress.joshattic.us.swipetoclean.R

@Composable
fun DashboardScreen(
    database: AppDatabase,
    onStartSwiping: () -> Unit,
    onEmptyTrash: () -> Unit,
    onCompressVideos: () -> Unit,
) {
    val dao = database.mediaFileDao()

    val unseenCount by dao.getUnseenCount().collectAsState(initial = 0)
    val keptCount by dao.getKeptCount().collectAsState(initial = 0)
    val binnedCount by dao.getBinnedCount().collectAsState(initial = 0)
    val trashedCount by dao.getTrashedCount().collectAsState(initial = 0)
    val compressCount by dao.getPendingCompressCount().collectAsState(initial = 0)
    val totalSavedBytes by dao.getTotalSavedBytes().collectAsState(initial = 0L)

    val reviewedCount = keptCount

    val context = LocalContext.current

    // Scan device media into database (insertAll IGNOREs duplicates)
    LaunchedEffect(Unit) {
        val repo = MediaStoreRepository(context)
        val results = repo.queryAllMedia()
        val entities = results.map { r ->
            MediaFileEntity(
                fileUri = r.uri.toString(),
                status = MediaStatus.UNSEEN,
                originalSize = r.size,
                mimeType = r.mimeType,
                displayName = r.displayName,
                timestamp = r.dateModified,
                width = r.width,
                height = r.height,
            )
        }
        if (entities.isNotEmpty()) {
            dao.insertAll(entities)
        }
    }

    val (totalBytes, usedBytes, freeBytes) = getStorageStats()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF1A1A2E),
                        Color(0xFF16213E),
                        Color(0xFF0F3460),
                    ),
                    center = Offset(400f, 800f),
                    radius = 1200f,
                )
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Dashboard",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Storage ring chart
            StorageRingChart(
                usedBytes = usedBytes,
                totalBytes = totalBytes,
                modifier = Modifier.size(180.dp),
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = formatBytes(usedBytes) + " of " + formatBytes(totalBytes) + " used",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.7f),
            )

            Spacer(modifier = Modifier.height(24.dp))

        // Quick Stats Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            StatCard(
                title = "Reviewed",
                value = "$reviewedCount",
                modifier = Modifier.weight(1f),
                containerColor = Color.White.copy(alpha = 0.05f),
                contentColor = Color.White,
                icon = { Icon(Icons.Default.Swipe, contentDescription = "Reviewed files icon", modifier = Modifier.size(24.dp), tint = Color.White.copy(alpha = 0.7f)) },
            )
            StatCard(
                title = "Space Saved",
                value = formatBytes(totalSavedBytes),
                modifier = Modifier.weight(1f),
                containerColor = CompressBlue.copy(alpha = 0.15f),
                contentColor = Color.White,
                icon = { Icon(Icons.Outlined.Folder, contentDescription = "Saved space icon", modifier = Modifier.size(24.dp), tint = CompressBlue) },
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Hero Action 1: Start Swiping
        ActionCard(
            title = "Start Swiping",
            subtitle = if (unseenCount > 0) "$unseenCount items waiting" else "All caught up!",
            icon = Icons.Default.SwipeRight,
            onClick = onStartSwiping,
            enabled = unseenCount > 0,
            gradientColors = listOf(Color(0xFFE94560), Color(0xFFC81D3E)),
            iconTint = Color.White
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Action 2: Empty Trash
            ActionCard(
                title = "Empty Trash",
                subtitle = if (trashedCount > 0) "$trashedCount items" else "Empty",
                icon = Icons.Default.Delete,
                onClick = onEmptyTrash,
                enabled = trashedCount > 0,
                gradientColors = listOf(Color.White.copy(alpha = 0.1f), Color.White.copy(alpha = 0.05f)),
                iconTint = if (trashedCount > 0) TrashRed else Color.White.copy(alpha = 0.3f),
                showBorder = true,
                modifier = Modifier.weight(1f)
            )

            // Action 3: Compress Videos
            ActionCard(
                title = "Compress",
                subtitle = if (compressCount > 0) "$compressCount items" else "Done",
                icon = Icons.Default.AutoAwesome,
                onClick = onCompressVideos,
                enabled = compressCount > 0,
                gradientColors = listOf(Color.White.copy(alpha = 0.1f), Color.White.copy(alpha = 0.05f)),
                iconTint = if (compressCount > 0) CompressBlue else Color.White.copy(alpha = 0.3f),
                showBorder = true,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}
}

@Composable
fun ActionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    enabled: Boolean,
    gradientColors: List<Color>,
    iconTint: Color,
    showBorder: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val alpha = if (enabled) 1f else 0.5f
    val borderStroke = if (showBorder) androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)) else null

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        border = borderStroke,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.horizontalGradient(gradientColors.map { it.copy(alpha = it.alpha * alpha) }))
                .padding(24.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = alpha)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.7f * alpha)
                    )
                }
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White.copy(alpha = 0.2f * alpha)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = iconTint.copy(alpha = iconTint.alpha * alpha),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun StorageRingChart(usedBytes: Long, totalBytes: Long, modifier: Modifier = Modifier) {
    val usedFraction = if (totalBytes > 0) usedBytes.toFloat() / totalBytes else 0f
    val usedColor = when {
        usedFraction > 0.9f -> Color(0xFFF44336)
        usedFraction > 0.7f -> Color(0xFFFF9800)
        else -> MaterialTheme.colorScheme.primary
    }
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant

    Box(contentAlignment = Alignment.Center, modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 16.dp.toPx()
            val radius = (size.minDimension - strokeWidth) / 2

            // Background arc (remaining space)
            drawArc(
                color = surfaceVariant,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(
                    strokeWidth / 2, strokeWidth / 2
                ),
                size = Size(size.width - strokeWidth, size.height - strokeWidth),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )

            // Used space arc
            drawArc(
                color = usedColor,
                startAngle = -90f,
                sweepAngle = 360f * usedFraction,
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(
                    strokeWidth / 2, strokeWidth / 2
                ),
                size = Size(size.width - strokeWidth, size.height - strokeWidth),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "${(usedFraction * 100).toInt()}%",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
            Text(
                text = "used",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
fun StatCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    icon: @Composable () -> Unit,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            icon()
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = contentColor.copy(alpha = 0.8f),
            )
        }
    }

}

fun getStorageStats(): Triple<Long, Long, Long> {
    val stat = try {
        StatFs(Environment.getExternalStorageDirectory().absolutePath)
    } catch (_: Exception) {
        return Triple(128L * 1024 * 1024 * 1024, 64L * 1024 * 1024 * 1024, 64L * 1024 * 1024 * 1024)
    }
    val total = stat.totalBytes
    val free = stat.availableBytes
    val used = total - free
    return Triple(total, used, free)
}

fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val gb = bytes / (1024.0 * 1024.0 * 1024.0)
    if (gb >= 1) return "%.1f GB".format(gb)
    val mb = bytes / (1024.0 * 1024.0)
    if (mb >= 1) return "${mb.toInt()} MB"
    return "${bytes / 1024} KB"
}
