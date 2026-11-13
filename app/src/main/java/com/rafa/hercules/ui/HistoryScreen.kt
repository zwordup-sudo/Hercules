package com.rafa.hercules.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rafa.hercules.data.*
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun HistoryScreen(viewModel: HistoryViewModel, onSessionClick: (String) -> Unit) {
    val sessions by viewModel.finishedSessions.collectAsStateWithLifecycle()
    val exercises by viewModel.exercises.collectAsStateWithLifecycle()
    val routines by viewModel.routines.collectAsStateWithLifecycle()
    val streak by viewModel.streakInfo.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    var showGoal by remember { mutableStateOf(false) }
    var dateText by rememberSaveable { mutableStateOf<String?>(null) }
    var monthText by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    val selectedDate = dateText?.let(LocalDate::parse)
    val month = YearMonth.parse(monthText)
    LaunchedEffect(Unit) { while (true) { viewModel.refreshToday(); kotlinx.coroutines.delay(60000) } }
    val zone = ZoneId.systemDefault()
    val byDate = sessions.groupBy { Instant.ofEpochMilli(it.session.startTime).atZone(zone).toLocalDate() }
    val trainedDays = sessions.filter(::isTrainingSession).map { Instant.ofEpochMilli(it.session.startTime).atZone(zone).toLocalDate() }.toSet()
    val cardio = exercises.filter { it.isCardio }.map { it.id }.toSet()
    val filtered = selectedDate?.let { byDate[it].orEmpty() } ?: sessions
    val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Text("Progreso e historial", style = MaterialTheme.typography.headlineMedium)
            // Exportar/Importar viven en Ajustes desde la v1.15 (misma lógica, un solo lugar).
            Text("Racha actual: ${streak.currentStreak} semanas · mejor: ${streak.bestStreak}")
            TextButton(onClick = { showGoal = true }, enabled = !busy) { Text("Meta: ${streak.currentGoal} días/semana") }
        }
        item { ProgressCard { AnnualHeatmap(sessions) { dateText = if (dateText == it.toString()) null else it.toString() } } }
        item { ProgressCard { ExerciseProgressSection(sessions, exercises) } }
        item { ProgressCard { RoutineComparisonSection(sessions, routines, cardio) } }
        item {
            ProgressCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { monthText = month.minusMonths(1).toString() }) { Text("‹") }
                Text(month.format(DateTimeFormatter.ofPattern("MMMM yyyy", java.util.Locale("es", "MX"))), Modifier.weight(1f), textAlign = TextAlign.Center)
                IconButton(onClick = { monthText = month.plusMonths(1).toString() }) { Text("›") }
            }
            val blanks = month.atDay(1).dayOfWeek.value - 1
            val weeks = (blanks + month.lengthOfMonth() + 6) / 7
            Column {
                Row { listOf("L", "M", "X", "J", "V", "S", "D").forEach { Text(it, Modifier.weight(1f), textAlign = TextAlign.Center) } }
                repeat(weeks) { week ->
                    Row {
                        repeat(7) { day ->
                            val number = week * 7 + day - blanks + 1
                            if (number !in 1..month.lengthOfMonth()) Spacer(Modifier.weight(1f).height(44.dp))
                            else {
                                val date = month.atDay(number)
                                val selected = selectedDate == date
                                Box(Modifier.weight(1f).height(44.dp).padding(2.dp)
                                    .background(if (selected) MaterialTheme.colorScheme.primary else if (date in trainedDays) MaterialTheme.colorScheme.primaryContainer else Color.Transparent, MaterialTheme.shapes.small)
                                    .clickable { dateText = if (selected) null else date.toString() }, contentAlignment = Alignment.Center) {
                                    Text(number.toString(), color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
                                }
                            }
                        }
                    }
                }
            }
            if (selectedDate != null) TextButton(onClick = { dateText = null }) { Text("Mostrar todas las sesiones") }
            }
        }
        if (filtered.isEmpty()) item { Text("No hay sesiones para mostrar.") }
        items(filtered, key = { it.session.id }) { sws ->
            val summary = buildSessionSummary(sws.series) { it in cardio }
            Card(onClick = { if (!busy) onSessionClick(sws.session.id) }, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(Instant.ofEpochMilli(sws.session.startTime).atZone(zone).format(formatter), style = MaterialTheme.typography.titleMedium)
                    Text("Duración de la sesión: ${sws.session.endTime?.let { ((it - sws.session.startTime).coerceAtLeast(0) / 60000).toString() } ?: "—"} min")
                    Text("Ejercicios: ${summary.exerciseCount} · series de trabajo: ${summary.workSeriesCount}")
                    Text(
                        when (strengthVolumeState(summary)) {
                            StrengthVolumeState.NOT_APPLICABLE -> "Volumen de fuerza: no aplica"
                            StrengthVolumeState.INCOMPLETE -> "Volumen de fuerza: sin datos completos"
                            StrengthVolumeState.VALUE -> "Volumen de fuerza: %.1f kg·rep".format(summary.workVolumeKg ?: 0.0)
                        }
                    )
                    summary.warmupVolumeKg?.takeIf { it > 0 }?.let { Text("Aproximaciones: %.1f kg·rep".format(it)) }
                    when (cardioDurationState(summary)) {
                        CardioDurationState.NOT_APPLICABLE -> Unit
                        CardioDurationState.MISSING -> Text("Cardio: sin duración registrada")
                        CardioDurationState.VALUE -> Text("Cardio: ${summary.cardioMinutes} min")
                    }
                    sws.session.overallEffort?.let { Text("Esfuerzo general: $it/10") }
                }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
    if (showGoal) {
        var goal by remember { mutableStateOf(streak.currentGoal.toString()) }
        val valid = goal.toIntOrNull()?.let { it in 1..7 } == true
        AlertDialog(onDismissRequest = { showGoal = false }, title = { Text("Meta semanal") },
            text = { OutlinedTextField(goal, { goal = it }, label = { Text("Días (1–7)") }, isError = !valid) },
            confirmButton = { TextButton(enabled = valid, onClick = { viewModel.updateGoal(goal.toInt()); showGoal = false }) { Text("Guardar") } },
            dismissButton = { TextButton(onClick = { showGoal = false }) { Text("Volver") } })
    }
}

