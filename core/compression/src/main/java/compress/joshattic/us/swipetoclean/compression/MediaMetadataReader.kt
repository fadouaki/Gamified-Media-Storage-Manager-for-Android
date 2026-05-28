package compress.joshattic.us.swipetoclean.compression

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build

data class VideoTrackInfo(
    val mimeType: String?,
    val width: Int,
    val height: Int,
    val frameRate: Float,
)

class MediaMetadataReader(private val context: Context) {

    var videoWidth: Int = 0
        private set
    var videoHeight: Int = 0
        private set
    var videoBitrate: Int = 0
        private set
    var videoFps: Float = 30f
        private set
    var audioBitrate: Int = 0
        private set
    var durationMs: Long = 0L
        private set
    var videoMime: String? = null
        private set

    fun readAll(uri: Uri) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)

            videoWidth = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                ?.toIntOrNull() ?: 0
            videoHeight = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?.toIntOrNull() ?: 0

            val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                ?.toIntOrNull() ?: 0
            if (rotation == 90 || rotation == 270) {
                val temp = videoWidth
                videoWidth = videoHeight
                videoHeight = temp
            }

            videoBitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
                ?.toIntOrNull() ?: 0
            durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L

            val fpsStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)
            videoFps = fpsStr?.toFloatOrNull() ?: 0f

            val videoInfo = getVideoTrackInfo(uri)
            videoMime = videoInfo?.mimeType
            if (videoFps <= 0f && videoInfo != null && videoInfo.frameRate > 0f) {
                videoFps = videoInfo.frameRate
            }
            if (videoFps <= 0f) videoFps = 30f

            audioBitrate = getAudioBitrate(uri)
        } catch (_: Exception) {
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }
    }

    fun getFileSize(uri: Uri): Long {
        return try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { fd ->
                fd.statSize
            } ?: 0L
        } catch (_: Exception) {
            0L
        }
    }

    private fun getVideoTrackInfo(uri: Uri): VideoTrackInfo? {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, uri, null)
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME)
                if (mime?.startsWith("video/") == true) {
                    val w = if (format.containsKey(MediaFormat.KEY_WIDTH))
                        format.getInteger(MediaFormat.KEY_WIDTH) else 0
                    val h = if (format.containsKey(MediaFormat.KEY_HEIGHT))
                        format.getInteger(MediaFormat.KEY_HEIGHT) else 0
                    var fr = 0f
                    if (format.containsKey(MediaFormat.KEY_FRAME_RATE)) {
                        try { fr = format.getInteger(MediaFormat.KEY_FRAME_RATE).toFloat() }
                        catch (_: Exception) {
                            try { fr = format.getFloat(MediaFormat.KEY_FRAME_RATE) }
                            catch (_: Exception) {}
                        }
                    }
                    return VideoTrackInfo(mime, w, h, fr)
                }
            }
        } catch (_: Exception) {
        } finally {
            extractor.release()
        }
        return null
    }

    private fun getAudioBitrate(uri: Uri): Int {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, uri, null)
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME)
                if (mime?.startsWith("audio/") == true) {
                    if (format.containsKey(MediaFormat.KEY_BIT_RATE)) {
                        return format.getInteger(MediaFormat.KEY_BIT_RATE)
                    }
                }
            }
        } catch (_: Exception) {
        } finally {
            extractor.release()
        }
        return 0
    }

    fun isHdr(uri: Uri): Boolean {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            if (Build.VERSION.SDK_INT >= 30) {
                val transfer = retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_COLOR_TRANSFER
                )
                return transfer == "6" || transfer == "7"
            }
        } catch (_: Exception) {
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }
        return false
    }
}
