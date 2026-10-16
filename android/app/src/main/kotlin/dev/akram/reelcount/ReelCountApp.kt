package dev.akram.reelcount

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.room.Room
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dev.akram.reelcount.data.ReelRepository
import dev.akram.reelcount.data.db.AppDatabase
import dev.akram.reelcount.data.rules.RulesRepository
import dev.akram.reelcount.data.settings.SettingsStore
import dev.akram.reelcount.worker.MaintenanceWorker
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
        val request =
            PeriodicWorkRequestBuilder<MaintenanceWorker>(1, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
                .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "reelcount-maintenance",
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }
}

class AppContainer(
    context: Application,
) {
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