@Composable
fun AnnualHeatmap(sessions: List<SessionWithSeries>, onDateSelected: (LocalDate) -> Unit) {
    val today = LocalDate.now()
    val startDate = today.minusYears(1).with(DayOfWeek.MONDAY)
    val activeDays = sessions.filter(::isTrainingSession)
        .map { Instant.ofEpochMilli(it.session.startTime).atZone(ZoneId.systemDefault()).toLocalDate() }
        .toSet()

    // Semanas completas (lunes a domingo) desde hace un año hasta la semana actual; cubre todo el rango.
    val weekStarts = remember(today, startDate) {
        buildList {
            var cursor = startDate
            while (!cursor.isAfter(today)) { add(cursor); cursor = cursor.plusWeeks(1) }
        }
    }
    val monthFormatter = remember { DateTimeFormatter.ofPattern("MMM", Locale("es", "MX")) }
    val listState = rememberLazyListState()
    // Arranca en las semanas recientes, sin dejar de poder recorrer el año completo deslizando.
    LaunchedEffect(weekStarts.size) { if (weekStarts.isNotEmpty()) listState.scrollToItem(weekStarts.lastIndex) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text("Mapa Anual", style = MaterialTheme.typography.titleSmall)
        Spacer(modifier = Modifier.height(8.dp))
        LazyRow(state = listState, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            items(weekStarts.size) { weekIndex ->
                val weekStart = weekStarts[weekIndex]
                val showMonth = weekIndex == 0 || weekStart.month != weekStarts[weekIndex - 1].month
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (showMonth) weekStart.format(monthFormatter) else "",
                        style = MaterialTheme.typography.labelSmall
                    )
                    repeat(7) { offset ->
                        val date = weekStart.plusDays(offset.toLong())
                        val inRange = !date.isAfter(today)
                        val isActive = activeDays.contains(date)
                        Box(
                            modifier = Modifier
                                .padding(vertical = 1.dp)
                                .size(12.dp)
                                .background(
                                    when {
                                        !inRange -> Color.Transparent
                                        isActive -> MaterialTheme.colorScheme.primary
                                        else -> MaterialTheme.colorScheme.surfaceVariant
                                    },
                                    shape = RoundedCornerShape(2.dp)
                                )
                                .then(if (inRange) Modifier.clickable { onDateSelected(date) } else Modifier)
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "Cada columna es una semana; desliza para recorrer el año.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Contenedor uniforme de los módulos de Progreso. */
@Composable
private fun ProgressCard(content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
        Column(modifier = Modifier.padding(vertical = 12.dp)) {
            content()
        }
    }
}
