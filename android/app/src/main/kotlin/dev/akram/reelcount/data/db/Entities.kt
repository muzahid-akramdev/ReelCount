package dev.akram.reelcount.data.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "events",
    indices = [Index("ts"), Index("app")],
)
data class EventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ts: Long,
    val app: String,
)

@Entity(
    tableName = "daily_stats",
    primaryKeys = ["day", "app"],
)
data class DailyStatEntity(
    val day: String,
    val app: String,
    val count: Int,
    @ColumnInfo(name = "est_seconds") val estSeconds: Long,
)

data class EventStamp(
    val ts: Long,
    val app: String,
)

@Dao
interface ReelDao {
    @Query("SELECT COALESCE(SUM(count), 0) FROM daily_stats WHERE day = :day")
    fun observeTotal(day: String): Flow<Int>

    @Query("SELECT COALESCE(SUM(count), 0) FROM daily_stats WHERE day = :day")
    suspend fun totalForDay(day: String): Int

    @Query("SELECT * FROM daily_stats WHERE day = :day")
    fun observeDay(day: String): Flow<List<DailyStatEntity>>

    @Query("SELECT * FROM daily_stats WHERE day >= :fromDay ORDER BY day ASC")
    fun observeSince(fromDay: String): Flow<List<DailyStatEntity>>

    @Query("SELECT ts, app FROM events WHERE ts >= :fromTs ORDER BY ts ASC")
    fun observeEventsSince(fromTs: Long): Flow<List<EventStamp>>

    @Insert
    suspend fun insertEvent(event: EventEntity)

    @Query(
        """
        INSERT INTO daily_stats(day, app, count, est_seconds)
        VALUES(:day, :app, 1, :est)
        ON CONFLICT(day, app) DO UPDATE SET
            count = count + 1,
            est_seconds = est_seconds + :est
        """,
    )
    suspend fun upsertStat(
        day: String,
        app: String,
        est: Long,
    )

    @Query("DELETE FROM events")
    suspend fun deleteEvents()

    @Query("DELETE FROM daily_stats")
    suspend fun deleteStats()

    @Query("DELETE FROM events WHERE ts < :before")
    suspend fun deleteEventsBefore(before: Long)

    @Query("SELECT * FROM daily_stats ORDER BY day ASC, app ASC")
    suspend fun statsSnapshot(): List<DailyStatEntity>

    @Query("SELECT ts, app FROM events ORDER BY ts ASC")
    suspend fun eventsSnapshot(): List<EventStamp>
}

@Database(
    entities = [EventEntity::class, DailyStatEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun reelDao(): ReelDao
}
