package compress.joshattic.us.swipetoclean.compression

import android.media.MediaCodecList
import android.os.Build
import androidx.media3.common.MimeTypes

class CodecDetector {

    val supportedCodecs: List<String> by lazy { detectSupportedCodecs() }

    fun hasEncoder(mimeType: String): Boolean {
        return try {
            val list = MediaCodecList(MediaCodecList.ALL_CODECS)
            for (info in list.codecInfos) {
                if (!info.isEncoder) continue

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    if (info.isSoftwareOnly) continue
                } else {
                    val name = info.name.lowercase()
                    if (name.startsWith("c2.android")) continue
                }

                if (info.supportedTypes.any { it.equals(mimeType, ignoreCase = true) }) {
                    return true
                }
            }
            false
        } catch (_: Exception) {
            false
        }
    }

    private fun detectSupportedCodecs(): List<String> {
        val supported = mutableListOf<String>()
        supported.add(MimeTypes.VIDEO_H264)
        if (hasEncoder(MimeTypes.VIDEO_H265)) supported.add(MimeTypes.VIDEO_H265)
        if (hasEncoder(MimeTypes.VIDEO_AV1)) supported.add(MimeTypes.VIDEO_AV1)
        return supported
    }
}
