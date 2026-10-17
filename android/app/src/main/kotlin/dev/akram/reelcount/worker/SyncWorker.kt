package dev.akram.reelcount.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.akram.reelcount.ReelCountApp
import dev.akram.reelcount.sync.SyncOutcome

class SyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as ReelCountApp
        return when (val outcome = app.container.sync.syncIfEnabled()) {
            is SyncOutcome.Failed -> Result.retry()
            else -> Result.success()
        }
    }
}
