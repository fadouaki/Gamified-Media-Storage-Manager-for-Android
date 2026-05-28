package compress.joshattic.us.swipetoclean.compression

import android.content.Context
import android.media.MediaCodecInfo
import android.net.Uri
import android.os.Build
import androidx.annotation.OptIn
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.FrameDropEffect
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultDecoderFactory
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import androidx.media3.transformer.AudioEncoderSettings
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.MainScope

@OptIn(UnstableApi::class)
class VideoCompressor(private val context: Context) {

    private var activeTransformer: Transformer? = null
    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress

    fun cancel() {
        activeTransformer?.cancel()
    }

    suspend fun compress(request: CompressionRequest): CompressionResult {
        _progress.value = 0f

        // 1. Build compression plan (IO)
        val plan = withContext(Dispatchers.IO) {
            val planner = CompressionPlanner(context)
            planner.buildPlan(request)
        }

        if (plan.blockingError != null) {
            throw CompressionException(null, plan.blockingError, "")
        }

        // 2. Ensure output directory (IO)
        withContext(Dispatchers.IO) {
            request.outputFile.parentFile?.mkdirs()
            if (request.outputFile.exists()) request.outputFile.delete()
        }

        // 3. Calculate bitrate
        val bitrate = BitrateCalculator.targetBitrate(
            targetSizeMb = request.targetSizeMb,
            durationMs = request.durationMs,
            targetResolutionHeight = request.targetResolutionHeight,
            originalHeight = request.originalHeight,
            videoCodec = request.videoCodec,
            targetFps = request.targetFps,
            originalFps = request.originalFps,
            originalBitrate = request.originalBitrate,
            removeAudio = request.removeAudio,
            audioBitrate = request.audioBitrate,
        )

        val audioBitrateToUse = if (request.audioBitrate == 0) {
            if (request.originalAudioBitrate > 0) request.originalAudioBitrate else 256_000
        } else {
            request.audioBitrate
        }

        // 4. Build effects
        val effectsList = mutableListOf<Effect>()

        if (plan.outputHeight > 0 && plan.outputHeight != request.originalHeight) {
            val aspectRatio = if (request.originalHeight > 0)
                request.originalWidth.toFloat() / request.originalHeight else 16f / 9f
            var width = (plan.outputHeight * aspectRatio).toInt()
            var height = plan.outputHeight
            if (width % 2 != 0) width -= 1
            if (height % 2 != 0) height -= 1
            if (width > 0 && height > 0) {
                effectsList.add(
                    Presentation.createForWidthAndHeight(width, height, Presentation.LAYOUT_SCALE_TO_FIT)
                )
            }
        }

        if (plan.outputFps > 0 && request.originalFps > 0 &&
            plan.outputFps.toFloat() < request.originalFps
        ) {
            effectsList.add(
                FrameDropEffect.createSimpleFrameDropEffect(
                    request.originalFps, plan.outputFps.toFloat()
                )
            )
        }

        // 5. Build composition
        val mediaItem = MediaItem.fromUri(request.sourceUri)
        val editedMediaItem = EditedMediaItem.Builder(mediaItem)
            .setEffects(Effects(emptyList(), effectsList))
            .setRemoveAudio(request.removeAudio)
            .build()

        var hdrMode = Composition.HDR_MODE_KEEP_HDR
        if (Build.MANUFACTURER.equals("Google", ignoreCase = true) &&
            Build.MODEL.contains("Pixel 10")
        ) {
            if (plan.outputVideoMimeType == MimeTypes.VIDEO_H265 ||
                plan.outputVideoMimeType == MimeTypes.VIDEO_H264
            ) {
                val reader = MediaMetadataReader(context)
                if (reader.isHdr(request.sourceUri)) {
                    hdrMode = Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL
                }
            }
        }

        val composition = Composition.Builder(
            listOf(EditedMediaItemSequence(editedMediaItem))
        ).setHdrMode(hdrMode).build()

        // 6. Execute on Main Thread (Required for Transformer)
        return withContext(Dispatchers.Main) {
            val encoderFactory = buildEncoderFactory(bitrate, audioBitrateToUse)

            val transformer = Transformer.Builder(context)
                .setVideoMimeType(plan.outputVideoMimeType)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .setAssetLoaderFactory(
                    androidx.media3.transformer.DefaultAssetLoaderFactory(
                        context,
                        DefaultDecoderFactory.Builder(context)
                            .setEnableDecoderFallback(true)
                            .build(),
                        androidx.media3.common.util.Clock.DEFAULT,
                    )
                )
                .setEncoderFactory(encoderFactory)
                .build()

            activeTransformer = transformer

            suspendCancellableCoroutine { continuation ->
                val progressJob = launch {
                    while (continuation.isActive) {
                        val holder = ProgressHolder()
                        val state = transformer.getProgress(holder)
                        if (state != Transformer.PROGRESS_STATE_NOT_STARTED) {
                            _progress.value = holder.progress / 100f
                        }
                        delay(200)
                    }
                }

                transformer.addListener(object : Transformer.Listener {
                    override fun onCompleted(comp: Composition, exportResult: ExportResult) {
                        progressJob.cancel()
                        val finalSize = request.outputFile.length()
                        val uri = Uri.fromFile(request.outputFile)
                        activeTransformer = null
                        continuation.resume(
                            CompressionResult(
                                outputFileSize = finalSize,
                                outputUri = uri,
                                originalSize = request.originalSize,
                                warnings = plan.warnings,
                            )
                        )
                    }

                    override fun onError(comp: Composition, exportResult: ExportResult, exportException: ExportException) {
                        progressJob.cancel()
                        activeTransformer = null
                        val msg = when (exportException.errorCode) {
                            ExportException.ERROR_CODE_DECODER_INIT_FAILED ->
                                "Device cannot decode this video format"
                            ExportException.ERROR_CODE_ENCODER_INIT_FAILED ->
                                "Device cannot encode this configuration"
                            ExportException.ERROR_CODE_MUXING_FAILED ->
                                "Failed to create output file"
                            else -> exportException.localizedMessage ?: "Unknown compression error"
                        }
                        continuation.resumeWithException(
                            CompressionException(
                                errorCode = exportException.errorCode,
                                userMessage = msg,
                                stackTrace = exportException.stackTraceToString(),
                            )
                        )
                    }
                })

                try {
                    transformer.start(composition, request.outputFile.absolutePath)
                } catch (e: Exception) {
                    progressJob.cancel()
                    continuation.resumeWithException(e)
                }

                continuation.invokeOnCancellation {
                    progressJob.cancel()
                    transformer.cancel()
                }
            }
        }
    }

