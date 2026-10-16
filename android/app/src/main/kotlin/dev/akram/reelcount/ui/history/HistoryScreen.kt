package dev.akram.reelcount.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.akram.reelcount.R
import dev.akram.reelcount.ReelCountApp
import dev.akram.reelcount.data.settings.UserSettings
import dev.akram.reelcount.domain.DayBoundary
import dev.akram.reelcount.domain.underLimitStreak
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun HistoryScreen() {
    val context = LocalContext.current
    val app = context.applicationContext as ReelCountApp
    val zone = remember { ZoneId.systemDefault() }
    val today = remember { LocalDate.now(zone) }
    val settings by app.container.settings.settings
        .collectAsStateWithLifecycle(UserSettings())
    val fromDay = remember(today) { today.minusDays(119).toString() }
    val stats by app.container.repository
        .observeSince(fromDay)
        .collectAsStateWithLifecycle(emptyList())
    val fromTs = remember(today) { DayBoundary.startEpoch(today.minusDays(29), zone) }
    val events by app.container.repository
        .observeEventsSince(fromTs)
        .collectAsStateWithLifecycle(emptyList())
    var days by remember { mutableIntStateOf(7) }
    val totals =
        remember(stats) {
            stats.groupBy { it.day }.mapValues { (_, rows) -> rows.sumOf { it.count } }
        }
    val series =
        (0 until days).map { index ->
            val day = today.minusDays((days - 1 - index).toLong())
            totals[day.toString()] ?: 0
        }
    val windowStart = DayBoundary.startEpoch(today.minusDays((days - 1).toLong()), zone)
    val hours = IntArray(24)
    events.asSequence().filter { it.ts >= windowStart }.forEach { event ->
        hours[DayBoundary.hourOf(event.ts, zone)]++
    }
    val peak = hours.maxOrNull()?.coerceAtLeast(1) ?: 1
    val streak = if (totals.isEmpty()) 0 else underLimitStreak(totals, today, settings.dailyLimit)
    val barMax = series.maxOrNull()?.coerceAtLeast(1) ?: 1
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = days == 7,
                onClick = { days = 7 },
                label = { Text(stringResource(R.string.history_range_7)) },
            )
            FilterChip(
                selected = days == 30,
                onClick = { days = 30 },
                label = { Text(stringResource(R.string.history_range_30)) },
            )
        }
        if (series.all { it == 0 } && hours.all { it == 0 }) {
            Text(stringResource(R.string.history_empty), modifier = Modifier.padding(top = 24.dp))
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 20.dp)
                .height(140.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            series.forEach { count ->
                val fraction = (count.toFloat() / barMax.toFloat()).coerceAtLeast(0.04f)
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight(fraction)
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(3.dp)),
                )
            }
        }
        Text(
            stringResource(R.string.heatmap_title),
            modifier = Modifier.padding(top = 28.dp, bottom = 8.dp),
            style = MaterialTheme.typography.titleMedium,
        )
        for (row in 0 until 4) {
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                for (column in 0 until 6) {
                    val hour = row * 6 + column
                    val level = hours[hour].toFloat() / peak.toFloat()
                    Box(
                        Modifier
                            .weight(1f)
                            .padding(2.dp)
                            .height(36.dp)
                            .background(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.12f + 0.88f * level),
                                RoundedCornerShape(4.dp),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(hour.toString(), fontSize = 11.sp, textAlign = TextAlign.Center)
                    }
                }
            }
        }
        Text(
            stringResource(R.string.streak_label),
            modifier = Modifier.padding(top = 28.dp),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(stringResource(R.string.streak_days, streak), style = MaterialTheme.typography.displaySmall)
    }
}
