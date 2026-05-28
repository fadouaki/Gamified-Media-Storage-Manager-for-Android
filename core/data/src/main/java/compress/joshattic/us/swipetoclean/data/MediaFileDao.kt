package compress.joshattic.us.swipetoclean.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaFileDao {

    @Query("SELECT * FROM media_files WHERE status = :status ORDER BY timestamp DESC")
    fun getByStatus(status: String): Flow<List<MediaFileEntity>>

    @Query("SELECT * FROM media_files WHERE status = 'UNSEEN' ORDER BY timestamp DESC")
    fun getUnseenFiles(): Flow<List<MediaFileEntity>>

    @Query("SELECT * FROM media_files WHERE status IN ('TRASHED', 'PENDING_COMPRESS', 'COMPRESSING') ORDER BY timestamp DESC")
    fun getBinnedFiles(): Flow<List<MediaFileEntity>>

    @Query("SELECT * FROM media_files WHERE file_uri = :uri LIMIT 1")
    suspend fun getByUri(uri: String): MediaFileEntity?

    @Query("SELECT * FROM media_files WHERE status = 'UNSEEN' ORDER BY timestamp DESC LIMIT 1")
    suspend fun getNextUnseen(): MediaFileEntity?

    @Query("SELECT * FROM media_files WHERE status IN ('UNSEEN', 'KEPT') ORDER BY timestamp DESC")
    fun getReviewableFiles(): Flow<List<MediaFileEntity>>

    @Query("SELECT COUNT(*) FROM media_files WHERE status = 'UNSEEN'")
    fun getUnseenCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM media_files WHERE status = 'KEPT'")
    fun getKeptCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM media_files WHERE status IN ('TRASHED', 'PENDING_COMPRESS')")
    fun getBinnedCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM media_files WHERE status = 'TRASHED'")
    fun getTrashedCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM media_files WHERE status = 'PENDING_COMPRESS'")
    fun getPendingCompressCount(): Flow<Int>

    @Query("SELECT COALESCE(SUM(original_size - COALESCE(compressed_size, 0)), 0) FROM media_files WHERE status = 'COMPLETED'")
    fun getTotalSavedBytes(): Flow<Long>

    @Query("SELECT COALESCE(SUM(original_size), 0) FROM media_files WHERE status = 'TRASHED'")
    fun getTotalTrashedBytes(): Flow<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(entities: List<MediaFileEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: MediaFileEntity)

    @Update
    suspend fun update(entity: MediaFileEntity)

    @Query("UPDATE media_files SET status = :status WHERE file_uri IN (:uris)")
    suspend fun batchUpdateStatus(uris: List<String>, status: String)

    @Query("DELETE FROM media_files WHERE file_uri IN (:uris)")
    suspend fun deleteByUris(uris: List<String>)

    @Query("SELECT EXISTS(SELECT 1 FROM media_files WHERE file_uri = :uri)")
    suspend fun exists(uri: String): Boolean
}
