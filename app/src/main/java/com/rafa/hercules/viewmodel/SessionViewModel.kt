package com.rafa.hercules.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rafa.hercules.data.*
import com.rafa.hercules.utils.ITimerManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class ExerciseState(
    val exercise: Exercise,
    val series: List<Series>,
    val previousSeries: List<Series>,
    val targetDurationMs: Long? = null,
    val targetDropsets: Int = 0,
    val occurrenceId: String = exercise.id,
    val previousById: Map<String, Series> = emptyMap(),
    val generation: Long = LocalMutations.generation,
    val recordIds: Set<String> = emptySet(),
    val progression: ProgressionSuggestion? = null
)

class SessionViewModel(private val dao: GymDao, private val timerManager: ITimerManager) : ViewModel() {
    private val _activeSession = MutableStateFlow<Session?>(null)
    val activeSession = _activeSession.asStateFlow()
    private val _sessionName = MutableStateFlow("Entrenamiento")
    val sessionName = _sessionName.asStateFlow()
    private val _exercisesState = MutableStateFlow<List<ExerciseState>>(emptyList())
    val exercisesState = _exercisesState.asStateFlow()
    val substitutionCatalog = dao.observeActiveExercises().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    private val _activeRestEndTime = MutableStateFlow<Long?>(null)
    val activeRestEndTime = _activeRestEndTime.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    private var sessionCollectJob: Job? = null
    fun exactAlarmsAvailable(): Boolean = timerManager.exactAlarmsAvailable()
    fun clearError() { _error.value = null }

    private fun command(expected: Long = LocalMutations.generation, action: suspend () -> Unit) {
        if (LocalMutations.replacing.value) { _error.value = "Espera a que termine la restauración."; return }
        val generation = expected
        viewModelScope.launch {
            try { if (!LocalMutations.run(generation, action)) _error.value = "Los datos cambiaron al importar. Vuelve a abrir la sesión." }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { _error.value = e.message ?: "No se pudo guardar el cambio." }
        }
    }

    fun startSession(routineId: String? = null, onStarted: () -> Unit = {}) = command {
        val existing = dao.getActiveSession()
        if (existing != null) { loadSessionData(existing.id); onStarted(); return@command }
        val routine = routineId?.let { dao.getRoutineById(it) }
        require(routineId == null || routine != null) { "La rutina ya no existe." }
        val config = routineId?.let { dao.getRoutineExercises(it) }.orEmpty()
        val session = Session(routineId = routineId, routineSnapshot = routine?.let { buildRoutineSnapshot(it.name, config) })
        val rows = config.flatMap { re ->
            val ex = dao.getExerciseById(re.exerciseId) ?: error("El ejercicio ya no existe.")
            if (ex.isCardio) listOf(Series(sessionId = session.id, exerciseId = ex.id,
                occurrenceId = re.id, exerciseOrder = re.orderIndex, roundIndex = 0, stageIndex = 0,
                unit = ex.defaultUnit, targetDurationMs = re.targetDurationMs, targetRestMs = 0L))
            else (0 until re.targetWarmups + re.targetSets).map { i ->
                val warmup = i < re.targetWarmups
                Series(sessionId = session.id, exerciseId = ex.id, isWarmup = warmup,
                    orderIndex = i, occurrenceId = re.id, exerciseOrder = re.orderIndex,
                    roundIndex = if (warmup) i else i - re.targetWarmups, stageIndex = 0,
                    unit = ex.defaultUnit, groupId = re.groupId, targetReps = re.targetReps,
                    targetRepsMax = if (warmup) null else re.targetRepsMax,
                    targetRestMs = re.targetRestMs, targetDropsets = if (warmup) 0 else re.dropsetStages)
            }
        }
        val created = dao.createSessionIfNone(session, rows)
        loadSessionData(created.id)
        onStarted()
    }

    fun resumeSession(sessionId: String? = null) = command {
        val session = sessionId?.let { dao.getSessionById(it) } ?: dao.getActiveSession()
        if (session != null) loadSessionData(session.id) else clearSession()
    }

