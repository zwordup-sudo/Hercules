package com.rafa.hercules.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rafa.hercules.data.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private enum class DateRange(val label: String, val days: Long?) {
    D30("30 días", 30),
    D90("90 días", 90),
    Y1("12 meses", 365),
    ALL("Todo", null)
}

/**
 * Métricas por ejercicio/variante con selector y rango de fechas.
 * Muestra valores en texto además del gráfico: una sola sesión sigue siendo legible.
 */
@Composable
fun ExerciseProgressSection(
    sessions: List<SessionWithSeries>,
    exercises: List<Exercise>
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Text("Por ejercicio", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))

        // Solo ejercicios con registros finalizados; sin datos no se ofrece.
        val exerciseIdsInHistory = sessions
            .filter { it.session.state == "FINISHED" }
            .flatMap { sws -> sws.series.filter { it.isCompleted }.map { it.exerciseId } }
            .toSet()
        val available = exercises.filter { it.id in exerciseIdsInHistory }
            .sortedBy { it.name.lowercase() }

        if (available.isEmpty()) {
            Text(
                "Aún no hay registros completados para mostrar gráficas por ejercicio.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@Column
        }

        var selectedId by rememberSaveable { mutableStateOf(available.first().id) }
        var range by remember { mutableStateOf(DateRange.ALL) }
        val selected = available.firstOrNull { it.id == selectedId } ?: available.first()

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            var menuOpen by remember { mutableStateOf(false) }
            Box(modifier = Modifier.weight(1f)) {
                OutlinedButton(onClick = { menuOpen = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(selected.name, maxLines = 1, textAlign = TextAlign.Start)
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    available.forEach { ex ->
                        DropdownMenuItem(
                            text = { Text(if (ex.isCardio) "${ex.name} (cardio)" else ex.name) },
                            onClick = {
                                selectedId = ex.id
                                menuOpen = false
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DateRange.entries.forEach { r ->
                FilterChip(
                    selected = range == r,
                    onClick = { range = r },
                    label = { Text(r.label) }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        val today = LocalDate.now()
        val fromMillis = range.days?.let {
            today.minusDays(it).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }
        val metrics = buildExerciseMetrics(
            sessions = sessions,
            exerciseId = selected.id,
            isCardio = selected.isCardio,
            fromMillis = fromMillis
        )

        if (metrics.isEmpty()) {
            Text(
                "Sin datos de ${selected.name} en este rango.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@Column
        }

        val formatter = remember { DateTimeFormatter.ofPattern("dd/MM/yy") }
        fun dateOf(ms: Long): String =
            Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate().format(formatter)

        if (selected.isCardio) {
            Text("Duración realizada (min)", style = MaterialTheme.typography.bodyMedium)
            val minutes = metrics.map { it.cardioMinutes?.toFloat() }
            if (minutes.size >= 2) {
                val maxM = (minutes.filterNotNull().maxOrNull() ?: 1f) * 1.1f
                LineChart(data = minutes, maxVal = maxM, lineColor = MaterialTheme.colorScheme.primary)
            }
            MetricValueList(items = metrics.map { m ->
                "${dateOf(m.startTime)}: ${m.cardioMinutes} min"
            })
        } else {
            Text("Mayor carga por sesión (kg)", style = MaterialTheme.typography.bodyMedium)
            val loads = metrics.map { it.maxLoadKg?.toFloat() }
            if (loads.size >= 2) {
                val maxL = (loads.filterNotNull().maxOrNull() ?: 1f) * 1.1f
                LineChart(data = loads, maxVal = maxL, lineColor = MaterialTheme.colorScheme.primary)
            }
            MetricValueList(items = metrics.map { m ->
                val load = m.maxLoadKg
                if (load == null) "${dateOf(m.startTime)}: sin carga registrada"
                else "${dateOf(m.startTime)}: ${"%.1f".format(load)} kg × ${m.maxLoadReps ?: "?"} reps"
            })

            Spacer(modifier = Modifier.height(8.dp))
            Text("Volumen de trabajo por sesión (kg·rep)", style = MaterialTheme.typography.bodyMedium)
            val vols = metrics.map { it.volumeKg?.toFloat() }
            if (vols.size >= 2) {
                val maxV = (vols.filterNotNull().maxOrNull() ?: 1f) * 1.1f
                LineChart(data = vols, maxVal = maxV, lineColor = MaterialTheme.colorScheme.tertiary)
            }
            MetricValueList(items = metrics.map { m ->
                "${dateOf(m.startTime)}: ${m.volumeKg?.let { "%.0f kg·rep".format(it) } ?: "volumen incompleto"} · ${m.totalReps ?: "—"} reps · ${m.workSeriesCount} series"
            })
        }
    }
}

@Composable
private fun MetricValueList(items: List<String>) {
    // Valores en texto: necesarios con una sola sesión y accesibles sin leer el gráfico.
    Column(modifier = Modifier.fillMaxWidth()) {
        if (items.size > 20) Text("Últimos 20 registros; el historial conserva todos.", style = MaterialTheme.typography.bodySmall)
        items.takeLast(20).forEach { line ->
            Text(line, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 1.dp))
        }
    }
}

/**
 * Comparación de sesiones equivalentes de una rutina: duración, esfuerzo
 * general y volumen de fuerza. Indica comparabilidad limitada cuando faltan
 * instantáneas o hay composiciones distintas.
 */
@Composable
fun RoutineComparisonSection(
    sessions: List<SessionWithSeries>,
    routines: List<Routine>,
    cardioExerciseIds: Set<String> = emptySet()
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Text("Comparación por rutina", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))

        val candidates = routines
            .filter { r -> sessions.any { it.session.state == "FINISHED" && it.session.routineId == r.id } }
            .sortedBy { it.name.lowercase() }

        if (candidates.isEmpty()) {
            Text(
                "No hay sesiones finalizadas asociadas a una rutina todavía.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@Column
        }

        var selectedRoutineId by rememberSaveable { mutableStateOf(candidates.first().id) }
        val selected = candidates.firstOrNull { it.id == selectedRoutineId } ?: candidates.first()

        var menuOpen by remember { mutableStateOf(false) }
        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = { menuOpen = true }, modifier = Modifier.fillMaxWidth()) {
                Text(selected.name, maxLines = 1, textAlign = TextAlign.Start)
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                candidates.forEach { r ->
                    DropdownMenuItem(
                        text = { Text(r.name) },
                        onClick = {
                            selectedRoutineId = r.id
                            menuOpen = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        val versions = sessions.filter { it.session.routineId == selected.id && it.session.state == "FINISHED" }
            .mapNotNull { routineSnapshotSignature(it.session.routineSnapshot) }.distinct()
        var signature by remember(selected.id) { mutableStateOf<String?>(null) }
        if (versions.size > 1) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = signature == null, onClick = { signature = null }, label = { Text("Todas") })
            versions.forEachIndexed { index, version -> FilterChip(selected = signature == version, onClick = { signature = version }, label = { Text("Versión ${index + 1}") }) }
        }
        val scopedSessions = if (signature == null) sessions else sessions.filter { routineSnapshotSignature(it.session.routineSnapshot) == signature }
        val comparison = buildRoutineComparison(scopedSessions, selected.id, cardioExerciseIds)

        if (comparison.rows.isEmpty()) {
            Text(
                "Sin sesiones finalizadas para comparar.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@Column
        }

        if (comparison.comparabilityLimited) {
            val reason = when {
                comparison.sessionsWithoutSnapshot > 0 && comparison.compositionGroups > 1 ->
                    "Hay sesiones sin instantánea y con composiciones distintas."
                comparison.sessionsWithoutSnapshot > 0 ->
                    "Hay sesiones antiguas sin instantánea de composición."
                else ->
                    "La composición de la rutina cambió entre sesiones."
            }
            Text(
                "Comparabilidad limitada: $reason",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.tertiary
            )
            Spacer(modifier = Modifier.height(4.dp))
        } else {
            Text(
                "Todas las sesiones comparadas comparten la misma composición.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
        }

        if (!comparison.comparabilityLimited && comparison.rows.size >= 2) {
            val chronological = comparison.rows.sortedBy { it.startTime }
            Text("Esfuerzo general por sesión", style = MaterialTheme.typography.bodyMedium)
            LineChart(chronological.map { it.overallEffort?.toFloat() }, 10f, MaterialTheme.colorScheme.primary)
            val volumes = chronological.map { it.strengthVolumeKg?.toFloat() }
            Text("Volumen comparable (kg·rep)", style = MaterialTheme.typography.bodyMedium)
            LineChart(volumes, (volumes.filterNotNull().maxOrNull() ?: 1f) * 1.1f, MaterialTheme.colorScheme.tertiary)
        }
        Text("Mostrando las últimas 20 sesiones. Consulta las demás en el historial.", style = MaterialTheme.typography.bodySmall)

        // Encabezado de columnas sin depender solo del color.
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Fecha", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1.2f))
            Text("Duración", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
            Text("Esfuerzo", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
            Text("Volumen", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1.2f), textAlign = TextAlign.End)
        }
        HorizontalDivider()
        val formatter = remember { DateTimeFormatter.ofPattern("dd/MM/yy") }
        comparison.rows.take(20).forEach { row ->
            val date = Instant.ofEpochMilli(row.startTime)
                .atZone(ZoneId.systemDefault()).toLocalDate().format(formatter)
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(date, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1.2f))
                Text(
                    row.durationMinutes?.let { "$it min" } ?: "—",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.End
                )
                Text(
                    row.overallEffort?.let { "$it/10" } ?: "—",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.End
                )
                Text(
                    row.strengthVolumeKg?.let { "%.0f kg·rep".format(it) } ?: "—",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1.2f),
                    textAlign = TextAlign.End
                )
            }
        }
    }
}
