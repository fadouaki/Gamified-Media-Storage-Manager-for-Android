package compress.joshattic.us.swipetoclean.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

object MediaStatus {
    const val UNSEEN = "UNSEEN"
    const val KEPT = "KEPT"
    const val TRASHED = "TRASHED"
    const val PENDING_COMPRESS = "PENDING_COMPRESS"
    const val COMPRESSING = "COMPRESSING"
    const val COMPLETED = "COMPLETED"
}

@Entity(tableName = "media_files")
data class MediaFileEntity(
    @PrimaryKey
    @ColumnInfo(name = "file_uri")
    val fileUri: String,

    @ColumnInfo(name = "status")
    val status: String,

    @ColumnInfo(name = "original_size")
    val originalSize: Long,

    @ColumnInfo(name = "mime_type")
    val mimeType: String,

    @ColumnInfo(name = "display_name")
    val displayName: String?,

    @ColumnInfo(name = "timestamp")
    val timestamp: Long,

    @ColumnInfo(name = "date_added")
    val dateAdded: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "compressed_uri")
    val compressedUri: String? = null,

    @ColumnInfo(name = "compressed_size")
    val compressedSize: Long? = null,

    @ColumnInfo(name = "width")
    val width: Int = 0,

    @ColumnInfo(name = "height")
    val height: Int = 0,
) {
    val isVideo: Boolean get() = mimeType.startsWith("video/")
    val isImage: Boolean get() = mimeType.startsWith("image/")
}
