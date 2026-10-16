package dev.akram.reelcount.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.akram.reelcount.ReelCountApp

class MaintenanceWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as ReelCountApp
        val cutoff = System.currentTimeMillis() - 90L * 24L * 60L * 60L * 1000L
        app.container.repository.pruneEventsBefore(cutoff)
        return Result.success()
    }
}