    fun loadSessionData(sessionId: String) {
        sessionCollectJob?.cancel()
        sessionCollectJob = viewModelScope.launch {
            try {
                val session = dao.getSessionById(sessionId)
                _sessionName.value = session?.routineSnapshot?.takeIf { it.startsWith("v1;") }
                    ?.removePrefix("v1;")?.substringBeforeLast(';')
                    ?: session?.routineId?.let { dao.getRoutineById(it)?.name }
                    ?: if (session?.routineId == null) "Sesión libre" else "Entrenamiento"
                _activeSession.value = session
                _activeRestEndTime.value = if (session?.state == "ACTIVE") timerManager.restEndTime(sessionId) else null
                if (session == null) { _exercisesState.value = emptyList(); return@launch }
                val recordHistory = if (session.state == "ACTIVE")
                    dao.observeRecordHistory(sessionId, session.startTime)
                else flowOf(emptyList())
                combine(dao.getSeriesForSession(sessionId), dao.observeExercises(), recordHistory, ::Triple)
                    .collect { (rows, catalog, history) ->
                        if (LocalMutations.replacing.value) return@collect
                        val generation = LocalMutations.generation
                        val records = if (session.state == "ACTIVE")
                            personalRecordIds(history, rows, catalog.filterNot { it.isCardio }.map { it.id }.toSet())
                        else emptySet()
                        val priorRows = mutableMapOf<String, List<Series>>()
                        val states = rows.groupBy { it.occurrenceKey() }.values.sortedBy { group -> group.first().exerciseOrder ?: Int.MAX_VALUE }.mapNotNull { group ->
                            val first = group.first()
                            val ex = catalog.find { it.id == first.exerciseId } ?: return@mapNotNull null
                            val previous = dao.getPreviousSeriesForExercise(ex.id, session.id, session.startTime)
                            val progression = if (session.state == "ACTIVE" && !ex.isCardio &&
                                group.any { it.targetRepsMax != null } && !group.hasRecordedValues() && !group.hasCompletedSeries())
                                progressionFromPrevious(session, group, previous, priorRows)
                            else null
                            ExerciseState(ex, group, previous, first.targetDurationMs,
                                first.targetDropsets ?: 0, first.occurrenceKey(),
                                group.mapNotNull { row -> previousFor(row, rows.filter { it.exerciseId == ex.id }, previous)?.let { row.id to it } }.toMap(), generation,
                                group.mapNotNull { it.id.takeIf(records::contains) }.toSet(), progression)
                        }
                        if (generation == LocalMutations.generation && !LocalMutations.replacing.value) _exercisesState.value = states
                    }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { _error.value = e.message ?: "No se pudo cargar la sesión." }
        }
    }

    private suspend fun progressionFromPrevious(
        session: Session, planned: List<Series>, previous: List<Series>, cache: MutableMap<String, List<Series>>
    ): ProgressionSuggestion? {
        val row = planned.firstOrNull() ?: return null
        val previousSession = previous.firstOrNull()?.sessionId ?: return null
        if (session.routineId == null || dao.getSessionById(previousSession)?.routineId != session.routineId) return null
        val prior = cache.getOrPut(previousSession) { dao.getSessionSeries(previousSession) }
            .filter { it.exerciseId == row.exerciseId }
            .groupBy { it.occurrenceKey() }.values
            .singleOrNull { it.first().exerciseOrder == row.exerciseOrder && it.first().groupId == row.groupId }
            ?: return null
        return suggestProgression(planned, prior)
    }

    fun applyProgression(state: ExerciseState) = command(state.generation) {
        val suggestion = state.progression ?: return@command
        val sessionId = state.series.firstOrNull()?.sessionId ?: return@command
        val session = dao.getSessionById(sessionId) ?: return@command
        require(session.state == "ACTIVE") { "La sesión ya terminó." }
        val rows = dao.getSessionSeries(sessionId).filter { it.occurrenceKey() == state.occurrenceId }
        require(rows.isNotEmpty() && rows.all { it.exerciseId == state.exercise.id } &&
            !rows.hasRecordedValues() && !rows.hasCompletedSeries()) { "Este bloque ya cambió." }
        val previous = dao.getPreviousSeriesForExercise(state.exercise.id, session.id, session.startTime)
        require(progressionFromPrevious(session, rows, previous, mutableMapOf()) == suggestion) { "La sugerencia cambió." }
        dao.updateSeriesRows(rows.filter { !it.isWarmup && it.parentId == null }.map {
            val kg = seriesWeightKg(suggestion.suggested, suggestion.unit)
            val weight = if (it.unit == suggestion.unit) suggestion.suggested else if (it.unit == "lb") kg / LB_TO_KG else kg
            it.copy(weight = weight)
        })
    }

    /** La transformación recibe la última fila persistida, nunca una copia antigua de Compose. */
    fun editSeries(id: String, change: (Series) -> Series, expected: Long = LocalMutations.generation, expectedExerciseId: String? = null) = command(expected) {
        val current = dao.getSeriesById(id) ?: return@command
        require(expectedExerciseId == null || current.exerciseId == expectedExerciseId) { "El ejercicio cambió. Vuelve a abrir la serie." }
        val proposed = change(current)
        require(!proposed.isCompleted || current.isCompleted) { "Completa las series con la acción de finalización." }
        val updated = current.copy(weight = proposed.weight, unit = proposed.unit, reps = proposed.reps,
            effort = proposed.effort, effortScale = proposed.effortScale, durationMs = proposed.durationMs,
            isCompleted = proposed.isCompleted, notes = proposed.notes)
        require(proposed.id == current.id && proposed.sessionId == current.sessionId && proposed.exerciseId == current.exerciseId) { "La identidad del registro no puede cambiar." }
        validateSeriesChange(current, updated)
        dao.updateSeries(updated)
    }

    fun completeSeries(series: Series, expected: Long = LocalMutations.generation) = command(expected) {
        val current = dao.getSeriesById(series.id) ?: return@command
        require(current.exerciseId == series.exerciseId) { "El ejercicio cambió. Vuelve a abrir la serie." }
        if (current.isCompleted) return@command
        validateSeriesInput(current)
        val session = dao.getSessionById(current.sessionId) ?: return@command
        val ex = dao.getExerciseById(current.exerciseId)
        require(if (ex?.isCardio == true) current.durationMs != null else current.reps != null) {
            if (ex?.isCardio == true) "Registra los minutos realizados antes de completar." else "Registra las repeticiones realizadas antes de completar."
        }
        val updated = current.copy(isCompleted = true, completedAt = current.completedAt ?: System.currentTimeMillis())
        dao.updateSeries(updated)
        if (session.state != "ACTIVE" || current.completedAt != null || ex?.isCardio == true) return@command
        val rows = dao.getSeriesForSession(session.id).first()
        val parent = rows.find { it.id == (updated.parentId ?: updated.id) } ?: return@command
        val roots = completedRound(parent, rows) ?: return@command
        val duration = roots.maxOf { it.targetRestMs ?: dao.getRoutineExerciseForSession(session.id, it.exerciseId)?.targetRestMs ?: 90000L }
        require(duration in 0L..86400000L) { "El descanso guardado está fuera de rango." }
        val now = System.currentTimeMillis()
        // LocalMutations serializa todos los eventos; el marcador impide reiniciar una ronda.
        dao.markRoundRest(roots, now)
        if (duration > 0) {
            timerManager.setSessionRestAlarm(session.id, duration)
            _activeRestEndTime.value = now + duration
        }
    }

    fun addDropset(parentSeries: Series, expected: Long = LocalMutations.generation) = command(expected) {
        val parent = dao.getSeriesById(parentSeries.id) ?: return@command
        require(parent.exerciseId == parentSeries.exerciseId) { "El ejercicio cambió. Vuelve a abrir la serie." }
        val session = dao.getSessionById(parent.sessionId) ?: return@command
        require(parent.parentId == null && !parent.isWarmup && session.state == "ACTIVE") { "Las bajadas se añaden a una serie de trabajo activa." }
        require(dao.getExerciseById(parent.exerciseId)?.isCardio != true) { "El cardio no admite dropsets." }
        require(!parent.isCompleted) { "Añade las bajadas antes de completar la serie principal." }
        val rows = dao.getSeriesForSession(parent.sessionId).first()
        require(roundRoots(parent, rows).none { it.blockRestStartedAt != null }) { "La ronda ya inició su descanso." }
        val stage = rows.filter { it.parentId == parent.id }.maxOfOrNull { it.stageIndex ?: 0 }?.plus(1) ?: 1
        dao.insertSeries(parent.copy(id = java.util.UUID.randomUUID().toString(), parentId = parent.id,
            stageIndex = stage, weight = null, reps = null, effort = null, effortScale = null,
            completedAt = null, isCompleted = false, blockRestStartedAt = null, notes = null))
    }

    fun substituteExercise(state: ExerciseState, replacementId: String, discardConfirmed: Boolean, onSaved: () -> Unit = {}) = command(state.generation) {
        val sessionId = state.series.firstOrNull()?.sessionId ?: return@command
        dao.substituteSessionExercise(sessionId, state.occurrenceId, state.exercise.id, replacementId, discardConfirmed)
        onSaved()
    }

    fun skipRest() { timerManager.cancelRestAlarm(); _activeRestEndTime.value = null }
    // Vencer la UI no cancela una entrega pendiente del sistema.
    fun restRemainingMs(sessionId: String, now: Long): Long = timerManager.remainingRestMs(sessionId)
        ?: (_activeRestEndTime.value?.let { (it - now).coerceAtLeast(0L) } ?: 0L)
    fun refreshAfterImport() {
        skipRest()
        command {
            val active = dao.getActiveSession()
            if (active != null) loadSessionData(active.id) else clearSession()
        }
    }
    private fun clearSession() {
        sessionCollectJob?.cancel(); sessionCollectJob = null
        _activeSession.value = null; _exercisesState.value = emptyList()
        _activeRestEndTime.value = null
    }
    private val _finishedSummary = MutableStateFlow<FinishedWorkout?>(null)
    val finishedSummary = _finishedSummary.asStateFlow()
    fun dismissSummary() { _finishedSummary.value = null }

    fun finishSession(effort: Int? = null, onSaved: () -> Unit = {}) = closeSession("FINISHED", effort, onSaved)
    fun cancelSession(onSaved: () -> Unit = {}) = closeSession("CANCELLED", null, onSaved)
    private fun closeSession(state: String, effort: Int?, onSaved: () -> Unit) = command {
        val id = _activeSession.value?.id ?: return@command
        val session = dao.getSessionById(id) ?: return@command
        require(session.state == "ACTIVE") { "Esta sesión ya terminó." }
        require(effort == null || effort in 0..10) { "El esfuerzo general debe estar entre 0 y 10." }
        val end = System.currentTimeMillis()
        val summary = if (state == "FINISHED") {
            val rows = dao.getSessionSeries(id)
            val catalog = dao.getAllExercises().associateBy { it.id }
            val history = dao.observeRecordHistory(id, session.startTime).first()
            val records = personalRecordIds(history, rows, catalog.values.filterNot { it.isCardio }.map { it.id }.toSet())
            FinishedWorkout(_sessionName.value, (end - session.startTime).coerceAtLeast(0),
                buildSessionSummary(rows) { catalog[it]?.isCardio == true }, rows.filter { it.id in records }.map {
                    WorkoutRecord(catalog.getValue(it.exerciseId).name, requireNotNull(it.weight), it.unit, requireNotNull(it.reps))
                })
        } else null
        dao.updateSession(session.copy(state = state, endTime = end, overallEffort = effort))
        _finishedSummary.value = summary
        skipRest(); clearSession(); onSaved()
    }
}
