package com.rafa.hercules.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rafa.hercules.data.*
import com.rafa.hercules.ui.theme.HerculesSpacing
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.UUID

/** Abre el menú de compartir de Android con el texto de la plantilla (sin datos personales). */
fun shareRoutineText(context: Context, text: String) {
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    try {
        context.startActivity(Intent.createChooser(sendIntent, "Compartir rutina"))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "No hay ninguna app para compartir", Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun RoutineScreen(
    viewModel: RoutineViewModel,
    onStartSession: (String) -> Unit,
    onResumeSession: () -> Unit = {}
) {
    val routines by viewModel.routines.collectAsStateWithLifecycle()
    val exercises by viewModel.availableExercises.collectAsStateWithLifecycle()
    val stats by viewModel.routineStats.collectAsStateWithLifecycle()
    val consistency by viewModel.weeklyConsistency.collectAsStateWithLifecycle()
    val activeSession by viewModel.activeSession.collectAsStateWithLifecycle()
    var isEditing by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var editingName by rememberSaveable { mutableStateOf("") }
    var editingGeneration by rememberSaveable { mutableStateOf(LocalMutations.generation) }
    var loadedRows by remember { mutableStateOf<List<RoutineExercise>?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val error by viewModel.error.collectAsStateWithLifecycle()
    LaunchedEffect(editingId, isEditing) {
        if (isEditing && loadedRows == null) loadedRows = editingId?.let { viewModel.configuration(it) } ?: emptyList()
    }
    val edit: (Routine) -> Unit = { routine ->
        editingGeneration = LocalMutations.generation; editingId = routine.id; editingName = routine.name; loadedRows = null; isEditing = true
    }
    val newRoutine: () -> Unit = {
        editingGeneration = LocalMutations.generation; editingId = null; editingName = ""; loadedRows = emptyList(); isEditing = true
    }

    if (!isEditing) {
        // Refresca el día en pantalla (cambio de medianoche) sin depender de la sesión abierta.
        LaunchedEffect(Unit) { while (true) { viewModel.refreshToday(); kotlinx.coroutines.delay(60000) } }
        var selectedDay by rememberSaveable { mutableStateOf<Int?>(LocalDate.now().dayOfWeek.value) }
        val todayDay = LocalDate.now().dayOfWeek.value
        val days = listOf("L", "M", "X", "J", "V", "S", "D")

        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Hércules", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = newRoutine, modifier = Modifier.size(HerculesSpacing.touch)) {
                    Icon(Icons.Default.Add, contentDescription = "Nueva rutina")
                }
            }
            WeekSelector(selectedDay = selectedDay, todayDay = todayDay) { day ->
                selectedDay = if (selectedDay == day) null else day
            }
            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp))
            }
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                activeSession?.let { session ->
                    item(key = "sesion-activa") {
                        ActiveSessionCard(
                            routineName = routines.find { it.id == session.routineId }?.name,
                            onResume = onResumeSession
                        )
                    }
                }
                item(key = "constancia") { ConsistencyCard(consistency) }

                val dayRoutines = selectedDay?.let { d -> routines.filter { it.assignedDay == d } } ?: routines
                val otherRoutines = selectedDay?.let { d -> routines.filter { it.assignedDay != d } } ?: emptyList()
                val card: @Composable (Routine) -> Unit = { routine ->
                    RoutineCard(
                        routine = routine,
                        stats = stats[routine.id],
                        selectedDay = selectedDay,
                        days = days,
                        onStart = { onStartSession(routine.id) },
                        onEdit = { edit(routine) },
                        onShare = { scope.launch { shareRoutineText(context, viewModel.buildShareText(routine)) } },
                        onAssign = { day -> viewModel.assignDay(routine.id, day) },
                        onUnassign = { viewModel.assignDay(routine.id, null) },
                        onArchive = { viewModel.archiveRoutine(routine.id) }
                    )
                }

                if (routines.isEmpty()) {
                    item {
                        Text(
                            "Aún no hay rutinas. Pulsa + para crear la primera.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    item(key = "titulo-dia") {
                        Text(
                            if (selectedDay != null) "Rutinas de este día" else "Todas las rutinas",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    if (selectedDay != null && dayRoutines.isEmpty()) {
                        item {
                            Text(
                                "Sin rutinas asignadas para este día.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    items(dayRoutines, key = { it.id }) { routine -> card(routine) }
                    if (selectedDay != null && otherRoutines.isNotEmpty()) {
                        item(key = "titulo-otras") {
                            Text("Otras rutinas", style = MaterialTheme.typography.titleMedium)
                        }
                        items(otherRoutines, key = { it.id }) { routine -> card(routine) }
                    }
                }
            }
        }
    } else {
        val initial = loadedRows
        if (initial == null) { Text("Cargando rutina…"); return }
        RoutineEditor(
            availableExercises = exercises,
            initialName = editingName,
            initialRows = initial,
            error = error,
            isEditing = editingId != null,
            onSave = { name, list ->
                viewModel.saveRoutine(name, list, editingId, editingGeneration) { isEditing = false }
            },
            onCancel = { isEditing = false },
            onAddCustomExercise = { name, type -> viewModel.addCustomExercise(name, type) },
            onArchiveExercise = { ex -> viewModel.archiveExercise(ex.id) }
        )
    }
}

/** Selector semanal de la referencia: 7 días con hoy marcado y selección conmutable. */
@Composable
fun WeekSelector(selectedDay: Int?, todayDay: Int, onSelect: (Int) -> Unit) {
    val letters = listOf("L", "M", "X", "J", "V", "S", "D")
    val dayNames = listOf("lunes", "martes", "miércoles", "jueves", "viernes", "sábado", "domingo")
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        letters.forEachIndexed { index, letter ->
            val day = index + 1
            val selected = selectedDay == day
            val isToday = todayDay == day
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(HerculesSpacing.touch)
                    .clip(CircleShape)
                    .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                    .then(
                        if (isToday && !selected) Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape)
                        else Modifier
                    )
                    .clickable(onClick = { onSelect(day) }),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    letter,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (selected || isToday) FontWeight.Bold else FontWeight.Medium,
                    modifier = Modifier.semantics { contentDescription = "Día ${dayNames[index]}" }
                )
            }
        }
    }
}

