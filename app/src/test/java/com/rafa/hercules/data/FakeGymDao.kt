package com.rafa.hercules.data

import kotlinx.coroutines.flow.*
import kotlinx.coroutines.yield

/** Emite invalidaciones y suspende escrituras; no sustituye pruebas con Room real. */
class FakeGymDao : GymDao {
    val exercises = mutableListOf<Exercise>()
    val sessions = mutableListOf<Session>()
    val series = mutableListOf<Series>()
    val goalChanges = mutableListOf(GoalChange("default", 0, 3))
    val routines = mutableListOf<Routine>()
    val routineExercises = mutableListOf<RoutineExercise>()
    private val revision = MutableStateFlow(0L)
    private fun changed() { revision.value++ }
    private fun <T> observe(read: () -> T): Flow<T> = revision.map { read() }.distinctUntilChanged()
    override suspend fun insertExercise(exercise: Exercise) { yield(); exercises.removeAll { it.id == exercise.id }; exercises.add(exercise); changed() }
    override suspend fun updateExercise(exercise: Exercise) { yield(); exercises.replaceAll { if (it.id == exercise.id) exercise else it }; changed() }
    override suspend fun insertRoutine(routine: Routine) { yield(); routines.removeAll { it.id == routine.id }; routines.add(routine); changed() }
    override suspend fun updateRoutine(routine: Routine) { yield(); routines.replaceAll { if (it.id == routine.id) routine else it }; changed() }
    override suspend fun insertSession(session: Session) { yield(); sessions.removeAll { it.id == session.id }; sessions.add(session); changed() }
    override suspend fun updateSession(session: Session) { yield(); sessions.replaceAll { if (it.id == session.id) session else it }; changed() }
    override suspend fun insertSeries(series: Series) { yield(); this.series.removeAll { it.id == series.id }; this.series.add(series); changed() }
    override suspend fun updateSeries(series: Series) { yield(); this.series.replaceAll { if (it.id == series.id) series else it }; changed() }
    override suspend fun updateSeriesRows(rows: List<Series>) { yield(); val byId = rows.associateBy { it.id }; series.replaceAll { byId[it.id] ?: it }; changed() }
    override suspend fun getSessionSeries(sessionId: String) = series.filter { it.sessionId == sessionId }
    override fun getSeriesForSession(sessionId: String) = observe { series.filter { it.sessionId == sessionId }.sortedWith(compareBy({ it.exerciseOrder }, { it.orderIndex }, { it.stageIndex }, { it.id })) }
    override suspend fun getSeriesById(id: String) = series.find { it.id == id }
    override suspend fun getSessionById(sessionId: String) = sessions.find { it.id == sessionId }
    override suspend fun getExerciseById(exerciseId: String) = exercises.find { it.id == exerciseId }
    override suspend fun getAllExercises() = exercises.toList()
    override fun observeExercises() = observe { exercises.toList() }
    override fun observeActiveExercises() = observe { exercises.filter { it.archivedAt == null } }
    override suspend fun activeRoutinesUsingExercise(exerciseId: String) =
        routineExercises.filter { it.exerciseId == exerciseId }
            .mapNotNull { re -> routines.find { it.id == re.routineId && it.archivedAt == null }?.name }
            .sorted()
    override suspend fun getActiveSession() = sessions.filter { it.state == "ACTIVE" }.maxByOrNull { it.startTime }
    override fun observeActiveSession() = observe { sessions.filter { it.state == "ACTIVE" }.maxByOrNull { it.startTime } }
    override fun observeRoutineExercises() = observe { routineExercises.toList() }
    override fun getFinishedSessions() = observe { sessions.filter { it.state == "FINISHED" }.sortedByDescending { it.startTime } }
    override fun getFinishedSessionsWithSeries() = observe { sessions.filter { it.state == "FINISHED" }.sortedByDescending { it.startTime }.map { session -> SessionWithSeries(session, series.filter { it.sessionId == session.id }) } }
    override fun observeRecordHistory(sessionId: String, beforeStartTime: Long) = observe {
        val earlier = sessions.filter { it.state == "FINISHED" && it.id != sessionId && it.startTime < beforeStartTime }.map { it.id }.toSet()
        val exercises = series.filter { it.sessionId == sessionId }.map { it.exerciseId }.toSet()
        series.filter { row ->
            row.sessionId in earlier && row.exerciseId in exercises && row.isCompleted && !row.isWarmup &&
                row.parentId == null && (row.weight ?: 0.0) > 0 && (row.reps ?: 0) > 0 &&
                series.none { it.sessionId == row.sessionId && it.parentId == row.id }
        }
    }
    override fun getGoalChanges() = observe { goalChanges.sortedBy { it.timestamp } }
    override suspend fun insertGoalChange(goalChange: GoalChange) { yield(); goalChanges.add(goalChange); changed() }
    override suspend fun getPreviousSeriesForExercise(exerciseId: String, excludingSessionId: String, beforeStartTime: Long): List<Series> {
        val prior = sessions.filter { s -> s.state == "FINISHED" && s.id != excludingSessionId && s.startTime < beforeStartTime && series.any { it.sessionId == s.id && it.exerciseId == exerciseId && it.isCompleted } }.maxByOrNull { it.endTime ?: Long.MIN_VALUE } ?: return emptyList()
        return series.filter { it.exerciseId == exerciseId && it.sessionId == prior.id && it.isCompleted }.sortedWith(compareBy({ it.exerciseOrder }, { it.orderIndex }, { it.stageIndex }, { it.id }))
    }
    override suspend fun insertRoutineExercise(routineExercise: RoutineExercise) { yield(); routineExercises.removeAll { it.id == routineExercise.id }; routineExercises.add(routineExercise); changed() }
    override suspend fun getRoutineExercises(routineId: String) = routineExercises.filter { it.routineId == routineId }.sortedBy { it.orderIndex }
    override suspend fun deleteRoutineExercises(routineId: String) { routineExercises.removeAll { it.routineId == routineId }; changed() }
    override fun getAllRoutines() = observe { routines.toList() }
    override fun observeActiveRoutines() = observe { routines.filter { it.archivedAt == null } }
    override suspend fun getRoutineById(routineId: String) = routines.find { it.id == routineId }
    override suspend fun getActiveSessionForRoutine(routineId: String) =
        sessions.find { it.routineId == routineId && it.state == "ACTIVE" }
    override suspend fun getRoutineExerciseForSession(sessionId: String, exerciseId: String): RoutineExercise? {
        val session = getSessionById(sessionId) ?: return null
        return routineExercises.filter { it.routineId == session.routineId && it.exerciseId == exerciseId }.minByOrNull { it.orderIndex }
    }
    override suspend fun getAllRoutinesSync() = routines.toList()
    override suspend fun getAllExercisesSync() = exercises.toList()
    override suspend fun getAllRoutineExercisesSync() = routineExercises.toList()
    override suspend fun getAllSessionsSync() = sessions.toList()
    override suspend fun getAllSeriesSync() = series.toList()
    override suspend fun getAllGoalChangesSync() = goalChanges.toList()
    override suspend fun insertRoutines(routines: List<Routine>) { routines.forEach { insertRoutine(it) } }
    override suspend fun insertExercises(exercises: List<Exercise>) { exercises.forEach { insertExercise(it) } }
    override suspend fun insertRoutineExercises(routineExercises: List<RoutineExercise>) { routineExercises.forEach { insertRoutineExercise(it) } }
    override suspend fun insertSessions(sessions: List<Session>) { sessions.forEach { insertSession(it) } }
    override suspend fun insertSeries(series: List<Series>) { series.forEach { insertSeries(it) } }
    override suspend fun clearRoutines() { routines.clear(); changed() }
    override suspend fun clearExercises() { exercises.clear(); changed() }
    override suspend fun clearRoutineExercises() { routineExercises.clear(); changed() }
    override suspend fun clearSessions() { sessions.clear(); changed() }
    override suspend fun clearSeries() { series.clear(); changed() }
    override suspend fun clearGoalChanges() { goalChanges.clear(); changed() }
}
