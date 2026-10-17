package dev.akram.reelcount

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.room.Room
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dev.akram.reelcount.data.ReelRepository
import dev.akram.reelcount.data.db.AppDatabase
import dev.akram.reelcount.data.rules.RulesRepository
import dev.akram.reelcount.data.settings.SettingsStore
import dev.akram.reelcount.sync.SyncCoordinator
import dev.akram.reelcount.worker.MaintenanceWorker
import dev.akram.reelcount.worker.SyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.concurrent.TimeUnit

class ReelCountApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        val language = runBlocking { container.settings.current().languageTag }
        applyLanguage(language)
        val maintenance =
            PeriodicWorkRequestBuilder<MaintenanceWorker>(1, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
                .build()
        val sync =
            PeriodicWorkRequestBuilder<SyncWorker>(6, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
        val work = WorkManager.getInstance(this)
        work.enqueueUniquePeriodicWork("reelcount-maintenance", ExistingPeriodicWorkPolicy.KEEP, maintenance)
        work.enqueueUniquePeriodicWork("reelcount-sync", ExistingPeriodicWorkPolicy.KEEP, sync)
        container.scope.launch { container.sync.syncIfEnabled() }
    }
}

class AppContainer(
    context: Application,
) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val database: AppDatabase =
        Room
            .databaseBuilder(
                context,
                AppDatabase::class.java,
                "reelcount.db",
            ).build()
    val repository = ReelRepository(database)
    val settings = SettingsStore(context)
    val rules = RulesRepository(context)
    val sync = SyncCoordinator(settings, repository)
}

fun applyLanguage(tag: String) {
    val locales =
        if (tag == "system" || tag.isBlank()) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(tag)
        }
    AppCompatDelegate.setApplicationLocales(locales)
}
