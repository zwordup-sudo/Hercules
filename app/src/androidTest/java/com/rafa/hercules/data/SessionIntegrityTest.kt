package com.rafa.hercules.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SessionIntegrityTest {
    private lateinit var db: AppDatabase
    @Before fun setup() { db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java).build() }
    @After fun close() { db.close() }
    @Test fun inicio_fallido_no_deja_sesion_parcial() = runBlocking {
        val session = Session(id = "s", routineId = null)
        try { db.gymDao().createSessionIfNone(session, listOf(Series(sessionId = "s", exerciseId = "inexistente"))); fail("Debía fallar la FK") }
        catch (e: android.database.sqlite.SQLiteConstraintException) { }
        assertTrue(db.gymDao().getAllSessionsSync().isEmpty())
        assertTrue(db.gymDao().getAllSeriesSync().isEmpty())
    }
    @Test fun editar_plantilla_conserva_historial_y_su_configuracion() = runBlocking {
        val dao = db.gymDao()
        dao.insertExercise(Exercise(id = "e", name = "E"))
        val routine = Routine(id = "r", name = "R")
        dao.saveRoutineTemplate(routine, listOf(RoutineExercise(id = "re", routineId = "r", exerciseId = "e", targetRestMs = 120000)))
        val session = Session(id = "s", routineId = "r")
        dao.createSessionIfNone(session, listOf(Series(id = "row", sessionId = "s", exerciseId = "e", occurrenceId = "re", targetRestMs = 120000)))
        dao.saveRoutineTemplate(routine.copy(name = "Editada"), listOf(RoutineExercise(id = "re", routineId = "r", exerciseId = "e", targetRestMs = 60000)))
        assertEquals(120000L, dao.getSeriesById("row")?.targetRestMs)
        assertEquals(1, dao.getAllSessionsSync().size)
    }

    @Test fun sustitucion_es_atomica_si_falla_la_segunda_fila() = runBlocking {
        val dao = db.gymDao()
        dao.insertExercises(listOf(Exercise(id = "e", name = "E"), Exercise(id = "new", name = "Nuevo")))
        dao.insertSession(Session(id = "s", routineId = null))
        val first = Series(id = "a", sessionId = "s", exerciseId = "e", occurrenceId = "occ", reps = 8)
        val second = first.copy(id = "b", parentId = "a", stageIndex = 1)
        dao.insertSeries(listOf(first, second))
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_substitution BEFORE UPDATE ON series WHEN OLD.id = 'b' BEGIN SELECT RAISE(ABORT, 'fallo de prueba'); END")
        try { dao.substituteSessionExercise("s", "occ", "e", "new", true); fail("Debía fallar la segunda actualización") }
        catch (_: android.database.sqlite.SQLiteConstraintException) { }
        assertEquals(first, dao.getSeriesById("a")); assertEquals(second, dao.getSeriesById("b"))
    }

    @Test fun sustitucion_persiste_aparicion_no_toca_plantilla_y_revalida_bloqueo() = runBlocking {
        val dao = db.gymDao()
        dao.insertExercises(listOf(Exercise(id = "e", name = "E"), Exercise(id = "new", name = "Nuevo")))
        val routine = Routine(id = "r", name = "R")
        val config = listOf(RoutineExercise(id = "one", routineId = "r", exerciseId = "e"), RoutineExercise(id = "two", routineId = "r", exerciseId = "e", orderIndex = 1))
        dao.saveRoutineTemplate(routine, config)
        val session = Session(id = "s", routineId = "r", routineSnapshot = buildRoutineSnapshot("R", config))
        val first = Series(id = "a", sessionId = "s", exerciseId = "e", occurrenceId = "one", exerciseOrder = 0, roundIndex = 0, targetRestMs = 120000)
        val second = first.copy(id = "b", occurrenceId = "two", exerciseOrder = 1)
        dao.createSessionIfNone(session, listOf(first, second))
        dao.substituteSessionExercise("s", "one", "e", "new", true)
        assertEquals(first.copy(exerciseId = "new"), dao.getSeriesById("a"))
        assertEquals(second, dao.getSeriesById("b")); assertEquals(config, dao.getRoutineExercises("r"))
        dao.updateSeries(second.copy(isCompleted = true))
        try { dao.substituteSessionExercise("s", "two", "e", "new", true); fail("Debía bloquear") }
        catch (_: IllegalArgumentException) { }
        assertEquals("e", dao.getSeriesById("b")!!.exerciseId)
    }
}
