package dev.akram.reelcount.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.akram.reelcount.R
import dev.akram.reelcount.accessibility.ServiceStatus

@Composable
fun ServiceStatusCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val running by ServiceStatus.running.collectAsStateWithLifecycle()
    var enabled by remember { mutableStateOf(context.accessibilityEnabled()) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    enabled = context.accessibilityEnabled()
                }
            }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    val message =
        when {
            enabled && running -> stringResource(R.string.status_running)
            enabled -> stringResource(R.string.status_enabled_stopped)
            else -> stringResource(R.string.status_off)
        }
    Column(modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(stringResource(R.string.status_title), style = MaterialTheme.typography.titleMedium)
        Text(message, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
    }
}
