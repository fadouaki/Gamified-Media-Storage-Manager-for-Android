package compress.joshattic.us.swipetoclean.compression

import android.content.Context
import android.media.MediaCodecList
import androidx.media3.common.MimeTypes

internal data class CompressionPlan(
    val outputVideoMimeType: String,
    val outputHeight: Int,
    val outputFps: Int,
    val warnings: List<String>,
    val blockingError: String?,
)

internal class CompressionPlanner(private val context: Context) {

    fun buildPlan(request: CompressionRequest): CompressionPlan {
        var outputMime = request.videoCodec
        var outputHeight = request.targetResolutionHeight
        var outputFps = request.targetFps
        val warnings = mutableListOf<String>()

        // If metadata wasn't pre-loaded, read it now
        val reader = MediaMetadataReader(context)
        if (request.originalVideoMime == null && request.originalWidth == 0) {
            reader.readAll(request.sourceUri)
        }

        val sourceMime = request.originalVideoMime ?: reader.videoMime
        val sourceWidth = if (request.originalWidth > 0) request.originalWidth else reader.videoWidth
        val sourceHeight = if (request.originalHeight > 0) request.originalHeight else reader.videoHeight
        val sourceFps = if (request.originalFps > 0f) request.originalFps else reader.videoFps

        // Decoder check
        if (!sourceMime.isNullOrBlank() && sourceWidth > 0 && sourceHeight > 0) {
            val decoderSupported = isCodecConfigurationSupported(
                mimeType = sourceMime,
                width = sourceWidth,
                height = sourceHeight,
                fps = sourceFps,
                encoder = false,
            )
            if (!decoderSupported) {
                return CompressionPlan(
                    outputVideoMimeType = outputMime,
                    outputHeight = outputHeight,
                    outputFps = outputFps,
                    warnings = warnings,
                    blockingError = "Device cannot decode source video: ${sourceWidth}x${sourceHeight} @ ${sourceFps}fps ${sourceMime.substringAfter("/")}",
                )
            }
        }

        val attemptedConfigs = mutableListOf<String>()

        fun isOutputSupported(mime: String, height: Int, fps: Int): Boolean {
            val safeHeight = if (height > 0) height else request.originalHeight
            val safeFps = if (fps > 0) fps else request.originalFps.toInt()
            val aspectRatio = if (request.originalHeight > 0)
                request.originalWidth.toFloat() / request.originalHeight else 16f / 9f
            var outputWidth = (safeHeight * aspectRatio).toInt().coerceAtLeast(2)
            var actualHeight = safeHeight.coerceAtLeast(2)
            if (outputWidth % 2 != 0) outputWidth -= 1
            if (actualHeight % 2 != 0) actualHeight -= 1
            attemptedConfigs.add("${mime.substringAfter("/")} ${actualHeight}p@${safeFps}fps")
            return isCodecConfigurationSupported(
                mimeType = mime,
                width = outputWidth,
                height = actualHeight,
                fps = safeFps.toFloat(),
                encoder = true,
            )
        }

        if (!isOutputSupported(outputMime, outputHeight, outputFps)) {
            if (outputMime != MimeTypes.VIDEO_H264 &&
                isOutputSupported(MimeTypes.VIDEO_H264, outputHeight, outputFps)) {
                outputMime = MimeTypes.VIDEO_H264
                warnings.add("Falling back to H.264 (H.265/AV1 not supported for this resolution)")
            } else {
                val fallbackHeights = listOf(1080, 720, 540, 480)
                    .filter { it in 2..request.originalHeight }
                    .ifEmpty { listOf(request.originalHeight.coerceAtLeast(2)) }
                val fallbackFps = listOf(30, 24)
                var supported = false

                for (h in fallbackHeights) {
                    for (f in fallbackFps) {
                        if (isOutputSupported(MimeTypes.VIDEO_H264, h, f)) {
                            outputMime = MimeTypes.VIDEO_H264
                            outputHeight = h
                            outputFps = f
                            warnings.add("Reduced to ${h}p@${f}fps for device compatibility")
                            supported = true
                            break
                        }
                    }
                    if (supported) break
                }

                if (!supported) {
                    return CompressionPlan(
                        outputVideoMimeType = outputMime,
                        outputHeight = outputHeight,
                        outputFps = outputFps,
                        warnings = warnings,
                        blockingError = "No supported encoder configuration found. Tried: ${attemptedConfigs.joinToString(", ")}",
                    )
                }
            }
        }

        return CompressionPlan(outputMime, outputHeight, outputFps, warnings, null)
    }

    private fun isCodecConfigurationSupported(
        mimeType: String, width: Int, height: Int, fps: Float, encoder: Boolean,
    ): Boolean {
        return try {
            val codecList = MediaCodecList(MediaCodecList.ALL_CODECS)
            val safeFps = kotlin.math.ceil(if (fps > 0f) fps.toDouble() else 30.0)
            codecList.codecInfos
                .asSequence()
                .filter { it.isEncoder == encoder }
                .filter { info -> info.supportedTypes.any { it.equals(mimeType, ignoreCase = true) } }
                .any { info ->
                    try {
                        val capabilities = info.getCapabilitiesForType(mimeType)
                        val videoCaps = capabilities.videoCapabilities ?: return@any false
                        videoCaps.areSizeAndRateSupported(width, height, safeFps) ||
                            videoCaps.areSizeAndRateSupported(height, width, safeFps)
                    } catch (_: Exception) { false }
                }
        } catch (_: Exception) { false }
    }
}
