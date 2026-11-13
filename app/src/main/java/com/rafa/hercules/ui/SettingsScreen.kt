package com.rafa.hercules.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rafa.hercules.data.AppearanceMode
import com.rafa.hercules.ui.theme.HerculesSpacing

/**
 * Ajustes (v1.15): apariencia persistida, acciones de respaldo reutilizando la
 * lógica de HistoryViewModel (una sola implementación, sin duplicar confirmaciones)
 * e información de almacenamiento y versión.
 */
@Composable
fun SettingsScreen(
    appearance: AppearanceMode,
    onAppearanceChange: (AppearanceMode) -> Unit,
    backupViewModel: HistoryViewModel,
    onImported: () -> Unit
) {
    val context = LocalContext.current
    val version = remember(context) {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "—"
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { Text("Ajustes", style = MaterialTheme.typography.headlineMedium) }

        item {
            SettingsCard(title = "Apariencia") {
                AppearanceSection(selected = appearance, onSelect = onAppearanceChange)
            }
        }

        item {
            SettingsCard(title = "Panel de descanso") {
                val prefs = remember(context) {
                    context.getSharedPreferences(EDGE_PREFS, android.content.Context.MODE_PRIVATE)
                }
                var enabled by remember { mutableStateOf(prefs.getBoolean(EDGE_ENABLED, false)) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Mostrar al salir de Hércules", Modifier.weight(1f))
                    Switch(checked = enabled, onCheckedChange = { checked ->
                        enabled = checked
                        prefs.edit().putBoolean(EDGE_ENABLED, checked).apply()
                    })
                }
                Text("Muestra el tiempo restante en el borde derecho. Toca el panel para abrir Hércules, saltar u ocultar.",
                    style = MaterialTheme.typography.bodySmall)
                if (enabled) TextButton(onClick = {
                    context.startActivity(android.content.Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}")))
                }) { Text("Configurar permiso para mostrar sobre otras apps") }
            }
        }

        item {
            SettingsCard(title = "Respaldo de datos") {
                BackupControls(viewModel = backupViewModel, onImported = onImported)
            }
        }

        item {
            SettingsCard(title = "Almacenamiento") {
                Text(
                    "Tus datos de entrenamiento se guardan solo en este dispositivo: no hay cuenta ni servidor. " +
                        "La apariencia es una preferencia de este teléfono y no se incluye en el respaldo.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text("Versión $version", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Tarjeta de sección de Ajustes: título y contenido agrupados con separación uniforme. */
@Composable
private fun SettingsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(10.dp))
            content()
        }
    }
}

/** Selector Claro / Oscuro / Sistema con filas táctiles y descripción de cada opción. */
@Composable
fun AppearanceSection(selected: AppearanceMode, onSelect: (AppearanceMode) -> Unit) {
    val hints = mapOf(
        AppearanceMode.SYSTEM to "Se adapta al modo del teléfono",
        AppearanceMode.LIGHT to "Fondo celeste y acentos azules",
        AppearanceMode.DARK to "Fondo grafito y acentos violetas"
    )
    Column {
        AppearanceMode.entries.forEach { mode ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = HerculesSpacing.touch)
                    .clickable { onSelect(mode) }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(selected = selected == mode, onClick = { onSelect(mode) })
                Column(modifier = Modifier.weight(1f)) {
                    Text(mode.label, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        hints.getValue(mode),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * Acciones de exportar/importar compartidas con Progreso: reutiliza HistoryViewModel
 * (misma lógica, mismo bloqueo de operaciones concurrentes y misma confirmación).
 */
@Composable
fun BackupControls(viewModel: HistoryViewModel, onImported: () -> Unit) {
    val context = LocalContext.current
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    var confirmImport by remember { mutableStateOf<Uri?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { viewModel.export(context, it) }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> confirmImport = uri }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { exportLauncher.launch("hercules_backup.json") },
                enabled = !busy,
                modifier = Modifier.heightIn(min = HerculesSpacing.touch)
            ) { Text("Exportar") }
            OutlinedButton(
                onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) },
                enabled = !busy,
                modifier = Modifier.heightIn(min = HerculesSpacing.touch)
            ) { Text("Importar") }
        }
        if (busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }

    confirmImport?.let { uri ->
        AlertDialog(
            onDismissRequest = { confirmImport = null },
            title = { Text("Reemplazar datos") },
            text = { Text("Importar reemplazará los datos actuales. Exporta un respaldo antes de continuar.") },
            confirmButton = {
                TextButton(onClick = { confirmImport = null; viewModel.restore(context, uri, onImported) }) { Text("Importar") }
            },
            dismissButton = { TextButton(onClick = { confirmImport = null }) { Text("Cancelar") } }
        )
    }
}
