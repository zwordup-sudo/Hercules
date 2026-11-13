package com.rafa.hercules.viewmodel

import com.rafa.hercules.data.Exercise
import com.rafa.hercules.data.FakeGymDao
import com.rafa.hercules.data.Series
import com.rafa.hercules.utils.ITimerManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class FakeTimerManager : ITimerManager {
    var alarmSetCount = 0
    var lastDuration = 0L
    var cancelCount = 0
    var now = System.currentTimeMillis()
    private var restSession: String? = null
    private var deadline: Long? = null

    override fun setSessionRestAlarm(sessionId: String, durationMs: Long) {
        restSession = sessionId; deadline = now + durationMs; setRestAlarm(durationMs)
    }
    override fun restEndTime(sessionId: String): Long? = deadline.takeIf { restSession == sessionId }
    override fun remainingRestMs(sessionId: String): Long? = restEndTime(sessionId)?.let { (it - now).coerceAtLeast(0L) }

    override fun setRestAlarm(durationMs: Long, requestCode: Int) {
        alarmSetCount++
        lastDuration = durationMs
    }
    override fun cancelRestAlarm(requestCode: Int) {
        cancelCount++
        restSession = null; deadline = null
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class SessionViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var dao: FakeGymDao
    private lateinit var timerManager: FakeTimerManager
    private lateinit var viewModel: SessionViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        dao = FakeGymDao()
        timerManager = FakeTimerManager()
        viewModel = SessionViewModel(dao, timerManager)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `startSession creates new session if none active`() = runTest {
        dao.insertExercise(Exercise(name = "Test"))
        viewModel.startSession()
        testDispatcher.scheduler.advanceUntilIdle()
        
        val active = viewModel.activeSession.value
        assertNotNull(active)
        assertEquals("ACTIVE", active?.state)
        assertEquals(1, dao.sessions.size)
    }

    @Test
    fun `startSession resumes existing active session without duplicating`() = runTest {
        dao.insertExercise(Exercise(name = "Test"))
        viewModel.startSession()
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Simulate app reopen
        val newViewModel = SessionViewModel(dao, timerManager)
        newViewModel.startSession()
        testDispatcher.scheduler.advanceUntilIdle()
        
        assertEquals(1, dao.sessions.size) // No duplicates
    }

    @Test
    fun `completeSeries updates DB and starts TimerManager`() = runTest {
        val session = com.rafa.hercules.data.Session(id = "1", routineId = null, state = "ACTIVE")
        dao.insertSession(session)
        val series = Series(reps = 10, sessionId = "1", exerciseId = "1", weight = 50.0)
        dao.insertSeries(series)

        viewModel.completeSeries(series)
        testDispatcher.scheduler.advanceUntilIdle()

        val updated = dao.series.first()
        assertTrue(updated.isCompleted)
        assertNotNull(updated.completedAt)
        
        assertEquals(1, timerManager.alarmSetCount)
        assertEquals(90000L, timerManager.lastDuration)
    }

    @Test
    fun `finishSession sets state to FINISHED and cancels timers`() = runTest {
        dao.insertExercise(Exercise(name = "Test"))
        viewModel.startSession()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.finishSession()
        testDispatcher.scheduler.advanceUntilIdle()

        val session = dao.sessions.first()
        assertEquals("FINISHED", session.state)
        assertNotNull(session.endTime)
        assertEquals(1, timerManager.cancelCount) // Timer skipped/cancelled
    }

    @Test
    fun `completeSeries for dropset does not start timer until whole block is done`() = runTest {
        val session = com.rafa.hercules.data.Session(id = "1", routineId = null, state = "ACTIVE")
        dao.insertSession(session)
        val parent = Series(reps = 10, id = "p1", sessionId = "1", exerciseId = "1")
        val drop1 = Series(reps = 8, id = "d1", sessionId = "1", exerciseId = "1", parentId = "p1")
        dao.insertSeries(parent)
        dao.insertSeries(drop1)

        // Completar padre, aún falta el dropset
        viewModel.completeSeries(parent)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(0, timerManager.alarmSetCount)

        // Completar dropset, bloque completo
        viewModel.completeSeries(drop1)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, timerManager.alarmSetCount)
    }

    @Test
    fun `completeSeries for biserie does not start timer until both exercises are done`() = runTest {
        val session = com.rafa.hercules.data.Session(id = "1", routineId = null, state = "ACTIVE")
        dao.insertSession(session)
        val group = "group1"
        val exA = Series(reps = 10, id = "a1", sessionId = "1", exerciseId = "exA", groupId = group, orderIndex = 0)
        val exB = Series(reps = 10, id = "b1", sessionId = "1", exerciseId = "exB", groupId = group, orderIndex = 0)
        dao.insertSeries(exA)
        dao.insertSeries(exB)

        // Completar A, aún falta B
        viewModel.completeSeries(exA)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(0, timerManager.alarmSetCount)

        // Completar B, ronda completa
        viewModel.completeSeries(exB)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, timerManager.alarmSetCount)
    }

    // ---- PRUEBAS DE REGRESIÓN: bug descanso ignorado ----

    @Test
    fun `completeSeries usa targetRestMs de la rutina cuando es 120 segundos`() = runTest {
        val routine = com.rafa.hercules.data.Routine(id = "r1", name = "Rutina Test")
        val exercise = Exercise(id = "ex1", name = "Press Banca")
        val re = com.rafa.hercules.data.RoutineExercise(
            routineId = "r1", exerciseId = "ex1",
            targetRestMs = 120_000L // 120 segundos configurados
        )
        dao.insertRoutine(routine)
        dao.insertExercise(exercise)
        dao.insertRoutineExercise(re)

        val session = com.rafa.hercules.data.Session(id = "s1", routineId = "r1", state = "ACTIVE")
        dao.insertSession(session)
        val series = Series(reps = 10, sessionId = "s1", exerciseId = "ex1")
        dao.insertSeries(series)

        viewModel.completeSeries(series)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, timerManager.alarmSetCount)
        assertEquals(120_000L, timerManager.lastDuration) // debe ser 120 s, no 90 s
    }

    @Test
    fun `completeSeries usa targetRestMs de la rutina cuando es 60 segundos`() = runTest {
        val routine = com.rafa.hercules.data.Routine(id = "r2", name = "Rutina Corta")
        val exercise = Exercise(id = "ex2", name = "Curl Biceps")
        val re = com.rafa.hercules.data.RoutineExercise(
            routineId = "r2", exerciseId = "ex2",
            targetRestMs = 60_000L // 60 segundos configurados
        )
        dao.insertRoutine(routine)
        dao.insertExercise(exercise)
        dao.insertRoutineExercise(re)

        val session = com.rafa.hercules.data.Session(id = "s2", routineId = "r2", state = "ACTIVE")
        dao.insertSession(session)
        val series = Series(reps = 10, sessionId = "s2", exerciseId = "ex2")
        dao.insertSeries(series)

        viewModel.completeSeries(series)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, timerManager.alarmSetCount)
        assertEquals(60_000L, timerManager.lastDuration) // debe ser 60 s, no 90 s ni 120 s
    }

    @Test
    fun `completeSeries usa 90 segundos como fallback cuando la sesion no tiene rutina`() = runTest {
        // Sesión libre sin routineId (el valor fijo de 90 s es el fallback correcto)
        val session = com.rafa.hercules.data.Session(id = "s3", routineId = null, state = "ACTIVE")
        dao.insertSession(session)
        val series = Series(reps = 10, sessionId = "s3", exerciseId = "ex3")
        dao.insertSeries(series)

        viewModel.completeSeries(series)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, timerManager.alarmSetCount)
        assertEquals(90_000L, timerManager.lastDuration) // fallback correcto para sesión libre
    }

    @Test
    fun `cancelSession sets state to CANCELLED and cancels timers`() = runTest {
        dao.insertExercise(Exercise(name = "Test Cancel"))
        viewModel.startSession()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.cancelSession()
        testDispatcher.scheduler.advanceUntilIdle()

        val session = dao.sessions.first()
        assertEquals("CANCELLED", session.state)
        assertNotNull(session.endTime)
        assertEquals(1, timerManager.cancelCount) // Timer skipped/cancelled
        assertNull(viewModel.activeSession.value)
    }

    // ---- INSTANTÁNEA DE RUTINA ----

    @Test
    fun `startSession guarda la instantánea de composición de la rutina`() = runTest {
        dao.insertExercise(Exercise(id = "ex1", name = "Press"))
        dao.insertRoutine(com.rafa.hercules.data.Routine(id = "ri", name = "Push"))
        dao.insertRoutineExercise(
            com.rafa.hercules.data.RoutineExercise(
                routineId = "ri", exerciseId = "ex1",
                targetSets = 3, targetReps = 8, targetRestMs = 90_000L
            )
        )

        viewModel.startSession("ri")
        testDispatcher.scheduler.advanceUntilIdle()

        val session = dao.sessions.first()
        val expected = com.rafa.hercules.data.buildRoutineSnapshot("Push",
            dao.routineExercises.filter { it.routineId == "ri" })
        assertEquals(expected, session.routineSnapshot)
    }

    @Test
    fun `startSession sin rutina no inventa instantánea`() = runTest {
        dao.insertExercise(Exercise(name = "Test"))
        viewModel.startSession()
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(dao.sessions.first().routineSnapshot)
    }

    @Test
    fun `editar la plantilla despues de iniciar no altera la instantánea guardada`() = runTest {
        dao.insertExercise(Exercise(id = "ex1", name = "Press"))
        dao.insertRoutine(com.rafa.hercules.data.Routine(id = "ri", name = "Push"))
        dao.insertRoutineExercise(
            com.rafa.hercules.data.RoutineExercise(
                routineId = "ri", exerciseId = "ex1",
                targetSets = 3, targetReps = 8
            )
        )

        viewModel.startSession("ri")
        testDispatcher.scheduler.advanceUntilIdle()
        val saved = dao.sessions.first().routineSnapshot
        assertNotNull(saved)
        assertEquals("Push", viewModel.sessionName.value)

        // La plantilla cambia después de iniciar la sesión
        dao.insertRoutineExercise(
            com.rafa.hercules.data.RoutineExercise(
                routineId = "ri", exerciseId = "ex1",
                targetSets = 5, targetReps = 5
            )
        )

        assertEquals(saved, dao.sessions.first().routineSnapshot)
        assertNotEquals(saved, com.rafa.hercules.data.buildRoutineSnapshot("Push",
            dao.routineExercises.filter { it.routineId == "ri" }))
    }

    // ---- CARDIO ----

    @Test
    fun `startSession crea una sola fila de cardio sin peso ni reps`() = runTest {
        dao.insertExercise(Exercise(id = "cardio1", name = "Caminata", type = "CARDIO"))
        dao.insertRoutine(com.rafa.hercules.data.Routine(id = "rc", name = "Mixta"))
        dao.insertRoutineExercise(
            com.rafa.hercules.data.RoutineExercise(
                routineId = "rc",
                exerciseId = "cardio1",
                targetSets = 3,        // no debe generar 3 filas de cardio
                targetWarmups = 2,     // ni aproximaciones
                targetReps = 10,       // ni repeticiones
                targetDurationMs = 45 * 60000L
            )
        )

        viewModel.startSession("rc")
        testDispatcher.scheduler.advanceUntilIdle()

        val rows = dao.series.filter { it.exerciseId == "cardio1" }
        assertEquals(1, rows.size)
        assertNull(rows[0].weight)
        assertNull(rows[0].reps)
        assertNull(rows[0].durationMs) // duración realizada vacía, no cero
        assertFalse(rows[0].isWarmup)
    }

    @Test
    fun `startSession genera filas de fuerza y cardio en una rutina mixta`() = runTest {
        dao.insertExercise(Exercise(id = "fuerza1", name = "Press"))
        dao.insertExercise(Exercise(id = "cardio1", name = "Caminata", type = "CARDIO"))
        dao.insertRoutine(com.rafa.hercules.data.Routine(id = "rm", name = "Mixta"))
        dao.insertRoutineExercise(
            com.rafa.hercules.data.RoutineExercise(
                routineId = "rm", exerciseId = "fuerza1",
                targetWarmups = 1, targetSets = 2, targetReps = 8
            )
        )
        dao.insertRoutineExercise(
            com.rafa.hercules.data.RoutineExercise(
                routineId = "rm", exerciseId = "cardio1",
                targetDurationMs = 45 * 60000L
            )
        )

        viewModel.startSession("rm")
        testDispatcher.scheduler.advanceUntilIdle()

        val strengthRows = dao.series.filter { it.exerciseId == "fuerza1" }
        val cardioRows = dao.series.filter { it.exerciseId == "cardio1" }
        assertEquals(3, strengthRows.size) // 1 aprox + 2 series
        assertEquals(1, cardioRows.size)
        assertEquals(8, strengthRows.first { !it.isWarmup }.targetReps)
        assertNull(strengthRows.first { !it.isWarmup }.reps)
    }

    @Test
    fun `completeSeries de cardio marca completado sin iniciar descanso`() = runTest {
        dao.insertExercise(Exercise(id = "cardio1", name = "Caminata", type = "CARDIO"))
        val session = com.rafa.hercules.data.Session(id = "sc", routineId = null, state = "ACTIVE")
        dao.insertSession(session)
        val series = Series(id = "cs", sessionId = "sc", exerciseId = "cardio1", durationMs = 45 * 60000L)
        dao.insertSeries(series)

        viewModel.completeSeries(series)
        testDispatcher.scheduler.advanceUntilIdle()

        val updated = dao.series.first()
        assertTrue(updated.isCompleted)
        assertNotNull(updated.completedAt)
        assertEquals(45 * 60000L, updated.durationMs) // conserva la duración registrada
        assertEquals(0, timerManager.alarmSetCount) // el cardio no dispara beep de descanso
    }
    @Test fun `ediciones rapidas y completar conservan todos los campos`() = runTest {
        dao.insertSession(com.rafa.hercules.data.Session(id = "s", routineId = null))
        val original = Series(id = "row", sessionId = "s", exerciseId = "e")
        dao.insertSeries(original)
        viewModel.editSeries("row", { it.copy(weight = 50.0) })
        viewModel.editSeries("row", { it.copy(reps = 12) })
        viewModel.editSeries("row", { it.copy(effort = 8, effortScale = "RPE") })
        viewModel.completeSeries(original)
        testDispatcher.scheduler.advanceUntilIdle()
        val actual = dao.series.single()
        assertEquals(50.0, actual.weight!!, 0.0); assertEquals(12, actual.reps)
        assertEquals(8, actual.effort); assertTrue(actual.isCompleted)
        assertEquals(1, timerManager.alarmSetCount)
    }
    @Test fun `dos completados con una referencia antigua no reinician descanso`() = runTest {
        dao.insertSession(com.rafa.hercules.data.Session(id = "s", routineId = null))
        val row = Series(id = "row", sessionId = "s", exerciseId = "e", reps = 10)
        dao.insertSeries(row)
        viewModel.completeSeries(row); viewModel.completeSeries(row)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, timerManager.alarmSetCount)
    }
    @Test fun `biserie con bajada creada por la app espera al segundo ejercicio`() = runTest {
        dao.insertSession(com.rafa.hercules.data.Session(id = "s", routineId = null))
        val a = Series(id = "a", sessionId = "s", exerciseId = "a", occurrenceId = "a", groupId = "g", roundIndex = 0, reps = 10)
        val b = a.copy(id = "b", exerciseId = "b", occurrenceId = "b")
        dao.insertSeries(a); dao.insertSeries(b)
        viewModel.addDropset(a); testDispatcher.scheduler.advanceUntilIdle()
        val drop = dao.series.first { it.parentId == "a" }
        assertEquals("g", drop.groupId); assertEquals(0, drop.roundIndex)
        viewModel.editSeries(drop.id, { it.copy(reps = 8) })
        viewModel.completeSeries(a); viewModel.completeSeries(drop)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(0, timerManager.alarmSetCount)
        viewModel.completeSeries(b); testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, timerManager.alarmSetCount)
    }
    @Test fun `aproximaciones distintas no desplazan rondas generadas`() = runTest {
        dao.insertExercise(Exercise(id = "a", name = "A")); dao.insertExercise(Exercise(id = "b", name = "B"))
        dao.insertRoutine(com.rafa.hercules.data.Routine(id = "r", name = "AB"))
        dao.insertRoutineExercise(com.rafa.hercules.data.RoutineExercise(id = "ra", routineId = "r", exerciseId = "a", groupId = "g", targetWarmups = 1, targetSets = 2))
        dao.insertRoutineExercise(com.rafa.hercules.data.RoutineExercise(id = "rb", routineId = "r", exerciseId = "b", groupId = "g", targetWarmups = 0, targetSets = 2, orderIndex = 1))
        viewModel.startSession("r"); testDispatcher.scheduler.advanceUntilIdle()
        val a = dao.series.first { it.exerciseId == "a" && !it.isWarmup && it.roundIndex == 0 }
        val b = dao.series.first { it.exerciseId == "b" && it.roundIndex == 0 }
        viewModel.editSeries(a.id, { it.copy(reps = 10) }); viewModel.editSeries(b.id, { it.copy(reps = 10) })
        viewModel.completeSeries(a); viewModel.completeSeries(b); testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, timerManager.alarmSetCount)
    }
    @Test fun `editar plantilla no cambia descanso de una sesion existente`() = runTest {
        dao.insertExercise(Exercise(id = "e", name = "E")); dao.insertRoutine(com.rafa.hercules.data.Routine(id = "r", name = "R"))
        val config = com.rafa.hercules.data.RoutineExercise(id = "re", routineId = "r", exerciseId = "e", targetRestMs = 120000)
        dao.insertRoutineExercise(config); viewModel.startSession("r"); testDispatcher.scheduler.advanceUntilIdle()
        dao.insertRoutineExercise(config.copy(targetRestMs = 60000))
        val row = dao.series.first()
        viewModel.editSeries(row.id, { it.copy(reps = 10) }); viewModel.completeSeries(row); testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(120000L, timerManager.lastDuration)
    }
    @Test fun `sesion conserva orden y apariciones repetidas`() = runTest {
        dao.insertExercise(Exercise(id = "a", name = "A")); dao.insertExercise(Exercise(id = "b", name = "B"))
        dao.insertRoutine(com.rafa.hercules.data.Routine(id = "r", name = "R"))
        listOf("b", "a", "b").forEachIndexed { i, ex -> dao.insertRoutineExercise(com.rafa.hercules.data.RoutineExercise(id = "re$i", routineId = "r", exerciseId = ex, orderIndex = i, targetSets = 1)) }
        viewModel.startSession("r"); testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(listOf("b", "a", "b"), viewModel.exercisesState.value.map { it.exercise.id })
        assertEquals(3, viewModel.exercisesState.value.map { it.occurrenceId }.distinct().size)
    }
    @Test fun `inicio simultaneo crea una sola sesion`() = runTest {
        viewModel.startSession(); viewModel.startSession()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, dao.sessions.size)
    }
    @Test fun `ordenes previas a restauracion se descartan`() = runTest {
        val row = Series(id = "x", sessionId = "s", exerciseId = "e", weight = 20.0)
        dao.insertSeries(row)
        val generation = com.rafa.hercules.data.LocalMutations.generation
        com.rafa.hercules.data.LocalMutations.replace { }
        viewModel.editSeries(row.id, { it.copy(weight = 999.0) }, generation)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(20.0, dao.series.single().weight!!, 0.0)
        assertNotNull(viewModel.error.value)
    }
    @Test fun `campo invalido no modifica la base de datos`() = runTest {
        val row = Series(id = "x", sessionId = "s", exerciseId = "e", reps = 10)
        dao.insertSeries(row)
        viewModel.editSeries(row.id, { it.copy(reps = -1) }); testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(10, dao.series.single().reps); assertNotNull(viewModel.error.value)
    }
    @Test fun `no completar una serie sin repeticiones realizadas`() = runTest {
        dao.insertSession(com.rafa.hercules.data.Session(id = "s", routineId = null))
        val row = Series(id = "x", sessionId = "s", exerciseId = "e")
        dao.insertSeries(row); viewModel.completeSeries(row); testDispatcher.scheduler.advanceUntilIdle()
        assertFalse(dao.series.single().isCompleted); assertEquals(0, timerManager.alarmSetCount)
    }
    @Test fun `vencer la pantalla no cancela la alarma`() = runTest {
        viewModel.restRemainingMs("s", Long.MAX_VALUE)
        assertEquals(0, timerManager.cancelCount)
    }

    @Test fun `usar anterior persiste conversion sin completar ni descansar`() = runTest {
        dao.insertSession(com.rafa.hercules.data.Session(id = "s", routineId = null))
        val row = Series(id = "x", sessionId = "s", exerciseId = "e", effort = 2, effortScale = "RIR", notes = "Nota")
        dao.insertSeries(row)
        val old = row.copy(id = "old", weight = 100.0, unit = "lb", reps = 8, isCompleted = true, completedAt = 1)
        viewModel.editSeries(row.id, { com.rafa.hercules.data.copyPreviousValues(it, old) })
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(row.copy(weight = 45.359237, reps = 8), dao.series.single())
        assertEquals(0, timerManager.alarmSetCount)
        assertEquals(0, timerManager.cancelCount)
    }

    @Test fun `sesion legada sin snapshot muestra nombre de rutina al reanudar`() = runTest {
        dao.insertRoutine(com.rafa.hercules.data.Routine(id = "r", name = "Pierna"))
        dao.insertSession(com.rafa.hercules.data.Session(id = "s", routineId = "r"))
        viewModel.resumeSession("s"); testDispatcher.scheduler.advanceUntilIdle()
        assertEquals("Pierna", viewModel.sessionName.value)
    }

    @Test fun `biserie espera ultima bajada y doble toque no duplica descanso`() = runTest {
        dao.insertSession(com.rafa.hercules.data.Session(id = "s", routineId = null))
        val a = Series(id = "a", sessionId = "s", exerciseId = "a", occurrenceId = "a", groupId = "g", roundIndex = 0, reps = 10, targetRestMs = 60000)
        val b = a.copy(id = "b", exerciseId = "b", occurrenceId = "b", targetRestMs = 120000)
        dao.insertSeries(a); dao.insertSeries(b)
        viewModel.addDropset(a); testDispatcher.scheduler.advanceUntilIdle()
        val drop = dao.series.first { it.parentId == a.id }
        viewModel.editSeries(drop.id, { it.copy(reps = 6) })
        viewModel.completeSeries(a); viewModel.completeSeries(b)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(0, timerManager.alarmSetCount)
        viewModel.completeSeries(drop); viewModel.completeSeries(drop); viewModel.completeSeries(b)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, timerManager.alarmSetCount)
        assertEquals(120000L, timerManager.lastDuration)
        timerManager.now += 120000L
        assertEquals(0L, viewModel.restRemainingMs("s", timerManager.now))
        assertEquals(0, timerManager.cancelCount)
        viewModel.skipRest()
        assertEquals(1, timerManager.cancelCount)
    }

    @Test fun `vencimiento salto regreso y nuevo descanso tienen estados distintos sin efectos sonoros de UI`() = runTest {
        dao.insertSession(com.rafa.hercules.data.Session(id = "s", routineId = null))
        val row = Series(id = "one", sessionId = "s", exerciseId = "e", reps = 8, targetRestMs = 60000)
        dao.insertSeries(row)
        viewModel.resumeSession("s"); viewModel.completeSeries(row); testDispatcher.scheduler.advanceUntilIdle()
        fun display(vm: SessionViewModel) = com.rafa.hercules.data.restDisplay(vm.activeRestEndTime.value != null, vm.restRemainingMs("s", timerManager.now))
        assertEquals(com.rafa.hercules.data.RestDisplay.COUNTING, display(viewModel))
        timerManager.now += 60001 // Vence fuera de pantalla; no se ejecuta ninguna acción UI.
        val reopened = SessionViewModel(dao, timerManager)
        reopened.resumeSession("s"); testDispatcher.scheduler.advanceUntilIdle()
        repeat(3) { assertEquals(com.rafa.hercules.data.RestDisplay.READY, display(reopened)) }
        assertEquals(1, timerManager.alarmSetCount); assertEquals(0, timerManager.cancelCount)
        val next = row.copy(id = "two")
        dao.insertSeries(next); reopened.completeSeries(next); testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(com.rafa.hercules.data.RestDisplay.COUNTING, display(reopened))
        reopened.skipRest()
        assertEquals(com.rafa.hercules.data.RestDisplay.IDLE, display(reopened))
        timerManager.now += 60001
        reopened.resumeSession("s"); testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(com.rafa.hercules.data.RestDisplay.IDLE, display(reopened))
        val third = row.copy(id = "three")
        dao.insertSeries(third); reopened.completeSeries(third); testDispatcher.scheduler.advanceUntilIdle()
        timerManager.now += 60001
        assertEquals(com.rafa.hercules.data.RestDisplay.READY, display(reopened))
        reopened.finishSession(); testDispatcher.scheduler.advanceUntilIdle()
        assertNull(reopened.activeRestEndTime.value)
        assertEquals(3, timerManager.alarmSetCount); assertEquals(2, timerManager.cancelCount)
    }

}
