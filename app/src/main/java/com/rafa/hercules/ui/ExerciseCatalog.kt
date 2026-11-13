package com.rafa.hercules.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rafa.hercules.data.Exercise
import com.rafa.hercules.data.ExerciseFilter
import com.rafa.hercules.data.countLabel
import com.rafa.hercules.data.filterExercises
import com.rafa.hercules.data.isCardio
import com.rafa.hercules.ui.theme.HerculesSpacing

/**
 * Catálogo para añadir ejercicios a una rutina (v1.16).
 * Se apoya en los datos existentes: nombre y tipo (Fuerza/Cardio). No inventa
 * grupos musculares, variantes ni equipamiento que el modelo todavía no tiene.
 */
@Composable
fun ExerciseCatalogScreen(
    exercises: List<Exercise>,
    routineExerciseCount: Int,
    onAdd: (Exercise) -> Unit,
    onCreate: (String, String?) -> Unit,
    onDelete: (Exercise) -> Unit,
    onClose: () -> Unit,
    selectionOnly: Boolean = false
) {
    var query by rememberSaveable { mutableStateOf("") }
    var filterName by rememberSaveable { mutableStateOf(ExerciseFilter.ALL.name) }
    val filter = ExerciseFilter.entries.firstOrNull { it.name == filterName } ?: ExerciseFilter.ALL
    var showCreateDialog by remember { mutableStateOf(false) }
    var exerciseToDelete by remember { mutableStateOf<Exercise?>(null) }

    val visible = filterExercises(exercises = exercises, query = query, filter = filter)

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 8.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose, modifier = Modifier.size(HerculesSpacing.touch)) {
                Icon(Icons.Default.ArrowBack, contentDescription = if (selectionOnly) "Volver a la sesión" else "Volver al editor")
            }
            Text(
                if (selectionOnly) "Sustituir solo por hoy" else "Ejercicios",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Buscar ejercicio") },
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }, modifier = Modifier.size(HerculesSpacing.touch)) {
                        Icon(Icons.Default.Clear, contentDescription = "Borrar búsqueda")
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
        )

        if (!selectionOnly) Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ExerciseFilter.entries.forEach { option ->
                FilterChip(
                    selected = filter == option,
                    onClick = { filterName = option.name },
                    label = { Text(option.label) }
                )
            }
        }

        if (!selectionOnly) Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { showCreateDialog = true },
                modifier = Modifier.heightIn(min = HerculesSpacing.touch)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Crear ejercicio")
            }
            Text(
                countLabel(routineExerciseCount, "ejercicio en la rutina", "ejercicios en la rutina"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2
            )
        }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (visible.isEmpty()) {
                item {
                    Text(
                        if (selectionOnly && exercises.isEmpty()) "No hay otros ejercicios activos del mismo tipo."
                        else if (exercises.isEmpty()) "El catálogo está vacío. Crea un ejercicio para empezar."
                        else "Ningún ejercicio coincide con la búsqueda.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            items(visible, key = { it.id }) { exercise ->
                Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                exercise.name,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                if (exercise.isCardio) "Cardio" else "Fuerza",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(
                            onClick = { onAdd(exercise) },
                            modifier = Modifier.heightIn(min = HerculesSpacing.touch)
                        ) { Text(if (selectionOnly) "Elegir" else "Añadir") }
                        // Los iniciales (SEED) no muestran la acción: no se pueden eliminar (regla v1.14).
                        if (!selectionOnly && exercise.origin != "SEED") {
                            IconButton(
                                onClick = { exerciseToDelete = exercise },
                                modifier = Modifier.size(HerculesSpacing.touch)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Eliminar ${exercise.name}")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateExerciseDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { name, type ->
                onCreate(name, type)
                showCreateDialog = false
            }
        )
    }

    exerciseToDelete?.let { ex ->
        AlertDialog(
            onDismissRequest = { exerciseToDelete = null },
            title = { Text("Eliminar ejercicio") },
            text = {
                Column {
                    Text("¿Eliminar «${ex.name}»?")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Los entrenamientos anteriores se conservarán.")
                    if (ex.origin == "LEGACY") {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Es un ejercicio de origen antiguo: no se pudo determinar si era inicial o lo creaste tú.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { val target = ex; exerciseToDelete = null; onDelete(target) }) { Text("Eliminar") }
            },
            dismissButton = { TextButton(onClick = { exerciseToDelete = null }) { Text("Cancelar") } }
        )
    }
}

/** Formulario de creación de ejercicio propio (nombre + tipo), compartido con el editor. */
@Composable
fun CreateExerciseDialog(
    onDismiss: () -> Unit,
    onCreate: (String, String?) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Crear ejercicio") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre del ejercicio") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text("Tipo", style = MaterialTheme.typography.labelLarge)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = type == null, onClick = { type = null })
                    Text("Fuerza")
                    Spacer(modifier = Modifier.width(16.dp))
                    RadioButton(selected = type == "CARDIO", onClick = { type = "CARDIO" })
                    Text("Cardio")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { if (name.isNotBlank()) onCreate(name, type) },
                enabled = name.isNotBlank()
            ) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
