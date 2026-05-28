package compress.joshattic.us.swipetoclean.compression

import androidx.media3.common.MimeTypes

/**
 * Calculates bitrate values based on video parameters and target file size.
 *
 * Extracted from CompressorUiState computed properties: minBitrate, minimumSizeMb, targetBitrate.
 */
object BitrateCalculator {

    fun minBitrate(
        targetResolutionHeight: Int,
        originalHeight: Int,
        videoCodec: String,
        targetFps: Int,
        originalFps: Float,
    ): Long {
        val h = if (targetResolutionHeight > 0) targetResolutionHeight else originalHeight
        var base = when {
            h >= 2160 -> 4_000_000L
            h >= 1440 -> 2_500_000L
            h >= 1080 -> 1_500_000L
            h >= 720 -> 1_000_000L
            h >= 480 -> 500_000L
            h >= 360 -> 350_000L
            else -> 200_000L
        }

        if (videoCodec == MimeTypes.VIDEO_H265) {
            base = (base * 0.7).toLong()
        } else if (videoCodec == MimeTypes.VIDEO_AV1) {
            base = (base * 0.6).toLong()
        }

        val fpsVal = if (targetFps > 0) targetFps.toFloat() else originalFps
        val multiplier = if (fpsVal > 45) 1.5f else 1.0f
        return (base * multiplier).toLong()
    }

    fun minimumSizeMb(
        durationMs: Long,
        targetResolutionHeight: Int,
        originalHeight: Int,
        videoCodec: String,
        targetFps: Int,
        originalFps: Float,
        removeAudio: Boolean,
        audioBitrate: Int,
    ): Float {
        if (durationMs <= 0) return 0.1f
        val seconds = durationMs / 1000f
        val audioBits = if (removeAudio) 0f else {
            val rate = if (audioBitrate == 0) 256_000f else audioBitrate.toFloat()
            rate * seconds
        }
        val minBits = minBitrate(targetResolutionHeight, originalHeight, videoCodec, targetFps, originalFps) * seconds
        val totalBits = minBits + audioBits
        return (totalBits / 8f) / (1024f * 1024f)
    }

    fun targetBitrate(
        targetSizeMb: Float,
        durationMs: Long,
        targetResolutionHeight: Int,
        originalHeight: Int,
        videoCodec: String,
        targetFps: Int,
        originalFps: Float,
        originalBitrate: Int,
        removeAudio: Boolean,
        audioBitrate: Int,
    ): Int {
        val durationSec = if (durationMs > 0) durationMs / 1000.0 else return 2_000_000

        val targetBits = targetSizeMb * 8 * 1024 * 1024

        val audioBits = if (removeAudio) 0.0 else {
            val rate = if (audioBitrate == 0) 256_000.0 else audioBitrate.toDouble()
            rate * durationSec
        }

        val overheadBits = (targetBits * 0.02) + (50 * 1024 * 8)
        var availableVideoBits = targetBits - audioBits - overheadBits
        availableVideoBits = availableVideoBits.coerceAtLeast(targetBits * 0.1)

        val calculated = (availableVideoBits / durationSec).toLong()
        val min = minBitrate(targetResolutionHeight, originalHeight, videoCodec, targetFps, originalFps)
        val original = if (originalBitrate > 0) originalBitrate.toLong() else Long.MAX_VALUE

        return calculated.coerceAtLeast(min).coerceAtMost(original).toInt()
    }
}
