package dev.akram.reelcount.data

import androidx.room.withTransaction
import dev.akram.reelcount.data.db.AppDatabase
import dev.akram.reelcount.data.db.DailyStatEntity
import dev.akram.reelcount.data.db.EventEntity
import dev.akram.reelcount.data.db.EventStamp
import dev.akram.reelcount.domain.DayBoundary
import kotlinx.coroutines.flow.Flow
import java.time.ZoneId

data class RecordResult(
    val before: Int,
    val total: Int,
)

class ReelRepository(
    private val database: AppDatabase,
) {
    private val dao = database.reelDao()

    fun observeTotal(day: String): Flow<Int> = dao.observeTotal(day)

    fun observeDay(day: String): Flow<List<DailyStatEntity>> = dao.observeDay(day)

    fun observeSince(fromDay: String): Flow<List<DailyStatEntity>> = dao.observeSince(fromDay)

    fun observeEventsSince(fromTs: Long): Flow<List<EventStamp>> = dao.observeEventsSince(fromTs)

    suspend fun record(
        packageName: String,
        epochMs: Long,
        estSeconds: Long,
        zone: ZoneId,
    ): RecordResult {
        val day = DayBoundary.dayOf(epochMs, zone)
        return database.withTransaction {
            val before = dao.totalForDay(day)
            dao.insertEvent(EventEntity(ts = epochMs, app = packageName))
            dao.upsertStat(day, packageName, estSeconds)
            RecordResult(before = before, total = dao.totalForDay(day))
        }
    }

    suspend fun totalForDay(
        epochMs: Long,
        zone: ZoneId,
    ): Int = dao.totalForDay(DayBoundary.dayOf(epochMs, zone))

    suspend fun exportCsv(zone: ZoneId): String = buildCsv(exportStats(), exportEvents(), zone)

    suspend fun exportStats(): List<DailyStatEntity> = dao.statsSnapshot()

    suspend fun exportEvents(): List<EventStamp> = dao.eventsSnapshot()

    suspend fun deleteAll() {
        database.withTransaction {
            dao.deleteEvents()
            dao.deleteStats()
        }
    }

    suspend fun pruneEventsBefore(epochMs: Long) {
        dao.deleteEventsBefore(epochMs)
    }

    companion object {
        fun buildCsv(
            stats: List<DailyStatEntity>,
            events: List<EventStamp>,
            zone: ZoneId,
        ): String {
            val builder = StringBuilder()
            builder.append("type,day_or_time,app,count,est_seconds\n")
            for (stat in stats) {
                builder.append("stat,")
                builder.append(stat.day)
                builder.append(',')
                builder.append(stat.app)
                builder.append(',')
                builder.append(stat.count)
                builder.append(',')
                builder.append(stat.estSeconds)
                builder.append('\n')
            }
            for (event in events) {
                val time =
                    java.time.Instant
                        .ofEpochMilli(event.ts)
                        .atZone(zone)
                        .toOffsetDateTime()
                        .toString()
                builder.append("event,")
                builder.append(time)
                builder.append(',')
                builder.append(event.app)
                builder.append(",,\n")
            }
            return builder.toString()
        }
    }
}
