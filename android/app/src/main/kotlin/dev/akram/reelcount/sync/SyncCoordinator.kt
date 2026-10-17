package dev.akram.reelcount.sync

import dev.akram.reelcount.data.ReelRepository
import dev.akram.reelcount.data.settings.SettingsStore
import dev.akram.reelcount.domain.SyncBuckets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.ZoneId

sealed class SyncOutcome {
    data class Uploaded(
        val dailyRows: Int,
        val hourlyRows: Int,
    ) : SyncOutcome()

    data object Disabled : SyncOutcome()

    data object NotConfigured : SyncOutcome()

    data object NeedsLogin : SyncOutcome()

    data class Failed(
        val message: String,
    ) : SyncOutcome()
}

class SyncCoordinator(
    private val settings: SettingsStore,
    private val repository: ReelRepository,
    private val apiFactory: () -> SupabaseApi = {
        SupabaseApi(SupabaseConfig.url, SupabaseConfig.anonKey)
    },
) {
    suspend fun syncIfEnabled(): SyncOutcome {
        val current = settings.current()
        if (!current.syncEnabled) return SyncOutcome.Disabled
        return push(current.dailyLimit, current.languageTag)
    }

    suspend fun push(
        dailyLimit: Int,
        languageTag: String,
    ): SyncOutcome {
        if (!SupabaseConfig.isConfigured()) return SyncOutcome.NotConfigured
        val session = settings.currentSession() ?: return SyncOutcome.NeedsLogin
        return try {
            val api = apiFactory()
            val fresh = ensureFresh(api, session)
            val zone = ZoneId.systemDefault()
            val deviceId = settings.deviceId()
            val stats = repository.exportStats()
            val events = repository.exportEvents()
            val daily = JSONArray()
            for (stat in stats) {
                daily.put(
                    JSONObject()
                        .put("user_id", fresh.userId)
                        .put("device_id", deviceId)
                        .put("day", stat.day)
                        .put("app", stat.app)
                        .put("reels_count", stat.count)
                        .put("est_seconds", stat.estSeconds.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()),
                )
            }
            val hourlyBuckets =
                SyncBuckets.hourly(events.map { it.ts to it.app }, zone)
            val hourly = JSONArray()
            for (bucket in hourlyBuckets) {
                hourly.put(
                    JSONObject()
                        .put("user_id", fresh.userId)
                        .put("device_id", deviceId)
                        .put("day", bucket.day)
                        .put("hour", bucket.hour)
                        .put("app", bucket.app)
                        .put("reels_count", bucket.count),
                )
            }
            chunk(daily) { api.upsertDaily(fresh.accessToken, it) }
            chunk(hourly) { api.upsertHourly(fresh.accessToken, it) }
            val locale = if (languageTag == "bn") "bn" else "en"
            api.updateProfile(fresh.accessToken, fresh.userId, dailyLimit.coerceIn(1, 500), locale)
            settings.setLastSync(System.currentTimeMillis(), "")
            SyncOutcome.Uploaded(daily.length(), hourly.length())
        } catch (error: Exception) {
            val message = error.message?.take(180) ?: "sync failed"
            settings.setLastSync(settings.current().lastSyncEpochMs, message)
            SyncOutcome.Failed(message)
        }
    }

    suspend fun signIn(
        email: String,
        password: String,
    ) {
        ensureConfigured()
        val session = withContext(Dispatchers.IO) { apiFactory().signIn(email.trim(), password) }
        settings.saveSession(session)
    }

    suspend fun signUp(
        email: String,
        password: String,
    ): Boolean {
        ensureConfigured()
        val session = withContext(Dispatchers.IO) { apiFactory().signUp(email.trim(), password) } ?: return false
        settings.saveSession(session)
        return true
    }

    suspend fun signOut() {
        settings.clearSession()
    }

    private fun ensureConfigured() {
        if (!SupabaseConfig.isConfigured()) {
            error("Add SUPABASE_URL and SUPABASE_ANON_KEY, then rebuild the app.")
        }
    }

    private suspend fun ensureFresh(
        api: SupabaseApi,
        session: AuthSession,
    ): AuthSession {
        if (session.expiresAtEpochMs - System.currentTimeMillis() > 60_000L) return session
        val refreshed = api.refresh(session.refreshToken)
        settings.saveSession(refreshed)
        return refreshed
    }

    private fun chunk(
        rows: JSONArray,
        send: (JSONArray) -> Unit,
    ) {
        var index = 0
        while (index < rows.length()) {
            val slice = JSONArray()
            val end = minOf(index + 200, rows.length())
            for (i in index until end) slice.put(rows.get(i))
            send(slice)
            index = end
        }
    }
}
