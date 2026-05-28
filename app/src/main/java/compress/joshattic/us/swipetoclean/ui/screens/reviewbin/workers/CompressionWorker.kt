package compress.joshattic.us.swipetoclean.ui.screens.reviewbin.workers

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import compress.joshattic.us.swipetoclean.R
import compress.joshattic.us.swipetoclean.SwipeToCleanApp
import compress.joshattic.us.swipetoclean.compression.CompressionRequest
import compress.joshattic.us.swipetoclean.compression.CompressionResult
import compress.joshattic.us.swipetoclean.compression.MediaMetadataReader
import compress.joshattic.us.swipetoclean.compression.VideoCompressor
import compress.joshattic.us.swipetoclean.data.MediaStatus
import java.io.File

class CompressionWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val uriStr = inputData.getString("uri") ?: return Result.failure()
        val targetSizeMb = inputData.getFloat("targetSizeMb", 10f)

        val database = (applicationContext as SwipeToCleanApp).database
        val dao = database.mediaFileDao()

        val entity = dao.getByUri(uriStr) ?: return Result.failure()

        // Update status to COMPRESSING
        dao.update(entity.copy(status = MediaStatus.COMPRESSING))
        setForeground(createForegroundInfo("Compressing ${entity.displayName}"))

        val outputDir = File(applicationContext.cacheDir, "compressed_videos")
        outputDir.mkdirs()
        val baseName = entity.displayName?.substringBeforeLast(".") ?: "Compressed"
        val outputFile = File(outputDir, "${baseName}_compressed.mp4")
        if (outputFile.exists()) outputFile.delete()

        val reader = MediaMetadataReader(applicationContext)
        reader.readAll(Uri.parse(uriStr))

        val request = CompressionRequest(
            sourceUri = Uri.parse(uriStr),
            outputFile = outputFile,
            targetSizeMb = targetSizeMb,
            originalSize = entity.originalSize,
            originalWidth = reader.videoWidth,
            originalHeight = reader.videoHeight,
            originalBitrate = reader.videoBitrate,
            originalAudioBitrate = reader.audioBitrate,
            originalFps = reader.videoFps,
            durationMs = reader.durationMs,
            originalVideoMime = reader.videoMime,
        )

        val compressor = VideoCompressor(applicationContext)

        return try {
            val result = compressor.compress(request)

            // Save compressed file to MediaStore
            val savedUri = saveToMediaStore(result)

            // Update Room
            dao.update(
                entity.copy(
                    status = MediaStatus.COMPLETED,
                    compressedUri = savedUri?.toString(),
                    compressedSize = result.outputFileSize,
                )
            )

            Result.success()
        } catch (e: compress.joshattic.us.swipetoclean.compression.CompressionException) {
            // Revert to pending so user can retry
            dao.update(entity.copy(status = MediaStatus.PENDING_COMPRESS))
            if (runAttemptCount < 3) Result.retry()
            else Result.failure()
        } catch (e: Exception) {
            dao.update(entity.copy(status = MediaStatus.PENDING_COMPRESS))
            Result.failure()
        }
    }

    private fun saveToMediaStore(result: CompressionResult): Uri? {
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

        val itemUri = applicationContext.contentResolver.insert(collection, values) ?: return null

        applicationContext.contentResolver.openOutputStream(itemUri)?.use { out ->
            val file = File(result.outputUri.path!!)
            file.inputStream().use { input -> input.copyTo(out) }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.Video.Media.IS_PENDING, 0)
            applicationContext.contentResolver.update(itemUri, values, null, null)
        }

        return itemUri
    }

    private fun createForegroundInfo(progress: String): ForegroundInfo {
        // In production, you'd create a Notification with a progress bar
        // For now, returning a minimal ForegroundInfo
        @Suppress("DEPRECATION")
        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            android.app.Notification.Builder(applicationContext, "compression_channel")
                .setContentTitle("Compressing")
                .setContentText(progress)
                .setSmallIcon(R.mipmap.ic_launcher)
                .build()
        } else {
            @Suppress("DEPRECATION")
            android.app.Notification.Builder(applicationContext)
                .setContentTitle("Compressing")
                .setContentText(progress)
                .setSmallIcon(R.mipmap.ic_launcher)
                .build()
        }
        return ForegroundInfo(1, notification)
    }
}
