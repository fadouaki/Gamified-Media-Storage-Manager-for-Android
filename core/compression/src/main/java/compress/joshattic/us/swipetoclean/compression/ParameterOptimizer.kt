package compress.joshattic.us.swipetoclean.compression

/**
 * Iteratively adjusts compression parameters to fit within a target file size.
 *
 * Extracted from CompressorUiState.autoAdjust().
 */
object ParameterOptimizer {

    data class Params(
        val targetResolutionHeight: Int,
        val targetFps: Int,
        val audioBitrate: Int,
        val removeAudio: Boolean,
    )

    /**
     * Adjusts parameters downward (reducing quality) to meet targetMb,
     * then upward (increasing quality) if there is headroom.
     */
    fun optimize(
        targetMb: Float,
        originalHeight: Int,
        originalFps: Float,
        originalAudioBitrate: Int,
        durationMs: Long,
        videoCodec: String,
        initial: Params,
        lockAudioBitrate: Boolean = false,
        allowUpward: Boolean = true,
    ): Params {
        var state = initial.copy()
        var attempts = 0
        val maxAttempts = 20

        // Downward adjustment
        while (attempts < maxAttempts) {
            val minMb = BitrateCalculator.minimumSizeMb(
                durationMs, state.targetResolutionHeight, originalHeight,
                videoCodec, state.targetFps, originalFps,
                state.removeAudio, state.audioBitrate,
            )
            if (minMb <= targetMb) break
            attempts++

            val effectiveFps = if (state.targetFps > 0) state.targetFps else originalFps.toInt()

            if (effectiveFps > 30) {
                state = state.copy(targetFps = 30)
                continue
            }

            if (!lockAudioBitrate) {
                if (state.audioBitrate > 128_000) {
                    state = state.copy(audioBitrate = 128_000)
                    continue
                }
                if (state.audioBitrate > 64_000 && minMb > targetMb * 1.5) {
                    state = state.copy(audioBitrate = 64_000)
                    continue
                }
            }

            val currentH = if (state.targetResolutionHeight > 0) state.targetResolutionHeight else originalHeight
            val newH = when {
                currentH > 2160 -> 2160
                currentH > 1440 -> 1440
                currentH > 1080 -> 1080
                currentH > 720 -> 720
                currentH > 480 -> 480
                currentH > 360 -> 360
                else -> 240
            }
            if (newH < currentH) {
                state = state.copy(targetResolutionHeight = newH)
                continue
            }

            if (effectiveFps > 24) {
                state = state.copy(targetFps = 24)
                continue
            }

            break
        }

        // Upward adjustment
        if (allowUpward) {
            attempts = 0
            while (attempts < maxAttempts) {
                attempts++
                var changed = false

                val currentH = if (state.targetResolutionHeight > 0) state.targetResolutionHeight else originalHeight
                if (currentH < originalHeight) {
                    val nextH = when {
                        currentH < 360 -> 360
                        currentH < 480 -> 480
                        currentH < 720 -> 720
                        currentH < 1080 -> 1080
                        currentH < 1440 -> 1440
                        currentH < 2160 -> 2160
                        else -> originalHeight
                    }.coerceAtMost(originalHeight)

                    val useOriginal = nextH >= originalHeight
                    val testParams = state.copy(targetResolutionHeight = if (useOriginal) 0 else nextH)
                    if (BitrateCalculator.minimumSizeMb(
                            durationMs, testParams.targetResolutionHeight, originalHeight,
                            videoCodec, testParams.targetFps, originalFps,
                            testParams.removeAudio, testParams.audioBitrate,
                        ) <= targetMb
                    ) {
                        state = testParams
                        changed = true
                        continue
                    }
                }

                val currentFps = if (state.targetFps > 0) state.targetFps else originalFps.toInt()
                if (currentFps < originalFps.toInt()) {
                    val nextFps = if (currentFps < 30) 30 else originalFps.toInt()
                    val useOriginal = nextFps >= originalFps.toInt()
                    val testParams = state.copy(targetFps = if (useOriginal) 0 else nextFps)
                    if (BitrateCalculator.minimumSizeMb(
                            durationMs, testParams.targetResolutionHeight, originalHeight,
                            videoCodec, testParams.targetFps, originalFps,
                            testParams.removeAudio, testParams.audioBitrate,
                        ) <= targetMb
                    ) {
                        state = testParams
                        changed = true
                        continue
                    }
                }

                if (!lockAudioBitrate) {
                    val maxAudio = if (originalAudioBitrate > 0) originalAudioBitrate else 320_000
                    if (state.audioBitrate < maxAudio) {
                        val nextAudio = when {
                            state.audioBitrate < 64_000 -> 64_000
                            state.audioBitrate < 128_000 -> 128_000
                            state.audioBitrate < 192_000 -> 192_000
                            state.audioBitrate < 320_000 -> 320_000
                            else -> maxAudio
                        }.coerceAtMost(maxAudio)
                        val testParams = state.copy(audioBitrate = nextAudio)
                        if (BitrateCalculator.minimumSizeMb(
                                durationMs, testParams.targetResolutionHeight, originalHeight,
                                videoCodec, testParams.targetFps, originalFps,
                                testParams.removeAudio, testParams.audioBitrate,
                            ) <= targetMb
                        ) {
                            state = testParams
                            changed = true
                            continue
                        }
                    }
                }

                if (!changed) break
            }
        }

        return state
    }
}
