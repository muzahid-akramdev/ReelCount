package dev.akram.reelcount.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

object DayBoundary {
    fun dayOf(
        epochMs: Long,
        zone: ZoneId,
    ): String =
        Instant
            .ofEpochMilli(epochMs)
            .atZone(zone)
            .toLocalDate()
            .toString()

    fun startEpoch(
        day: LocalDate,
        zone: ZoneId,
    ): Long = day.atStartOfDay(zone).toInstant().toEpochMilli()

    fun hourOf(
        epochMs: Long,
        zone: ZoneId,
    ): Int = Instant.ofEpochMilli(epochMs).atZone(zone).hour
}
