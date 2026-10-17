package dev.akram.reelcount.domain

import java.time.Instant
import java.time.ZoneId

data class HourlyBucket(
    val day: String,
    val hour: Int,
    val app: String,
    val count: Int,
)

object SyncBuckets {
    fun hourly(
        events: List<Pair<Long, String>>,
        zone: ZoneId,
    ): List<HourlyBucket> {
        val grouped = linkedMapOf<String, Int>()
        for ((ts, app) in events) {
            if (app.isBlank()) continue
            val zoned = Instant.ofEpochMilli(ts).atZone(zone)
            val day = zoned.toLocalDate().toString()
            val hour = zoned.hour
            val key = "$day|$hour|$app"
            grouped[key] = (grouped[key] ?: 0) + 1
        }
        return grouped.map { (key, count) ->
            val parts = key.split('|')
            HourlyBucket(day = parts[0], hour = parts[1].toInt(), app = parts[2], count = count)
        }
    }
}
