package dev.akram.reelcount.ui.debug

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.akram.reelcount.R
import dev.akram.reelcount.accessibility.InspectorBus

@Composable
fun DebugScreen() {
    val context = LocalContext.current
    val events by InspectorBus.events.collectAsStateWithLifecycle()
    var notice by remember { mutableStateOf<String?>(null) }
    val copied = stringResource(R.string.debug_copied)
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(stringResource(R.string.debug_title), style = MaterialTheme.typography.headlineSmall)
        Text(
            stringResource(R.string.debug_hint),
            modifier = Modifier.padding(top = 4.dp),
            style = MaterialTheme.typography.bodySmall,
        )
        Row {
            TextButton(
                onClick = {
                    val clipboard = context.getSystemService(ClipboardManager::class.java)
                    val text = InspectorBus.asText(events)
                    clipboard?.setPrimaryClip(ClipData.newPlainText("reelcount", text))
                    notice = copied
                },
            ) {
                Text(stringResource(R.string.debug_copy))
            }
            TextButton(onClick = {
                InspectorBus.clear()
                notice = null
            }) {
                Text(stringResource(R.string.debug_clear))
            }
        }
        if (notice != null) {
            Text(notice ?: "", style = MaterialTheme.typography.labelMedium)
        }
        if (events.isEmpty()) {
            Text(stringResource(R.string.debug_empty), modifier = Modifier.padding(top = 12.dp))
        } else {
            LazyColumn(Modifier.fillMaxWidth()) {
                itemsIndexed(events) { _, event ->
                    val state =
                        if (event.viewerActive) {
                            stringResource(R.string.debug_viewer_on)
                        } else {
                            stringResource(R.string.debug_viewer_off)
                        }
                    Text(
                        "${event.packageName}  ${event.eventType}  $state",
                        modifier = Modifier.padding(top = 12.dp),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Text(
                        event.lines.joinToString("\n"),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}
