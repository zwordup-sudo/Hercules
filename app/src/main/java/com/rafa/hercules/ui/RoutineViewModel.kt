package com.rafa.hercules.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rafa.hercules.data.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate

class RoutineViewModel(private val dao: GymDao) : ViewModel() {
    // Inicio y selector semanal: solo rutinas no archivadas (el histórico se ve en Progreso).
    val routines = dao.observeActiveRoutines().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    // Catálogo de selección: solo ejercicios no archivados (los históricos siguen resolviéndose en sesión e historial).
    val availableExercises = dao.observeActiveExercises().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    // Sesión en curso para ofrecer "Reanudar" en Inicio.
    val activeSession = dao.observeActiveSession().stateIn(viewModelScope, SharingStarted.Eagerly, null)
    // Contadores reales por tarjeta: ejercicios y series de trabajo configurados.
    private val allExercises = dao.observeExercises().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private val routineRows = dao.observeRoutineExercises().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val routineStats: StateFlow<Map<String, RoutineStats>> =
        combine(routines, routineRows, allExercises) { list, rows, catalog ->
            val cardio = catalog.filter { it.isCardio }.mapTo(mutableSetOf()) { it.id }
            val activeIds = list.mapTo(mutableSetOf()) { it.id }
            rows.filter { it.routineId in activeIds }
                .groupBy { it.routineId }
                .mapValues { (_, rs) -> routineStats(rs) { exId -> exId in cardio } }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())
    // Constancia semanal de Inicio: días únicos con entrenamiento completado.
    private val finishedSessions = dao.getFinishedSessionsWithSeries().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private val goalChanges = dao.getGoalChanges().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private val today = MutableStateFlow(LocalDate.now())
    val weeklyConsistency = combine(finishedSessions, goalChanges, today) { sessions, goals, date ->
        calculateWeeklyConsistency(sessions, goals, date)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, WeeklyConsistency())
    fun refreshToday() { today.value = LocalDate.now() }
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    private fun command(expected: Long = LocalMutations.generation, action: suspend () -> Unit) {
        if (LocalMutations.replacing.value) { _error.value = "Espera a que termine la restauración."; return }
        val generation = expected
        viewModelScope.launch {
            try { if (!LocalMutations.run(generation, action)) _error.value = "Los datos cambiaron. Reabre el editor antes de guardar." }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { _error.value = e.message ?: "No se pudo guardar el cambio." }
        }
    }
    init { command { dao.seedExercisesIfEmpty() } }
    fun addCustomExercise(name: String, type: String? = null) = command {
        require(name.isNotBlank()) { "Escribe un nombre de ejercicio." }
        dao.insertExercise(Exercise(name = name.trim(), type = type, origin = "USER"))
    }
    /** Archiva el ejercicio; los bloqueos (inicial o en uso) llegan a error con el detalle. */
    fun archiveExercise(exerciseId: String) = command {
        dao.archiveExercise(exerciseId, System.currentTimeMillis())
    }
    /** Archiva la rutina; si tiene sesión activa el bloqueo llega a error. */
    fun archiveRoutine(routineId: String) = command {
        dao.archiveRoutine(routineId, System.currentTimeMillis())
    }
    suspend fun configuration(routineId: String) = dao.getRoutineExercises(routineId)
    suspend fun buildShareText(routine: Routine) = buildRoutineShareText(routine.name, dao.getRoutineExercises(routine.id), dao.getAllExercises())
    fun saveRoutine(name: String, rows: List<RoutineExercise>, existingId: String? = null, expected: Long = LocalMutations.generation, onSaved: () -> Unit = {}) = command(expected) {
        require(name.isNotBlank()) { "Escribe un nombre de rutina." }
        // Catálogo activo: una rutina no puede guardar ejercicios archivados en paralelo.
        val catalog = dao.getAllExercises().filter { it.archivedAt == null }.associateBy { it.id }
        validateRoutine(rows) { catalog[it]?.isCardio == true }
        require(rows.all { it.exerciseId in catalog }) { "El catálogo cambió. Revisa los ejercicios antes de guardar." }
        val normalized = normalizeRoutineGroups(rows)
        require(normalized.filter { it.groupId != null }.none { catalog[it.exerciseId]?.isCardio == true }) { "El cardio no se agrupa en biseries." }
        val old = existingId?.let { dao.getRoutineById(it) }
        require(existingId == null || old != null) { "La rutina ya no existe." }
        val routine = old?.copy(name = name.trim()) ?: Routine(name = name.trim())
        dao.saveRoutineTemplate(routine, normalized.map { it.copy(routineId = routine.id) })
        onSaved()
    }
    fun assignDay(id: String, day: Int?) = command {
        require(day == null || day in 1..7)
        dao.getRoutineById(id)?.let { dao.updateRoutine(it.copy(assignedDay = day)) }
    }
}