/**
 * Resumen de constancia semanal con datos reales: días únicos con entrenamiento
 * completado (aproximaciones, dropsets y cardio vacíos no cuentan por las reglas existentes).
 */
@Composable
fun ConsistencyCard(consistency: WeeklyConsistency) {
    val letters = listOf("L", "M", "X", "J", "V", "S", "D")
    Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Constancia semanal", style = MaterialTheme.typography.titleSmall)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    "${consistency.trainedCount} de ${countLabel(consistency.goalDays, "día", "días")}",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f)
                )
                if (consistency.trainedCount >= consistency.goalDays && consistency.goalDays > 0) {
                    Text("Meta cumplida", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                letters.forEachIndexed { index, letter ->
                    val trained = DayOfWeek.of(index + 1) in consistency.trainedDays
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(if (trained) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            letter,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (trained) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Text(
                "Días con entrenamiento completado esta semana.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Aviso de sesión en curso con la acción de reanudar. */
@Composable
fun ActiveSessionCard(routineName: String?, onResume: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Entrenamiento en curso", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                routineName?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Button(onClick = onResume, modifier = Modifier.heightIn(min = HerculesSpacing.touch)) { Text("Reanudar") }
        }
    }
}

/** Tarjeta de rutina con cantidades reales, acción principal y acciones secundarias en menú. */
@Composable
fun RoutineCard(
    routine: Routine,
    stats: RoutineStats?,
    selectedDay: Int?,
    days: List<String>,
    onStart: () -> Unit,
    onEdit: () -> Unit,
    onShare: () -> Unit,
    onAssign: (Int) -> Unit,
    onUnassign: () -> Unit,
    onArchive: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    routine.name,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(HerculesSpacing.touch)) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Más acciones de ${routine.name}")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Editar rutina") },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                        onClick = { menuOpen = false; onEdit() }
                    )
                    DropdownMenuItem(
                        text = { Text("Compartir") },
                        leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                        onClick = { menuOpen = false; onShare() }
                    )
                    if (selectedDay != null && routine.assignedDay != selectedDay) {
                        DropdownMenuItem(
                            text = { Text("Asignar a este día") },
                            onClick = { menuOpen = false; onAssign(selectedDay) }
                        )
                    }
                    if (routine.assignedDay != null) {
                        DropdownMenuItem(
                            text = { Text("Desasignar") },
                            onClick = { menuOpen = false; onUnassign() }
                        )
                    }
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("Eliminar", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                        onClick = { menuOpen = false; confirmDelete = true }
                    )
                }
            }
            Text(
                stats?.let {
                    "${countLabel(it.exerciseCount, "ejercicio", "ejercicios")} · ${countLabel(it.workSeriesCount, "serie", "series")}"
                } ?: "…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    routine.assignedDay?.let { "Asignada: ${days[it - 1]}" } ?: "Sin día asignado",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Button(onClick = onStart, modifier = Modifier.heightIn(min = HerculesSpacing.touch)) { Text("Entrenar") }
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Eliminar rutina") },
            text = {
                Column {
                    Text("¿Eliminar «${routine.name}»?")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Sus entrenamientos anteriores se conservarán.")
                }
            },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onArchive() }) { Text("Eliminar") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } }
        )
    }
}

