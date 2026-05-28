package compress.joshattic.us.swipetoclean.data

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.IntentSender
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import androidx.core.database.getLongOrNull
import androidx.core.database.getStringOrNull

class MediaStoreRepository(private val context: Context) {

    private val contentResolver: ContentResolver get() = context.contentResolver

    data class QueryResult(
        val uri: Uri,
        val displayName: String?,
        val size: Long,
        val mimeType: String,
        val dateModified: Long,
        val width: Int = 0,
        val height: Int = 0,
    )

    suspend fun queryAllMedia(limit: Int = Int.MAX_VALUE): List<QueryResult> {
        val results = mutableListOf<QueryResult>()
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.DATE_MODIFIED,
            MediaStore.Files.FileColumns.WIDTH,
            MediaStore.Files.FileColumns.HEIGHT,
            MediaStore.Files.FileColumns.MEDIA_TYPE,
        )

        val selection = "${MediaStore.Files.FileColumns.MEDIA_TYPE} IN (?, ?)"
        val args = arrayOf(
            MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
            MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString(),
        )

        val sortOrder = "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"

        contentResolver.query(
            MediaStore.Files.getContentUri("external"),
            projection,
            selection,
            args,
            sortOrder,
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
            val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)
            val widthCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.WIDTH)
            val heightCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.HEIGHT)
            val typeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)

            while (cursor.moveToNext() && results.size < limit) {
                val id = cursor.getLong(idCol)
                val name = cursor.getString(nameCol)
                val size = cursor.getLongOrNull(sizeCol) ?: 0L
                val mime = cursor.getString(mimeCol) ?: "application/octet-stream"
                val date = cursor.getLongOrNull(dateCol) ?: 0L
                val width = cursor.getLongOrNull(widthCol)?.toInt() ?: 0
                val height = cursor.getLongOrNull(heightCol)?.toInt() ?: 0
                val mediaType = cursor.getInt(typeCol)

                val baseUri = if (mediaType == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO) {
                    MediaStore.Video.Media.getContentUri("external")
                } else {
                    MediaStore.Images.Media.getContentUri("external")
                }

                val uri = ContentUris.withAppendedId(baseUri, id)

                results.add(
                    QueryResult(uri, name, size, mime, date, width, height)
                )
            }
        }
        return results
    }

    suspend fun deleteFiles(uris: List<String>): Result<Int> {
        return try {
            var count = 0
            for (uriStr in uris) {
                val uri = Uri.parse(uriStr)
                val deleted = contentResolver.delete(uri, null, null)
                if (deleted > 0) count++
            }
            Result.success(count)
        } catch (e: SecurityException) {
            Result.failure(e)
        }
    }

    /**
     * Creates a system delete request intent sender for API 30+.
     * Returns null if the API level doesn't support it.
     */
    fun createDeleteRequest(uris: List<Uri>): IntentSender? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        return MediaStore.createDeleteRequest(contentResolver, uris).intentSender
    }

    fun loadThumbnail(uri: Uri, size: Size): Bitmap? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentResolver.loadThumbnail(uri, size, null)
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Images.Thumbnails.getThumbnail(
                    contentResolver,
                    ContentUris.parseId(uri),
                    MediaStore.Images.Thumbnails.MINI_KIND,
                    null,
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    fun getMimeType(uri: Uri): String? {
        return contentResolver.getType(uri)
    }
}

internal object ContentUris {
    fun withAppendedId(uri: Uri, id: Long): Uri {
        return Uri.withAppendedPath(uri, id.toString())
    }

    fun parseId(uri: Uri): Long {
        return uri.lastPathSegment?.toLongOrNull() ?: -1L
    }
}
