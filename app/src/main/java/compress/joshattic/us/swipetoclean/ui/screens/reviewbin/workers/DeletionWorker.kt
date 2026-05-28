package compress.joshattic.us.swipetoclean.ui.screens.reviewbin.workers

import android.content.Context
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import compress.joshattic.us.swipetoclean.SwipeToCleanApp
import compress.joshattic.us.swipetoclean.data.MediaStoreRepository

class DeletionWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val uris = inputData.getStringArray("uris") ?: return Result.failure()
        if (uris.isEmpty()) return Result.success()

        val repository = MediaStoreRepository(applicationContext)
        val database = (applicationContext as SwipeToCleanApp).database
        val dao = database.mediaFileDao()

        val result = repository.deleteFiles(uris.toList())

        return when {
            result.isSuccess -> {
                dao.deleteByUris(uris.toList())
                Result.success()
            }
            else -> {
                if (runAttemptCount < 3) Result.retry()
                else Result.failure()
            }
        }
    }
}