/**
 * Editor de rutina (v1.16): conserva todas las funciones (añadir/crear, reordenar,
 * quitar, configurar series/reps/descanso/cardio/dropsets y biseries) con una
 * presentación más compacta y cabecera con volver.
 */
@Composable
fun RoutineEditor(
    availableExercises: List<Exercise>,
    onSave: (String, List<RoutineExercise>) -> Unit,
    onCancel: () -> Unit,
    onAddCustomExercise: (String, String?) -> Unit,
    onArchiveExercise: (Exercise) -> Unit = {},
    initialName: String = "",
    initialRows: List<RoutineExercise> = emptyList(),
    error: String? = null,
    isEditing: Boolean = false
) {
    val context = LocalContext.current
    var routineName by rememberSaveable { mutableStateOf(initialName) }
    val draftSaver = listSaver<List<RoutineExercise>, String>(save = { it.map(::encodeRoutineDraft) }, restore = { it.map(::decodeRoutineDraft) })
    var selectedList by rememberSaveable(stateSaver = draftSaver) { mutableStateOf(initialRows) }
    fun setRows(rows: List<RoutineExercise>) { selectedList = normalizeRoutineGroups(rows) }
    var showCatalog by remember { mutableStateOf(false) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var confirmExit by remember { mutableStateOf(false) }

    val hasChanges = routineDraftChanged(initialName, initialRows, routineName, selectedList)
    val requestExit: () -> Unit = { if (hasChanges) confirmExit = true else onCancel() }
    val canSave = routineName.isNotBlank() && selectedList.isNotEmpty()

    if (showCatalog) {
        // El catálogo reemplaza al editor mientras está abierto; al volver se conserva el borrador.
        ExerciseCatalogScreen(
            exercises = availableExercises,
            routineExerciseCount = selectedList.size,
            onAdd = { ex ->
                selectedList = normalizeRoutineGroups(
                    selectedList + RoutineExercise(id = UUID.randomUUID().toString(), routineId = "", exerciseId = ex.id)
                )
            },
            onCreate = onAddCustomExercise,
            onDelete = onArchiveExercise,
            onClose = { showCatalog = false }
        )
        return
    }

    Column(modifier = Modifier.fillMaxSize().imePadding()) {
        // Cabecera con volver y título según el modo.
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 8.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { if (hasChanges) confirmExit = true else onCancel() },
                modifier = Modifier.size(HerculesSpacing.touch)
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Volver")
            }
            Text(
                if (isEditing) "Editar rutina" else "Crear rutina",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp))
        }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item(key = "nombre") {
                Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = routineName,
                            onValueChange = { routineName = it },
                            label = { Text("Nombre de la rutina") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            "Series 1–100 · aproximaciones 0–20 · descanso 0–86400 s",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            item(key = "acciones-catalogo") {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { showCatalog = true },
                        modifier = Modifier.weight(1f).heightIn(min = HerculesSpacing.touch)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Añadir ejercicio")
                    }
                    OutlinedButton(
                        onClick = { showCatalog = true },
                        modifier = Modifier.weight(1f).heightIn(min = HerculesSpacing.touch)
                    ) { Text("Catálogo") }
                }
            }
            if (selectedList.isEmpty()) {
                item {
                    Text(
                        "Sin ejercicios todavía. Añade al menos uno para guardar la rutina.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            val groups = routineEditorGroups(selectedList)
            items(groups, key = { group -> selectedList.getOrNull(group.first())?.id ?: "grupo-${group.first()}" }) { group ->
                if (group.size == 2) {
                    val first = group[0]
                    val second = group[1]
                    BiserieContainer {
                        EditorExerciseCard(
                            row = selectedList[first],
                            exercise = availableExercises.find { it.id == selectedList[first].exerciseId },
                            position = first + 1,
                            canMoveUp = false,
                            canMoveDown = false,
                            showGroupToggle = false,
                            isGroupedWithPrevious = false,
                            inBiserie = true,
                            onMoveUp = {},
                            onMoveDown = {},
                            onDelete = { setRows(selectedList.filterIndexed { i, _ -> i != first }) },
                            onToggleGroup = {},
                            onChange = { transform -> setRows(selectedList.mapIndexed { i, row -> if (i == first) transform(row) else row }) }
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        EditorExerciseCard(
                            row = selectedList[second],
                            exercise = availableExercises.find { it.id == selectedList[second].exerciseId },
                            position = second + 1,
                            canMoveUp = false,
                            canMoveDown = false,
                            showGroupToggle = true,
                            isGroupedWithPrevious = true,
                            inBiserie = true,
                            onMoveUp = {},
                            onMoveDown = {},
                            onDelete = { setRows(selectedList.filterIndexed { i, _ -> i != second }) },
                            onToggleGroup = { setRows(toggleBiserie(selectedList, second)) },
                            onChange = { transform -> setRows(selectedList.mapIndexed { i, row -> if (i == second) transform(row) else row }) }
                        )
                    }
                } else {
                    val index = group[0]
                    val row = selectedList[index]
                    val exercise = availableExercises.find { it.id == row.exerciseId }
                    val previous = selectedList.getOrNull(index - 1)
                    val previousIsCardio = previous?.let { prev -> availableExercises.find { it.id == prev.exerciseId }?.isCardio == true } ?: false
                    EditorExerciseCard(
                        row = row,
                        exercise = exercise,
                        position = index + 1,
                        canMoveUp = index > 0,
                        canMoveDown = index < selectedList.lastIndex,
                        showGroupToggle = index > 0 && exercise?.isCardio != true && !previousIsCardio,
                        isGroupedWithPrevious = row.groupId != null && previous?.groupId == row.groupId,
                        inBiserie = false,
                        onMoveUp = { setRows(swapRows(selectedList, index, index - 1)) },
                        onMoveDown = { setRows(swapRows(selectedList, index, index + 1)) },
                        onDelete = { setRows(selectedList.filterIndexed { i, _ -> i != index }) },
                        onToggleGroup = { setRows(toggleBiserie(selectedList, index)) },
                        onChange = { transform -> setRows(selectedList.mapIndexed { i, r -> if (i == index) transform(r) else r }) }
                    )
                }
            }
        }

        // Barra inferior fija: sigue visible cuando aparece el teclado (imePadding en la Column).
        Surface(tonalElevation = 3.dp, color = MaterialTheme.colorScheme.surfaceContainer) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { if (hasChanges) confirmExit = true else onCancel() }, modifier = Modifier.heightIn(min = HerculesSpacing.touch)) { Text("Cancelar") }
                Spacer(modifier = Modifier.weight(1f))
                val canShare = routineName.isNotBlank() && selectedList.isNotEmpty()
                IconButton(
                    onClick = { shareRoutineText(context, buildRoutineShareText(routineName, selectedList, availableExercises)) },
                    enabled = canShare,
                    modifier = Modifier.size(HerculesSpacing.touch)
                ) {
                    Icon(Icons.Default.Share, contentDescription = "Compartir rutina")
                }
                Spacer(modifier = Modifier.width(4.dp))
                Button(
                    onClick = { onSave(routineName, selectedList) },
                    enabled = canSave,
                    modifier = Modifier.heightIn(min = HerculesSpacing.touch)
                ) { Text("Guardar rutina") }
            }
        }
    }

    if (confirmExit) {
        AlertDialog(
            onDismissRequest = { confirmExit = false },
            title = { Text("Descartar cambios") },
            text = { Text("Tienes cambios sin guardar. Si sales ahora, se perderán.") },
            confirmButton = { TextButton(onClick = { confirmExit = false; onCancel() }) { Text("Salir sin guardar") } },
            dismissButton = { TextButton(onClick = { confirmExit = false }) { Text("Seguir aquí") } }
        )
    }
}

