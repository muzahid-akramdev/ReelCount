package dev.akram.reelcount.ui.onboarding

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.akram.reelcount.R
import dev.akram.reelcount.ReelCountApp
import dev.akram.reelcount.ui.ServiceStatusCard
import dev.akram.reelcount.ui.openAccessibilitySettings
import dev.akram.reelcount.ui.openAppDetails
import dev.akram.reelcount.ui.requestIgnoreBatteryOptimizations
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen(onFinished: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as ReelCountApp
    val scope = rememberCoroutineScope()
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        Text(stringResource(R.string.onboarding_title), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.onboarding_body), modifier = Modifier.padding(top = 8.dp))
        Text(
            stringResource(R.string.onboarding_collect_title),
            modifier = Modifier.padding(top = 20.dp),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(stringResource(R.string.onboarding_collect_body), modifier = Modifier.padding(top = 4.dp))
        Text(
            stringResource(R.string.onboarding_not_title),
            modifier = Modifier.padding(top = 16.dp),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(stringResource(R.string.onboarding_not_body), modifier = Modifier.padding(top = 4.dp))
        Text(
            stringResource(R.string.onboarding_restricted_title),
            modifier = Modifier.padding(top = 16.dp),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(stringResource(R.string.onboarding_restricted_body), modifier = Modifier.padding(top = 4.dp))
        Text(
            stringResource(R.string.onboarding_battery_title),
            modifier = Modifier.padding(top = 16.dp),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(stringResource(R.string.onboarding_battery_body), modifier = Modifier.padding(top = 4.dp))
        Text(stringResource(R.string.onboarding_oem), modifier = Modifier.padding(top = 8.dp))
        ServiceStatusCard(Modifier.padding(top = 12.dp))
        Button(
            onClick = { context.openAccessibilitySettings() },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
        ) {
            Text(stringResource(R.string.action_accessibility))
        }
        OutlinedButton(
            onClick = { context.openAppDetails() },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
        ) {
            Text(stringResource(R.string.action_app_info))
        }
        OutlinedButton(
            onClick = { context.requestIgnoreBatteryOptimizations() },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
        ) {
            Text(stringResource(R.string.action_battery))
        }
        Button(
            onClick = {
                scope.launch {
                    app.container.settings.setOnboardingDone()
                    onFinished()
                }
            },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 24.dp),
        ) {
            Text(stringResource(R.string.action_continue))
        }
    }
}
