package com.rafa.hercules.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Pruebas de exportación/restauración con Room real sobre una base de datos en memoria.
 * Requieren dispositivo o emulador (`:app:connectedDebugAndroidTest`); sin él quedan
 * pendientes de ejecución.
 */
@RunWith(AndroidJUnit4::class)
class BackupRestoreTest {

    private lateinit var db: AppDatabase
    private lateinit var manager: BackupManager

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        manager = BackupManager(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    /** Datos ficticios representativos: cardio, biseries, dropsets, aproximaciones y metas. */
    private fun seedRepresentativeData() = runBlocking {
        val dao = db.gymDao()
        dao.insertExercises(
            listOf(
                Exercise(id = "ex-fuerza", name = "Press de Banca", defaultUnit = "kg"),
                Exercise(id = "ex-cardio", name = "Cinta", defaultUnit = "kg", type = "CARDIO")
            )
        )
        dao.insertRoutines(listOf(Routine(id = "rt-1", name = "Full Body", assignedDay = 1)))
        dao.insertRoutineExercises(
            listOf(
                RoutineExercise(
                    id = "rx-1", routineId = "rt-1", exerciseId = "ex-fuerza",
                    orderIndex = 0, targetSets = 3, targetWarmups = 1, targetReps = 8,
                    targetRestMs = 90000L, groupId = "grp-biserie", targetDropsets = 2
                ),
                RoutineExercise(
                    id = "rx-2", routineId = "rt-1", exerciseId = "ex-cardio",
                    orderIndex = 1, targetSets = 1, targetWarmups = 0, targetReps = 0,
                    targetRestMs = 0L, targetDurationMs = 600000L
                )
            )
        )
        dao.insertSessions(
            listOf(
                Session(id = "sn-activa", routineId = null, startTime = 1000L, state = "ACTIVE"),
                Session(
                    id = "sn-fin", routineId = "rt-1", startTime = 2000L, endTime = 5000L,
                    state = "FINISHED", overallEffort = 8, routineSnapshot = "v1|Press:3x8@90000"
                )
            )
        )
        dao.insertSeries(
            listOf(
                Series(
                    id = "sr-work", sessionId = "sn-fin", exerciseId = "ex-fuerza",
                    isWarmup = false, weight = 40.0, unit = "kg", reps = 8, effort = 8,
                    effortScale = "RPE", isCompleted = true, completedAt = 3000L, orderIndex = 0
                ),
                Series(
                    id = "sr-drop", sessionId = "sn-fin", exerciseId = "ex-fuerza",
                    isWarmup = false, weight = 30.0, unit = "kg", reps = 10, effort = 7,
                    effortScale = "RIR", isCompleted = true, completedAt = 3500L, orderIndex = 1,
                    parentId = "sr-work", groupId = "grp-biserie"
                ),
                Series(
                    id = "sr-cardio", sessionId = "sn-activa", exerciseId = "ex-cardio",
                    isWarmup = false, weight = null, unit = "kg", reps = null, effort = null,
                    isCompleted = false, orderIndex = 0, durationMs = 300000L
                )
            )
        )
        dao.insertGoalChange(GoalChange(id = "default_goal", timestamp = 0L, targetDays = 3))
    }

    private fun snapshotAll(): Map<String, List<Any>> = runBlocking {
        val dao = db.gymDao()
        mapOf(
            "exercises" to dao.getAllExercisesSync(),
            "routines" to dao.getAllRoutinesSync(),
            "routineExercises" to dao.getAllRoutineExercisesSync(),
            "sessions" to dao.getAllSessionsSync(),
            "series" to dao.getAllSeriesSync(),
            "goals" to dao.getAllGoalChangesSync()
        )
    }

    @Test
    fun exportar_importar_roundTrip_preserva_todo() = runBlocking {
        seedRepresentativeData()
        val before = snapshotAll()

        val json = manager.exportData()
        manager.importData(json)

        assertEquals(before, snapshotAll())
    }

    @Test
    fun doble_importacion_no_duplica_nada() = runBlocking {
        seedRepresentativeData()
        val json = manager.exportData()
        val expected = snapshotAll()

        manager.importData(json)
        manager.importData(json)

        val after = snapshotAll()
        assertEquals(expected, after)
        val ids = after.getValue("series").map { (it as Series).id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun fallo_durante_la_restauracion_hace_rollback() = runBlocking {
        seedRepresentativeData()
        val before = snapshotAll()

        val other = BackupCodec.serialize(
            routines = listOf(Routine(id = "rt-otra", name = "Otra")),
            exercises = listOf(Exercise(id = "ex-otra", name = "Otra")),
            routineExercises = emptyList(),
            sessions = listOf(Session(id = "sn-otra", routineId = null, startTime = 1L, state = "FINISHED")),
            series = emptyList(),
            goals = emptyList()
        )

        manager.testFailureAfterClear = { throw IllegalStateException("fallo simulado") }
        try {
            manager.importData(other)
            fail("La importación debía fallar y propagar el error")
        } catch (e: IllegalStateException) {
            assertEquals("fallo simulado", e.message)
        } finally {
            manager.testFailureAfterClear = null
        }

        // Los datos previos deben seguir intactos: nada de "rt-otra", nada vacío.
        assertEquals(before, snapshotAll())
    }

    @Test
    fun version_no_soportada_no_toca_los_datos() = runBlocking {
        seedRepresentativeData()
        val before = snapshotAll()

        try {
            manager.importData("""{"version":99,"routines":[]}""")
            fail("La versión 99 debía rechazarse")
        } catch (e: BackupFormatException) {
            assertTrue(e.message.orEmpty().contains("no soportada"))
        }

        assertEquals(before, snapshotAll())
    }

    @Test
    fun json_truncado_no_toca_los_datos() = runBlocking {
        seedRepresentativeData()
        val before = snapshotAll()
        val full = manager.exportData()

        try {
            manager.importData(full.substring(0, full.length / 2))
            fail("El JSON truncado debía rechazarse")
        } catch (e: BackupFormatException) {
            assertNotNull(e.message)
        }

        assertEquals(before, snapshotAll())
    }

    @Test
    fun respaldo_sin_goals_repone_la_meta_por_defecto() = runBlocking {
        val json = BackupCodec.serialize(
            routines = emptyList(),
            exercises = emptyList(),
            routineExercises = emptyList(),
            sessions = emptyList(),
            series = emptyList(),
            goals = emptyList()
        )
        manager.importData(json)

        val goals = db.gymDao().getAllGoalChangesSync()
        assertEquals(1, goals.size)
        assertEquals("default_goal", goals[0].id)
        assertEquals(3, goals[0].targetDays)
    }

    @Test
    fun sesion_activa_se_conserva_tras_importar() = runBlocking {
        seedRepresentativeData()
        val json = manager.exportData()

        manager.importData(json)

        val active = db.gymDao().getActiveSession()
        assertNotNull(active)
        assertEquals("sn-activa", active?.id)
        assertEquals("ACTIVE", active?.state)
        // La restauración no programa avisos por sí misma; el cancelado de avisos
        // anteriores lo hace SessionViewModel.refreshAfterImport al confirmarse la importación.
    }
}