    private fun buildEncoderFactory(
        targetBitrate: Int,
        audioBitrateToUse: Int,
    ): androidx.media3.transformer.Codec.EncoderFactory {
        val cbr = DefaultEncoderFactory.Builder(context)
            .setEnableFallback(true)
            .setRequestedVideoEncoderSettings(
                VideoEncoderSettings.Builder()
                    .setBitrate(targetBitrate)
                    .setBitrateMode(MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR)
                    .build()
            )
            .setRequestedAudioEncoderSettings(
                AudioEncoderSettings.Builder()
                    .setBitrate(audioBitrateToUse)
                    .build()
            )
            .build()

        val vbr = DefaultEncoderFactory.Builder(context)
            .setEnableFallback(true)
            .setRequestedVideoEncoderSettings(
                VideoEncoderSettings.Builder()
                    .setBitrate(targetBitrate)
                    .setBitrateMode(MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR)
                    .build()
            )
            .setRequestedAudioEncoderSettings(
                AudioEncoderSettings.Builder()
                    .setBitrate(audioBitrateToUse)
                    .build()
            )
            .build()

        return object : androidx.media3.transformer.Codec.EncoderFactory {
            override fun createForAudioEncoding(format: androidx.media3.common.Format) =
                cbr.createForAudioEncoding(format)

            override fun createForVideoEncoding(format: androidx.media3.common.Format): androidx.media3.transformer.Codec {
                var modifiedFormat = format
                if (format.colorInfo == null ||
                    !androidx.media3.common.ColorInfo.isTransferHdr(format.colorInfo)
                ) {
                    modifiedFormat = format.buildUpon().setColorInfo(null).build()
                }
                return try {
                    cbr.createForVideoEncoding(modifiedFormat)
                } catch (_: Exception) {
                    vbr.createForVideoEncoding(modifiedFormat)
                }
            }

            override fun audioNeedsEncoding() = cbr.audioNeedsEncoding()
            override fun videoNeedsEncoding() = cbr.videoNeedsEncoding()
        }
    }
}
