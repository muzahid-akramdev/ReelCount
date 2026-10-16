package dev.akram.reelcount.ui.settings

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.akram.reelcount.R
import dev.akram.reelcount.ReelCountApp
import dev.akram.reelcount.applyLanguage
import dev.akram.reelcount.data.settings.UserSettings
import dev.akram.reelcount.ui.appLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.ZoneId

@Composable
fun SettingsScreen(onOpenDebug: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as ReelCountApp
    val scope = rememberCoroutineScope()
    val settings by app.container.settings.settings
        .collectAsStateWithLifecycle(UserSettings())
    val rules by app.container.rules.rules
        .collectAsStateWithLifecycle()
    var limitText by remember(settings.dailyLimit) { mutableStateOf(settings.dailyLimit.toString()) }
    var notice by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val importOk = stringResource(R.string.import_ok)
    val importFailed = stringResource(R.string.import_failed)
    val exportFailed = stringResource(R.string.export_failed)
    val exportTitle = stringResource(R.string.action_export)
    val importer =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            scope.launch {
                val text =
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.use { stream ->
                            stream.readBytes().toString(Charsets.UTF_8)
                        }
                    }
                if (text == null) {
                    notice = importFailed
                    return@launch
                }
                val parsed = withContext(Dispatchers.IO) { app.container.rules.import(text) }
                parsed
                    .onSuccess { imported ->
                        app.container.settings.addPackages(imported.apps.map { it.packageName }.toSet())
                        notice = importOk
                    }.onFailure { error ->
                        val detail = error.message?.take(180).orEmpty()
                        notice = if (detail.isEmpty()) importFailed else "$importFailed $detail"
                    }
            }
        }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        OutlinedTextField(
            value = limitText,
            onValueChange = { raw ->
                val digits = raw.filter { it.isDigit() }.take(3)
                limitText = digits
                val value = digits.toIntOrNull() ?: return@OutlinedTextField
                scope.launch { app.container.settings.setDailyLimit(value) }
            },
            label = { Text(stringResource(R.string.settings_limit)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        ToggleRow(stringResource(R.string.settings_nudges), settings.nudgesEnabled) {
            scope.launch { app.container.settings.setNudges(it) }
        }
        ToggleRow(stringResource(R.string.settings_vibrate), settings.vibrate) {
            scope.launch { app.container.settings.setVibrate(it) }
        }
        ToggleRow(stringResource(R.string.settings_overlay), settings.overlayEnabled) {
            scope.launch { app.container.settings.setOverlay(it) }
        }
        ToggleRow(stringResource(R.string.settings_backward), settings.countBackward) {
            scope.launch { app.container.settings.setCountBackward(it) }
        }
        Text(
            stringResource(R.string.settings_language),
            modifier = Modifier.padding(top = 16.dp),
            style = MaterialTheme.typography.titleMedium,
        )
        Row {
            LanguageChip("system", R.string.lang_system, settings.languageTag) {
                scope.launch {
                    app.container.settings.setLanguage("system")
                    applyLanguage("system")
                }
            }
            LanguageChip("en", R.string.lang_en, settings.languageTag) {
                scope.launch {
                    app.container.settings.setLanguage("en")
                    applyLanguage("en")
                }
            }
            LanguageChip("bn", R.string.lang_bn, settings.languageTag) {
                scope.launch {
                    app.container.settings.setLanguage("bn")
                    applyLanguage("bn")
                }
            }
        }
        Text(
            stringResource(R.string.settings_apps),
            modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
            style = MaterialTheme.typography.titleMedium,
        )
        rules.apps.forEach { rule ->
            ToggleRow(appLabel(rule.packageName), rule.packageName in settings.enabledPackages) { checked ->
                scope.launch { app.container.settings.setPackageEnabled(rule.packageName, checked) }
            }
        }
        Text(
            text =
                if (app.container.rules.usingImport()) {
                    stringResource(R.string.rules_imported)
                } else {
                    stringResource(R.string.rules_bundled)
                },
            modifier = Modifier.padding(top = 8.dp),
            style = MaterialTheme.typography.bodySmall,
        )
        TextButton(onClick = { importer.launch(arrayOf("application/json", "text/*", "*/*")) }) {
            Text(stringResource(R.string.action_import))
        }
        TextButton(onClick = {
            app.container.rules.resetToBundled()
            notice = null
        }) {
            Text(stringResource(R.string.action_reset_rules))
        }
        TextButton(onClick = {
            scope.launch {
                try {
                    val csv =
                        withContext(Dispatchers.IO) {
                            app.container.repository.exportCsv(ZoneId.systemDefault())
                        }
                    val file = File(context.cacheDir, "reelcount.csv")
                    withContext(Dispatchers.IO) { file.writeText(csv) }
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
                    val send =
                        Intent(Intent.ACTION_SEND).apply {
                            type = "text/csv"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                    context.startActivity(Intent.createChooser(send, exportTitle))
                } catch (_: Exception) {
                    notice = exportFailed
                }
            }
        }) {
            Text(stringResource(R.string.action_export))
        }
        TextButton(onClick = onOpenDebug) {
            Text(stringResource(R.string.action_open_debug))
        }
        TextButton(onClick = { confirmDelete = true }) {
            Text(stringResource(R.string.action_delete))
        }
        if (notice != null) {
            Text(notice ?: "", modifier = Modifier.padding(top = 8.dp))
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_title)) },
            text = { Text(stringResource(R.string.delete_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch { app.container.repository.deleteAll() }
                }) {
                    Text(stringResource(R.string.action_confirm_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

@Composable
private fun LanguageChip(
    tag: String,
    label: Int,
    selected: String,
    onClick: () -> Unit,
) {
    TextButton(onClick = onClick, enabled = selected != tag) {
        Text(stringResource(label))
    }
}
