package com.rafa.hercules.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.rafa.hercules.data.AppearanceMode
import com.rafa.hercules.data.Exercise
import com.rafa.hercules.data.Routine
import com.rafa.hercules.data.RoutineExercise
import com.rafa.hercules.data.RoutineStats
import com.rafa.hercules.data.WeeklyConsistency
import com.rafa.hercules.ui.theme.HerculesTheme
import java.time.DayOfWeek

/*
 * Vistas previas de revisión (datos ficticios, nunca valores de producción):
 * Claro y Oscuro, ancho reducido y fuente ampliada. Compilan con el proyecto;
 * NO equivalen a una revisión visual ejecutada en dispositivo.
 */

private val dias = listOf("L", "M", "X", "J", "V", "S", "D")

@Preview(name = "Claro", uiMode = Configuration.UI_MODE_NIGHT_NO, widthDp = 360)
@Preview(name = "Oscuro", uiMode = Configuration.UI_MODE_NIGHT_YES, widthDp = 360)
@Preview(name = "Claro · ancho reducido", uiMode = Configuration.UI_MODE_NIGHT_NO, widthDp = 320)
@Preview(name = "Claro · fuente ampliada", uiMode = Configuration.UI_MODE_NIGHT_NO, widthDp = 360, fontScale = 1.5f)
@Composable
private fun InicioPreview() {
    HerculesTheme {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Vista previa · datos ficticios", style = MaterialTheme.typography.bodySmall)
            Text("Hércules", style = MaterialTheme.typography.headlineMedium)
            WeekSelector(selectedDay = 1, todayDay = 3) {}
            ConsistencyCard(WeeklyConsistency(trainedDays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY), trainedCount = 2, goalDays = 5))
            ActiveSessionCard(routineName = "Rutina ficticia de ejemplo", onResume = {})
            RoutineCard(
                routine = Routine(name = "Rutina ficticia de ejemplo (muy larga para revisar corte de texto)", assignedDay = 1),
                stats = RoutineStats(exerciseCount = 6, workSeriesCount = 16),
                selectedDay = 1,
                days = dias,
                onStart = {}, onEdit = {}, onShare = {}, onAssign = {}, onUnassign = {}, onArchive = {}
            )
            Text("Apariencia", style = MaterialTheme.typography.titleMedium)
            AppearanceSection(selected = AppearanceMode.SYSTEM, onSelect = {})
        }
    }
}

private val catalogoFicticio = listOf(
    Exercise(id = "e1", name = "Press banca ficticio"),
    Exercise(id = "e2", name = "Cinta ficticia", type = "CARDIO"),
    Exercise(id = "e3", name = "Remo ficticio", origin = "USER")
)

@Preview(name = "Editor · claro", uiMode = Configuration.UI_MODE_NIGHT_NO, widthDp = 360)
@Preview(name = "Editor · oscuro", uiMode = Configuration.UI_MODE_NIGHT_YES, widthDp = 360)
@Preview(name = "Editor · ancho reducido", uiMode = Configuration.UI_MODE_NIGHT_NO, widthDp = 320)
@Preview(name = "Editor · fuente ampliada", uiMode = Configuration.UI_MODE_NIGHT_NO, widthDp = 360, fontScale = 1.5f)
@Composable
private fun RoutineEditorPreview() {
    HerculesTheme {
        RoutineEditor(
            availableExercises = catalogoFicticio,
            initialName = "Rutina ficticia",
            initialRows = listOf(
                RoutineExercise(id = "r1", routineId = "", exerciseId = "e1"),
                RoutineExercise(id = "r2", routineId = "", exerciseId = "e2", targetDurationMs = 20 * 60000L)
            ),
            onSave = { _, _ -> },
            onCancel = {},
            onAddCustomExercise = { _, _ -> }
        )
    }
}

@Preview(name = "Catálogo · claro", uiMode = Configuration.UI_MODE_NIGHT_NO, widthDp = 360)
@Preview(name = "Catálogo · oscuro", uiMode = Configuration.UI_MODE_NIGHT_YES, widthDp = 360)
@Preview(name = "Catálogo · fuente ampliada", uiMode = Configuration.UI_MODE_NIGHT_NO, widthDp = 360, fontScale = 1.5f)
@Composable
private fun ExerciseCatalogPreview() {
    HerculesTheme {
        ExerciseCatalogScreen(
            exercises = catalogoFicticio,
            routineExerciseCount = 3,
            onAdd = {},
            onCreate = { _, _ -> },
            onDelete = {},
            onClose = {}
        )
    }
}
