package com.rafa.hercules.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rafa.hercules.data.*
import com.rafa.hercules.viewmodel.SessionViewModel
import com.rafa.hercules.viewmodel.ExerciseState

@Composable
fun SessionScreen(viewModel: SessionViewModel, onClose: () -> Unit) {
    val session by viewModel.activeSession.collectAsStateWithLifecycle()
    val sessionName by viewModel.sessionName.collectAsStateWithLifecycle()
    val states by viewModel.exercisesState.collectAsStateWithLifecycle()
    val restEnd by viewModel.activeRestEndTime.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val catalog by viewModel.substitutionCatalog.collectAsStateWithLifecycle()
    var substituting by remember { mutableStateOf<ExerciseState?>(null) }
    var replacement by remember { mutableStateOf<Exercise?>(null) }
    var substitutionBlocked by remember { mutableStateOf(false) }
    var finish by remember { mutableStateOf(false) }
    var cancel by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val finished by viewModel.finishedSummary.collectAsStateWithLifecycle()
    var plates by rememberSaveable { mutableStateOf(false) }
    if (finished != null) {
        FinishedWorkoutScreen(requireNotNull(finished)) {
            viewModel.dismissSummary()
            onClose()
        }
        return
    }
    val current = session
    if (current == null) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Text("Cargando sesión…")
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            TextButton(onClick = onClose) { Text("Volver") }
        }
        return
    }
    val active = current.state == "ACTIVE"
    KeepSessionScreenOn(active)
    var now by remember(current.id) { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(current.id, current.state) {
        while (active) { now = System.currentTimeMillis(); kotlinx.coroutines.delay(500) }
    }
    val duration = ((current.endTime ?: now) - current.startTime).coerceAtLeast(0L)
    val remaining = if (active && restEnd != null) viewModel.restRemainingMs(current.id, now) else 0L
    val restState = restDisplay(active && restEnd != null, remaining)
    Column(Modifier.fillMaxSize().imePadding().padding(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(sessionName, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            if (!active) TextButton(onClick = onClose) { Text("Cerrar") }
        }
        if (active) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = { plates = true }, modifier = Modifier.weight(1f)) { Text("Discos") }
            TextButton(onClick = { cancel = true }) { Text("Cancelar", color = MaterialTheme.colorScheme.error) }
            Button(onClick = { finish = true }) { Text("Finalizar") }
        }
        val seconds = duration / 1000
        val progress = sessionProgress(states.flatMap { it.series }, states.filter { it.exercise.isCardio }.map { it.exercise.id }.toSet())
        Text("${progress.completed}/${progress.total} ${if (progress.cardioOnly) "ejercicios completados" else "series de trabajo completadas"}")
        Text("Duración: %02d:%02d:%02d".format(seconds / 3600, (seconds / 60) % 60, seconds % 60))
        if (active && !viewModel.exactAlarmsAvailable()) {
            Text("Sin acceso a alarmas exactas, Android puede retrasar el beep.", style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = {
                context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")))
            }) { Text("Configurar alarmas") }
        }
        if (active) Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium,
            color = if (restState == RestDisplay.READY) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
            contentColor = if (restState == RestDisplay.READY) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(when (restState) {
                    RestDisplay.READY -> "Siguiente serie"
                    RestDisplay.COUNTING -> "Descanso: %02d:%02d".format(remaining / 60000, (remaining / 1000) % 60)
                    RestDisplay.IDLE -> "Sin descanso activo"
                }, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                if (restState == RestDisplay.COUNTING) TextButton(onClick = { viewModel.skipRest() }) { Text("Saltar") }
            }
        }
        error?.let {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(it, Modifier.weight(1f), color = MaterialTheme.colorScheme.error)
                TextButton(onClick = { viewModel.clearError() }) { Text("Cerrar") }
            }
        }
        val groups = states.groupBy { it.series.firstOrNull()?.groupId ?: it.occurrenceId }.values.toList()
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(groups, key = { it.first().occurrenceId }) { group ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        if (group.size > 1) Text("Biserie", style = MaterialTheme.typography.titleMedium)
                        val details = remember { mutableStateMapOf<String, Boolean>() }
                        group.forEach { state ->
                            var menu by remember { mutableStateOf(false) }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(state.exercise.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                                Box {
                                    TextButton(onClick = { menu = true }, modifier = Modifier.semantics { contentDescription = "Acciones de ${state.exercise.name}" }) { Text("⋮") }
                                    DropdownMenu(menu, { menu = false }) {
                                        if (active) DropdownMenuItem(text = { Text("Sustituir solo por hoy") }, onClick = {
                                            menu = false
                                            if (state.series.hasCompletedSeries()) substitutionBlocked = true
                                            else { viewModel.clearError(); substituting = state }
                                        })
                                        DropdownMenuItem(text = { Text(if (state.exercise.isCardio) "Notas" else "RPE/RIR y notas") }, onClick = { details[state.occurrenceId] = details[state.occurrenceId] != true; menu = false })
                                        if (!state.exercise.isCardio) {
                                            DropdownMenuItem(text = { Text("Cambiar kg/lb") }, onClick = {
                                                state.series.forEach { s -> viewModel.editSeries(s.id, { old ->
                                                    old.copy(unit = if (old.unit == "kg") "lb" else "kg", weight = old.weight?.let { if (old.unit == "kg") it / LB_TO_KG else it * LB_TO_KG })
                                                }, state.generation, state.exercise.id) }; menu = false
                                            })
                                            state.series.filter { active && it.parentId == null && !it.isWarmup && !it.isCompleted }.forEach { root ->
                                                DropdownMenuItem(text = { Text("Añadir bajada · serie ${root.workRound() + 1}") }, onClick = { viewModel.addDropset(root, state.generation); menu = false })
                                            }
                                        }
                                    }
                                }
                            }
                            state.progression?.takeIf { active }?.let { suggestion ->
                                Text("Alcanzaste el máximo de reps en todas las series anteriores.",
                                    style = MaterialTheme.typography.bodySmall)
                                TextButton(onClick = { viewModel.applyProgression(state) }) {
                                    Text("Usar ${displayWeight(suggestion.suggested)} ${suggestion.unit}")
                                }
                            }
                        }
                        val roots = sessionDisplayRoots(group.flatMap { it.series })
                        roots.forEachIndexed { index, row ->
                            val state = group.first { s -> s.series.any { it.id == row.id } }
                            key(row.id, row.exerciseId) {
                                if (group.size > 1 && !row.isWarmup && (index == 0 || roots[index - 1].isWarmup || roots[index - 1].workRound() != row.workRound()))
                                    Text("Ronda ${row.workRound() + 1}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
                                if (group.size > 1) Text(state.exercise.name, style = MaterialTheme.typography.labelLarge)
                                if (state.exercise.isCardio) CardioRow(row, state.previousById[row.id], state.targetDurationMs,
                                    onSeriesChange = { change -> viewModel.editSeries(row.id, change, state.generation, state.exercise.id) },
                                    onComplete = { viewModel.completeSeries(row, state.generation) }, showDetails = details[state.occurrenceId] == true)
                                else {
                                    SeriesRow(row, state.previousById[row.id],
                                        onSeriesChange = { change -> viewModel.editSeries(row.id, change, state.generation, state.exercise.id) },
                                        onComplete = { viewModel.completeSeries(row, state.generation) },
                                        showDetails = details[state.occurrenceId] == true)
                                    if (active && row.id in state.recordIds) {
                                        Text("Nuevo récord: ${displayWeight(row.weight)} ${row.unit} × ${row.reps}",
                                            color = MaterialTheme.colorScheme.primary,
                                            style = MaterialTheme.typography.labelLarge)
                                    }
                                    state.series.filter { it.parentId == row.id }.sortedWith(compareBy({ it.stageIndex ?: it.orderIndex }, { it.id })).forEach { child ->
                                         key(child.id) {
                                            SeriesRow(child, state.previousById[child.id],
                                                onSeriesChange = { change -> viewModel.editSeries(child.id, change, state.generation, state.exercise.id) },
                                                onComplete = { viewModel.completeSeries(child, state.generation) },
                                                isDropset = true, showDetails = details[state.occurrenceId] == true)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (plates) PlateCalculator { plates = false }
    if (cancel) AlertDialog(onDismissRequest = { cancel = false }, title = { Text("Cancelar entrenamiento") },
        text = { Text("Los registros se conservarán, pero esta sesión no contará para progreso ni rachas.") },
        confirmButton = { TextButton(onClick = { viewModel.cancelSession { cancel = false; onClose() } }) { Text("Sí, cancelar") } },
        dismissButton = { TextButton(onClick = { cancel = false }) { Text("Volver") } })
    if (finish) {
        var effort by rememberSaveable { mutableStateOf("") }
        val valid = effort.isBlank() || effort.toIntOrNull()?.let { it in 0..10 } == true
        AlertDialog(onDismissRequest = { finish = false }, title = { Text("Finalizar sesión") },
            text = { OutlinedTextField(effort, { effort = it }, label = { Text("Esfuerzo general 0–10 (opcional)") }, isError = !valid, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true) },
            confirmButton = { TextButton(enabled = valid, onClick = { viewModel.finishSession(effort.toIntOrNull()) { finish = false } }) { Text("Guardar y ver resumen") } },
            dismissButton = { TextButton(onClick = { finish = false }) { Text("Volver") } })
    }
    if (substitutionBlocked) AlertDialog(onDismissRequest = { substitutionBlocked = false },
        title = { Text("No se puede sustituir") },
        text = { Text("Esta aparición ya tiene una serie completada, incluidas aproximaciones y bajadas. Las otras apariciones no se modifican.") },
        confirmButton = { TextButton(onClick = { substitutionBlocked = false }) { Text("Entendido") } })
    substituting?.takeIf { active }?.let { original ->
        Dialog(onDismissRequest = { substituting = null; replacement = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(Modifier.fillMaxSize()) {
                Column(Modifier.safeDrawingPadding().imePadding()) {
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) }
                    Box(Modifier.weight(1f)) {
                        ExerciseCatalogScreen(substitutionCandidates(catalog, original.exercise), 0,
                            onAdd = { replacement = it; viewModel.clearError() }, onCreate = { _, _ -> }, onDelete = {},
                            onClose = { substituting = null; replacement = null }, selectionOnly = true)
                    }
                }
            }
        }
        replacement?.let { chosen ->
            AlertDialog(onDismissRequest = { replacement = null }, title = { Text("Sustituir solo por hoy") },
                text = { Column {
                    Text("¿Cambiar «${original.exercise.name}» por «${chosen.name}» en esta aparición? Se descartarán los datos ingresados sin completar. Se conservan series y objetivos; la rutina semanal no cambia.")
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                } },
                confirmButton = { TextButton(onClick = {
                    viewModel.substituteExercise(original, chosen.id, discardConfirmed = true) { substituting = null; replacement = null }
                }) { Text("Confirmar sustitución") } },
                dismissButton = { TextButton(onClick = { replacement = null }) { Text("Volver") } })
        }
    }
}

@Composable
private fun KeepSessionScreenOn(active: Boolean) {
    val view = LocalView.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(view, lifecycle, active) {
        val previous = view.keepScreenOn
        fun update() { view.keepScreenOn = active && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) }
        val observer = LifecycleEventObserver { _, _ -> update() }
        lifecycle.addObserver(observer)
        update()
        onDispose { lifecycle.removeObserver(observer); view.keepScreenOn = previous }
    }
}

private fun displayWeight(value: Double?): String = value?.let { "%.4f".format(java.util.Locale.US, it).trimEnd('0').trimEnd('.') } ?: ""

@Composable
fun SeriesRow(series: Series, previousSeries: Series?, onSeriesChange: ((Series) -> Series) -> Unit,
    onComplete: () -> Unit, isDropset: Boolean = false, showDetails: Boolean = false) {
    var weight by rememberSaveable(series.id) { mutableStateOf(displayWeight(series.weight)) }
    var reps by rememberSaveable(series.id) { mutableStateOf(series.reps?.toString() ?: "") }
    var effort by rememberSaveable(series.id) { mutableStateOf(series.effort?.toString() ?: "") }
    var showNotes by rememberSaveable(series.id) { mutableStateOf(!series.notes.isNullOrBlank()) }
    var notes by rememberSaveable(series.id) { mutableStateOf(series.notes ?: "") }
    var displayedUnit by rememberSaveable(series.id) { mutableStateOf(series.unit) }
    var lastSavedWeight by rememberSaveable(series.id) { mutableStateOf(displayWeight(series.weight)) }
    LaunchedEffect(series.unit, series.weight) {
        val saved = displayWeight(series.weight)
        if (displayedUnit != series.unit || weight == lastSavedWeight) weight = saved
        displayedUnit = series.unit
        lastSavedWeight = saved
    }
    val weightValue = weight.replace(',', '.').toDoubleOrNull()
    val weightValid = weight.isBlank() || (weightValue != null && weightValue.isFinite() && weightValue >= 0)
    val repsValue = reps.toIntOrNull()
    val repsValid = repsValue != null && repsValue >= 0
    val effortValue = effort.toIntOrNull()
    val effortValid = effort.isBlank() || (effortValue != null && effortValue >= 0 && ((series.effortScale ?: "RPE") != "RPE" || effortValue <= 10))
    Column(Modifier.fillMaxWidth().padding(top = 8.dp, start = if (isDropset) 20.dp else 0.dp)) {
        val label = when { isDropset -> "Bajada ${series.stageIndex ?: ""}"; series.isWarmup -> "Aproximación"; else -> "Trabajo · serie ${(series.roundIndex ?: series.orderIndex) + 1}" }
        Text(label, style = MaterialTheme.typography.labelLarge)
        series.targetReps?.let { Text("Objetivo: ${repTargetLabel(it, series.targetRepsMax)} reps", style = MaterialTheme.typography.bodySmall) }
        if (!isDropset && !series.isWarmup && (series.targetDropsets ?: 0) > 0) Text("Bajadas previstas: ${series.targetDropsets}. Añádelas antes de completar.", style = MaterialTheme.typography.bodySmall)
        Text("Anterior: ${previousSeries?.let { "${displayWeight(it.weight).ifBlank { "—" }} ${it.unit} × ${it.reps ?: "—"}" } ?: "—"}", style = MaterialTheme.typography.bodySmall)
        if (previousSeries != null && (previousSeries.weight != null || previousSeries.reps != null)) TextButton(onClick = {
            val copied = copyPreviousValues(series, previousSeries)
            weight = displayWeight(copied.weight); reps = copied.reps?.toString() ?: ""
            onSeriesChange { copyPreviousValues(it, previousSeries) }
        }) { Text("Usar anterior") }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(weight, { text ->
                weight = text
                val v = text.replace(',', '.').toDoubleOrNull()
                if (text.isBlank() || (v != null && v.isFinite() && v >= 0)) onSeriesChange { it.copy(weight = v) }
            }, label = { Text("Peso (${series.unit})") }, isError = !weightValid, modifier = Modifier.weight(1f), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            OutlinedTextField(reps, { text ->
                reps = text; val v = text.toIntOrNull()
                if (text.isBlank() || (v != null && v >= 0)) onSeriesChange { it.copy(reps = v) }
            }, label = { Text("Reps") }, isError = reps.isNotBlank() && !repsValid, modifier = Modifier.weight(1f), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            Checkbox(checked = series.isCompleted, enabled = series.isCompleted || (weightValid && repsValid && effortValid), modifier = Modifier.semantics { contentDescription = "Completar $label" }, onCheckedChange = { checked ->
                if (checked) onComplete() else onSeriesChange { it.copy(isCompleted = false) }
            })
        }
        if (showDetails) {
            OutlinedTextField(effort, { text ->
                effort = text; val v = text.toIntOrNull()
                if (text.isBlank() || (v != null && v >= 0 && ((series.effortScale ?: "RPE") != "RPE" || v <= 10))) onSeriesChange { it.copy(effort = v, effortScale = it.effortScale ?: "RPE") }
            }, label = { Text(series.effortScale ?: "RPE") }, isError = !effortValid, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            TextButton(onClick = {
                effort = ""
                onSeriesChange { it.copy(effort = null, effortScale = if ((it.effortScale ?: "RPE") == "RPE") "RIR" else "RPE") }
            }) { Text("Cambiar escala") }
            TextButton(onClick = { showNotes = !showNotes }) { Text(if (showNotes) "Ocultar notas" else "Notas") }
            if (showNotes) OutlinedTextField(notes, { text -> notes = text; onSeriesChange { it.copy(notes = text.ifBlank { null }) } }, label = { Text("Notas") }, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
fun CardioRow(series: Series, previousSeries: Series?, targetDurationMs: Long?, onSeriesChange: ((Series) -> Series) -> Unit, onComplete: () -> Unit, showDetails: Boolean = false) {
    var minutes by rememberSaveable(series.id) { mutableStateOf(series.durationMs?.let { (it / 60000).toString() } ?: "") }
    var notes by rememberSaveable(series.id) { mutableStateOf(series.notes ?: "") }
    var showNotes by rememberSaveable(series.id) { mutableStateOf(!series.notes.isNullOrBlank()) }
    val value = minutes.toLongOrNull()
    val valid = value != null && value in 0L..1440L
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        targetDurationMs?.let { Text("Objetivo: ${it / 60000} min") }
        Text("Anterior: ${previousSeries?.durationMs?.let { "${it / 60000} min" } ?: "—"}")
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(minutes, { text ->
                minutes = text; val v = text.toLongOrNull()
                if (text.isBlank() || (v != null && v in 0L..1440L)) onSeriesChange { it.copy(durationMs = v?.times(60000)) }
            }, label = { Text("Minutos realizados") }, isError = minutes.isNotBlank() && !valid, modifier = Modifier.weight(1f), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            Checkbox(series.isCompleted, enabled = series.isCompleted || valid, onCheckedChange = { checked -> if (checked) onComplete() else onSeriesChange { it.copy(isCompleted = false) } })
        }
        if (showDetails) TextButton(onClick = { showNotes = !showNotes }) { Text(if (showNotes) "Ocultar notas" else "Notas") }
        if (showDetails && showNotes) OutlinedTextField(notes, { text -> notes = text; onSeriesChange { it.copy(notes = text.ifBlank { null }) } }, label = { Text("Notas") }, modifier = Modifier.fillMaxWidth())
    }
}
