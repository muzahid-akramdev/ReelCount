package dev.akram.reelcount.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.akram.reelcount.R
import dev.akram.reelcount.ReelCountApp
import dev.akram.reelcount.data.settings.UserSettings
import dev.akram.reelcount.domain.DayBoundary
import dev.akram.reelcount.ui.ServiceStatusCard
import dev.akram.reelcount.ui.appLabel
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun HomeScreen() {
    val context = LocalContext.current
    val app = context.applicationContext as ReelCountApp
    val zone = remember { ZoneId.systemDefault() }
    val today = remember { LocalDate.now(zone).toString() }
    val settings by app.container.settings.settings
        .collectAsStateWithLifecycle(UserSettings())
    val rows by app.container.repository
        .observeDay(today)
        .collectAsStateWithLifecycle(emptyList())
    val total = rows.sumOf { it.count }
    val seconds = rows.sumOf { it.estSeconds }
    val limit = settings.dailyLimit.coerceAtLeast(1)
    val fraction = (total.toFloat() / limit.toFloat()).coerceIn(0f, 1f)
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        Text(stringResource(R.string.today_label), style = MaterialTheme.typography.labelLarge)
        Text(total.toString(), style = MaterialTheme.typography.displayLarge)
        Text(
            stringResource(R.string.progress_label, total, limit),
            style = MaterialTheme.typography.bodyMedium,
        )
        LinearProgressIndicator(
            progress = { fraction },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
        )
        Text(
            text =
                if (seconds < 60) {
                    stringResource(R.string.est_under_minute)
                } else {
                    stringResource(R.string.est_minutes, (seconds / 60).toInt())
                },
            modifier = Modifier.padding(top = 12.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            stringResource(R.string.per_app),
            modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
            style = MaterialTheme.typography.titleMedium,
        )
        if (rows.isEmpty()) {
            Text(stringResource(R.string.empty_today))
        } else {
            rows.sortedByDescending { it.count }.forEach { row ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Text(appLabel(row.app), modifier = Modifier.weight(1f))
                    Text(row.count.toString(), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
        ServiceStatusCard(Modifier.padding(top = 16.dp))
        Text(
            DayBoundary.dayOf(System.currentTimeMillis(), zone),
            modifier = Modifier.padding(top = 8.dp),
            style = MaterialTheme.typography.labelSmall,
        )
    }
}