/** Tarjeta compacta de un ejercicio en el editor, con controles y acciones. */
@Composable
private fun EditorExerciseCard(
    row: RoutineExercise,
    exercise: Exercise?,
    position: Int,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    showGroupToggle: Boolean,
    isGroupedWithPrevious: Boolean,
    inBiserie: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit,
    onToggleGroup: () -> Unit,
    onChange: ((RoutineExercise) -> RoutineExercise) -> Unit
) {
    val isCardio = exercise?.isCardio == true
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = if (inBiserie) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text("$position", style = MaterialTheme.typography.labelMedium)
                }
                Column(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
                    Text(
                        exercise?.name ?: "Ejercicio desconocido",
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        if (isCardio) "Cardio" else "Fuerza",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onMoveUp, enabled = canMoveUp, modifier = Modifier.size(HerculesSpacing.touch)) {
                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Subir")
                }
                IconButton(onClick = onMoveDown, enabled = canMoveDown, modifier = Modifier.size(HerculesSpacing.touch)) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Bajar")
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(HerculesSpacing.touch)) {
                    Icon(Icons.Default.Delete, contentDescription = "Quitar ejercicio")
                }
            }
            if (showGroupToggle) {
                TextButton(onClick = onToggleGroup, modifier = Modifier.heightIn(min = HerculesSpacing.touch)) {
                    Text(if (isGroupedWithPrevious) "Desagrupar" else "Agrupar con el anterior")
                }
            }
            if (isCardio) {
                OutlinedTextField(
                    value = row.targetDurationMs?.let { (it / 60000L).toString() } ?: "",
                    onValueChange = { v ->
                        if (v.isBlank()) {
                            onChange { it.copy(targetDurationMs = null) }
                        } else {
                            v.toLongOrNull()?.coerceIn(0L, 1440L)?.let { minutes ->
                                onChange { it.copy(targetDurationMs = minutes * 60000L) }
                            }
                        }
                    },
                    label = { Text("Duración (min)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(0.6f)
                )
            } else {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = row.targetWarmups.toString(),
                        onValueChange = { v -> v.toIntOrNull()?.let { n -> onChange { it.copy(targetWarmups = n) } } },
                        label = { Text("Aprox.") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = row.targetSets.toString(),
                        onValueChange = { v -> v.toIntOrNull()?.let { n -> onChange { it.copy(targetSets = n) } } },
                        label = { Text("Series") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = row.targetReps.toString(),
                        onValueChange = { v -> v.toIntOrNull()?.let { n -> onChange { it.copy(targetReps = n) } } },
                        label = { Text("Reps mín.") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = (row.targetRestMs / 1000L).toString(),
                        onValueChange = { v -> v.toLongOrNull()?.let { s -> onChange { it.copy(targetRestMs = s * 1000L) } } },
                        label = { Text("Descanso (s)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = row.targetRepsMax?.toString().orEmpty(),
                    onValueChange = { v ->
                        if (v.isEmpty()) onChange { it.copy(targetRepsMax = null) }
                        else v.toIntOrNull()?.let { max -> onChange { it.copy(targetRepsMax = max) } }
                    },
                    label = { Text("Reps máx. (opcional)") },
                    isError = !validRepRange(row.targetReps, row.targetRepsMax),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = (row.targetDropsets ?: 0).toString(),
                    onValueChange = { v -> v.toIntOrNull()?.let { n -> onChange { it.copy(targetDropsets = n) } } },
                    label = { Text("Bajadas por serie") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(0.5f)
                )
            }
        }
    }
}

/** Contenedor visual para una biserie (dos ejercicios agrupados). */
@Composable
private fun BiserieContainer(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text("Biserie", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(4.dp))
            content()
        }
    }
}

/** Intercambia dos posiciones consecutivas de la lista del editor. */
private fun swapRows(rows: List<RoutineExercise>, a: Int, b: Int): List<RoutineExercise> {
    if (a !in rows.indices || b !in rows.indices) return rows
    val mutable = rows.toMutableList()
    val tmp = mutable[a]
    mutable[a] = mutable[b]
    mutable[b] = tmp
    return mutable
}

/**
 * Alterna la biserie en la posición [index]: si ya comparte grupo con la fila
 * anterior lo desagrupa; si no, agrupa ambas y desagrupa cualquier grupo previo.
 */
private fun toggleBiserie(rows: List<RoutineExercise>, index: Int): List<RoutineExercise> {
    if (index <= 0 || index >= rows.size) return rows
    val mutable = rows.toMutableList()
    val previous = mutable[index - 1]
    val current = mutable[index]
    if (current.groupId != null && current.groupId == previous.groupId) {
        val group = current.groupId
        for (i in mutable.indices) if (mutable[i].groupId == group) mutable[i] = mutable[i].copy(groupId = null)
    } else {
        val stale = setOfNotNull(current.groupId, previous.groupId)
        for (i in mutable.indices) if (mutable[i].groupId in stale) mutable[i] = mutable[i].copy(groupId = null)
        val group = UUID.randomUUID().toString()
        mutable[index - 1] = mutable[index - 1].copy(groupId = group)
        mutable[index] = mutable[index].copy(groupId = group)
    }
    return mutable
}