package compress.joshattic.us.swipetoclean.compression

import android.net.Uri
import androidx.media3.common.MimeTypes
import java.io.File

data class CompressionRequest(
    val sourceUri: Uri,
    val outputFile: File,
    val targetSizeMb: Float = 10f,
    val videoCodec: String = MimeTypes.VIDEO_H265,
    val targetResolutionHeight: Int = 0,
    val targetFps: Int = 0,
    val removeAudio: Boolean = false,
    val audioBitrate: Int = 128_000,
    val audioVolume: Float = 1.0f,
    val originalSize: Long = 0,
    val originalWidth: Int = 0,
    val originalHeight: Int = 0,
    val originalBitrate: Int = 0,
    val originalAudioBitrate: Int = 0,
    val originalFps: Float = 30f,
    val durationMs: Long = 0,
    val originalVideoMime: String? = null,
) {

    /**
     * Returns a copy with metadata read from the source video.
     */
    fun withMetadata(reader: MediaMetadataReader): CompressionRequest {
        return copy(
            originalSize = reader.getFileSize(sourceUri),
            originalWidth = reader.videoWidth,
            originalHeight = reader.videoHeight,
            originalBitrate = reader.videoBitrate,
            originalAudioBitrate = reader.audioBitrate,
            originalFps = reader.videoFps,
            durationMs = reader.durationMs,
            originalVideoMime = reader.videoMime,
        )
    }
}

data class CompressionResult(
    val outputFileSize: Long,
    val outputUri: Uri,
    val originalSize: Long,
    val warnings: List<String> = emptyList(),
) {
    val bytesSaved: Long get() = maxOf(0, originalSize - outputFileSize)
    val reductionPercent: Int get() =
        if (originalSize > 0) ((1f - outputFileSize.toFloat() / originalSize) * 100).toInt() else 0
}

class CompressionException(
    val errorCode: Int?,
    val userMessage: String,
    stackTrace: String,
) : Exception(userMessage) {
    val errorLog: String = stackTrace
}
