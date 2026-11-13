package com.rafa.hercules.viewmodel

import com.rafa.hercules.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class SessionSubstitutionTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var dao: FakeGymDao
    private lateinit var timer: FakeTimerManager
    private lateinit var vm: SessionViewModel
    @Before fun setup() {
        Dispatchers.setMain(dispatcher)
        dao = FakeGymDao(); timer = FakeTimerManager(); vm = SessionViewModel(dao, timer)
    }
    @After fun teardown() { Dispatchers.resetMain() }

    private suspend fun prepare() {
        dao.insertExercises(listOf(Exercise(id = "a", name = "Original"), Exercise(id = "b", name = "Sustituto", defaultUnit = "lb")))
        dao.insertRoutine(Routine(id = "r", name = "Rutina"))
        dao.insertRoutineExercises(listOf(
            RoutineExercise(id = "first", routineId = "r", exerciseId = "a", orderIndex = 0, targetSets = 2, targetWarmups = 1, targetRestMs = 120000, groupId = "g", targetDropsets = 1),
            RoutineExercise(id = "second", routineId = "r", exerciseId = "a", orderIndex = 1, targetSets = 2)))
        vm.startSession("r"); dispatcher.scheduler.advanceUntilIdle()
    }

    @Test fun `sustituye solo una aparicion conserva estructura y plantilla`() = runTest {
        prepare()
        val state = vm.exercisesState.value.first()
        val root = state.series.first { !it.isWarmup }
        vm.addDropset(root); dispatcher.scheduler.advanceUntilIdle()
        val rows = dao.getSessionSeries(root.sessionId).filter { it.occurrenceKey() == "first" }
        rows.forEach { vm.editSeries(it.id, { s -> s.copy(weight = 20.0, reps = 8, effort = 3, effortScale = "RIR", notes = "Viejo") }) }
        dispatcher.scheduler.advanceUntilIdle()
        val before = dao.getSessionSeries(root.sessionId)
        val config = dao.routineExercises.toList()
        val snapshot = dao.sessions.single().routineSnapshot
        vm.substituteExercise(state, "b", true); dispatcher.scheduler.advanceUntilIdle()
        assertNull(vm.error.value)
        val replaced = dao.getSessionSeries(root.sessionId).filter { it.occurrenceKey() == "first" }
        assertEquals(substituteRows(before.filter { it.occurrenceKey() == "first" }, dao.exercises.first { it.id == "b" }), replaced)
        assertEquals(before.filter { it.occurrenceKey() == "second" }, dao.series.filter { it.occurrenceKey() == "second" })
        assertEquals(config, dao.routineExercises)
        assertNotEquals(snapshot, dao.sessions.single().routineSnapshot)
        assertTrue(dao.sessions.single().routineSnapshot!!.contains("b/0/"))
        assertTrue(dao.sessions.single().routineSnapshot!!.contains("a/1/"))
        assertEquals(0, timer.alarmSetCount)
        val backup = BackupData(dao.routines.toList(), dao.exercises.toList(), dao.routineExercises.toList(), dao.sessions.toList(), dao.series.toList(), dao.goalChanges.toList())
        assertEquals(backup, BackupCodec.parse(BackupCodec.serialize(backup.routines, backup.exercises,
            backup.routineExercises, backup.sessions, backup.series, backup.goals)))
    }

    @Test fun `bloquea trabajo aproximacion y bajada completadas incluso si se desmarcan`() = runTest {
        prepare()
        val state = vm.exercisesState.value.first()
        val root = state.series.first { !it.isWarmup }
        vm.addDropset(root); dispatcher.scheduler.advanceUntilIdle()
        val candidates = dao.series.filter { it.occurrenceKey() == "first" }.toList()
        for (row in candidates) {
            dao.updateSeries(row.copy(isCompleted = true))
            val before = dao.series.toList()
            vm.substituteExercise(state, "b", true); dispatcher.scheduler.advanceUntilIdle()
            assertEquals(before, dao.series)
            assertTrue(vm.error.value!!.contains("completada"))
            dao.updateSeries(row)
        }
        dao.updateSeries(root.copy(completedAt = 123))
        vm.substituteExercise(state, "b", true); dispatcher.scheduler.advanceUntilIdle()
        assertTrue(vm.error.value!!.contains("completada"))
    }

    @Test fun `revalida completado y descarte al guardar no solo al abrir menu`() = runTest {
        prepare()
        val state = vm.exercisesState.value.first()
        val root = state.series.first()
        vm.editSeries(root.id, { it.copy(reps = 8) })
        vm.substituteExercise(state, "b", false)
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(vm.error.value!!.contains("Confirma"))
        vm.completeSeries(root)
        vm.substituteExercise(state, "b", true)
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(vm.error.value!!.contains("completada"))
        assertTrue(dao.series.all { it.exerciseId == "a" })
    }

    @Test fun `rechaza historico sustituto archivado tipo distinto y orden obsoleta`() = runTest {
        prepare()
        val state = vm.exercisesState.value.first()
        dao.insertExercise(Exercise(id = "c", name = "Cardio", type = "CARDIO"))
        dao.insertExercise(Exercise(id = "arch", name = "Archivado", archivedAt = 1))
        for (id in listOf("c", "arch", "a", "missing")) {
            vm.substituteExercise(state, id, true); dispatcher.scheduler.advanceUntilIdle()
            assertTrue(dao.series.all { it.exerciseId == "a" }); assertNotNull(vm.error.value)
        }
        vm.substituteExercise(state, "b", true); dispatcher.scheduler.advanceUntilIdle()
        vm.editSeries(state.series.first().id, { it.copy(weight = 99.0) }, expectedExerciseId = "a")
        vm.completeSeries(state.series.first())
        vm.substituteExercise(state, "a", true)
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(dao.series.filter { it.occurrenceKey() == "first" }.all { it.exerciseId == "b" && it.weight == null && !it.isCompleted })
        assertEquals(0, timer.alarmSetCount)
        vm.finishSession(); dispatcher.scheduler.advanceUntilIdle()
        vm.substituteExercise(vm.exercisesState.value.firstOrNull() ?: state, "a", true)
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(vm.error.value!!.contains("activa"))
    }

    @Test fun `reanudar recalcula anterior del sustituto y su historial conserva ejercicio real`() = runTest {
        prepare()
        val state = vm.exercisesState.value.first()
        val session = dao.sessions.single()
        dao.insertSession(Session(id = "old", routineId = null, startTime = session.startTime - 10000, endTime = session.startTime - 5000, state = "FINISHED"))
        val previous = Series(id = "prev", sessionId = "old", exerciseId = "b", occurrenceId = "previous", roundIndex = 0, reps = 9, weight = 45.359237, isCompleted = true)
        dao.insertSeries(previous)
        vm.substituteExercise(state, "b", true); dispatcher.scheduler.advanceUntilIdle()
        val reopened = SessionViewModel(dao, timer)
        reopened.resumeSession(session.id); dispatcher.scheduler.advanceUntilIdle()
        val actual = reopened.exercisesState.value.first()
        assertEquals("b", actual.exercise.id)
        val row = actual.series.first { !it.isWarmup && it.parentId == null && it.roundIndex == 0 }
        assertEquals(previous, actual.previousById[row.id])
        reopened.editSeries(row.id, { copyPreviousValues(it, actual.previousById.getValue(row.id)) })
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(100.0, dao.getSeriesById(row.id)!!.weight!!, 0.00000001)
        assertFalse(dao.getSeriesById(row.id)!!.isCompleted); assertEquals(0, timer.alarmSetCount)
        reopened.completeSeries(dao.getSeriesById(row.id)!!); reopened.finishSession()
        dispatcher.scheduler.advanceUntilIdle()
        val history = dao.getFinishedSessionsWithSeries().first().first { it.session.id == session.id }
        assertEquals("b", history.series.first { it.id == row.id }.exerciseId)
        assertEquals(previous, dao.getSeriesById("prev"))
    }

    @Test fun `cardio se sustituye sin transferir minutos y catalogo filtra tipo y activos`() = runTest {
        val a = Exercise(id = "a", name = "Caminar", type = "CARDIO")
        val b = a.copy(id = "b", name = "Bici")
        val archived = b.copy(id = "arch", archivedAt = 1)
        val strength = Exercise(id = "strength", name = "Press")
        dao.insertExercises(listOf(a, b, archived, strength))
        assertEquals(listOf(b), substitutionCandidates(dao.exercises, a))
        dao.insertSession(Session(id = "s", routineId = null))
        val row = Series(id = "row", sessionId = "s", exerciseId = "a", occurrenceId = "cardio", durationMs = 60000, targetDurationMs = 120000, targetRestMs = 0)
        dao.insertSeries(row)
        dao.substituteSessionExercise("s", "cardio", "a", "b", true)
        assertEquals(row.copy(exerciseId = "b", durationMs = null), dao.getSeriesById("row"))
    }

    @Test fun `sustitucion dentro de biserie con dropset conserva un solo descanso de ronda`() = runTest {
        dao.insertExercises(listOf(Exercise(id = "a", name = "A"), Exercise(id = "b", name = "B"), Exercise(id = "c", name = "C")))
        dao.insertSession(Session(id = "s", routineId = null))
        val a = Series(id = "a", sessionId = "s", exerciseId = "a", occurrenceId = "first", groupId = "g", roundIndex = 0, targetRestMs = 60000)
        val partner = a.copy(id = "partner", exerciseId = "c", occurrenceId = "second", targetRestMs = 120000)
        dao.insertSeries(listOf(a, partner))
        vm.resumeSession("s"); dispatcher.scheduler.advanceUntilIdle()
        vm.addDropset(a); dispatcher.scheduler.advanceUntilIdle()
        vm.substituteExercise(vm.exercisesState.value.first { it.occurrenceId == "first" }, "b", true)
        dispatcher.scheduler.advanceUntilIdle()
        val rows = dao.series.toList()
        rows.forEach { vm.editSeries(it.id, { s -> s.copy(reps = 8) }) }
        vm.completeSeries(dao.getSeriesById("a")!!); vm.completeSeries(partner)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(0, timer.alarmSetCount)
        val drop = dao.series.first { it.parentId == "a" }
        vm.completeSeries(drop); vm.completeSeries(drop)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, timer.alarmSetCount); assertEquals(120000L, timer.lastDuration)
    }
}
