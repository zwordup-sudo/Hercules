package com.rafa.hercules.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/**
 * Archivado de ejercicios y rutinas (v1.14): ocultar del catálogo y de Inicio
 * sin borrar series, sesiones ni el histórico; con los bloqueos de uso.
 */
class ArchivadoTest {

    /**
     * Escenario: 3 ejercicios (SEED/USER/LEGACY); la rutina activa «Full Body» usa el
     * USER y una rutina ya archivada usa el LEGACY con sesión finalizada y series.
     */
    private suspend fun scenario(): FakeGymDao {
        val dao = FakeGymDao()
        dao.insertExercise(Exercise(id = "seed", name = "Press de Banca", origin = "SEED"))
        dao.insertExercise(Exercise(id = "propio", name = "Curl Martillo", origin = "USER"))
        dao.insertExercise(Exercise(id = "legacy", name = "Remo con Barra", origin = "LEGACY"))
        dao.insertRoutine(Routine(id = "rt", name = "Full Body"))
        dao.insertRoutineExercise(RoutineExercise(id = "rx", routineId = "rt", exerciseId = "propio"))
        dao.insertRoutine(Routine(id = "rt-old", name = "Vieja", archivedAt = 1000L))
        dao.insertRoutineExercise(RoutineExercise(id = "rx-old", routineId = "rt-old", exerciseId = "legacy"))
        dao.insertSession(Session(id = "sn", routineId = "rt-old", startTime = 1000L, endTime = 2000L, state = "FINISHED"))
        dao.insertSeries(Series(id = "sr", sessionId = "sn", exerciseId = "legacy", isCompleted = true))
        return dao
    }

    private suspend fun assertBlocked(fragment: String, block: suspend () -> Unit) {
        try {
            block()
            fail("La operación debía bloquearse y no lo fue")
        } catch (e: IllegalArgumentException) {
            assertTrue("El mensaje «${e.message}» no contiene «$fragment»", e.message.orEmpty().contains(fragment))
        }
    }

    @Test
    fun ejercicio_archivado_sale_del_catalogo_sin_borrar_el_historial() = runBlocking {
        val dao = scenario()
        dao.archiveExercise("legacy", 5000L)
        assertTrue(dao.observeActiveExercises().first().none { it.id == "legacy" })
        // Sesión e historial siguen resolviendo el nombre (observeExercises sin filtro).
        assertTrue(dao.observeExercises().first().any { it.id == "legacy" })
        // El export del respaldo incluye los archivados con sus metadatos.
        assertEquals("LEGACY", dao.getAllExercisesSync().first { it.id == "legacy" }.origin)
        assertEquals(5000L, dao.getAllExercisesSync().first { it.id == "legacy" }.archivedAt)
        assertEquals(1, dao.getAllSeriesSync().size)
        assertEquals("FINISHED", dao.getSessionById("sn")!!.state)
        // La rutina archivada de la que dependía sigue disponible para el histórico.
        assertTrue(dao.getAllRoutines().first().any { it.id == "rt-old" })
    }

    @Test
    fun ejercicio_inicial_no_se_puede_eliminar() = runBlocking {
        val dao = scenario()
        assertBlocked("inicial") { dao.archiveExercise("seed", 5000L) }
        assertTrue(dao.observeActiveExercises().first().any { it.id == "seed" })
        assertNull(dao.getExerciseById("seed")!!.archivedAt)
    }

    @Test
    fun ejercicio_en_uso_indica_las_rutinas_y_tras_archivarlas_se_puede() = runBlocking {
        val dao = scenario()
        assertBlocked("Full Body") { dao.archiveExercise("propio", 5000L) }
        assertTrue(dao.observeActiveExercises().first().any { it.id == "propio" })
        // La rutina queda archivada (sin sesión activa) y el ejercicio pasa a ser eliminable.
        dao.archiveRoutine("rt", 6000L)
        dao.archiveExercise("propio", 7000L)
        assertTrue(dao.observeActiveExercises().first().none { it.id == "propio" })
        assertEquals(7000L, dao.getExerciseById("propio")!!.archivedAt)
    }

    @Test
    fun rutina_con_sesion_activa_no_se_puede_eliminar_hasta_cerrarla() = runBlocking {
        val dao = scenario()
        dao.insertSession(Session(id = "activa", routineId = "rt", startTime = 3000L, state = "ACTIVE"))
        assertBlocked("entrenamiento activo") { dao.archiveRoutine("rt", 5000L) }
        assertTrue(dao.observeActiveRoutines().first().any { it.id == "rt" })
        // Finalizar la sesión (mismo camino que closeSession) desbloquea el archivado.
        val activa = dao.getSessionById("activa")!!
        dao.updateSession(activa.copy(state = "FINISHED", endTime = 4000L))
        dao.archiveRoutine("rt", 6000L)
        assertTrue(dao.observeActiveRoutines().first().none { it.id == "rt" })
        // Conserva rutina y sesiones para el histórico.
        val rt = dao.getAllRoutines().first().first { it.id == "rt" }
        assertEquals(6000L, rt.archivedAt)
        assertEquals(2, dao.getAllSessionsSync().size)
    }

    @Test
    fun sesion_activa_con_ejercicio_archivado_conserva_series_y_nombre() = runBlocking {
        val dao = scenario()
        dao.insertExercise(Exercise(id = "libre", name = "Búlgara", origin = "USER"))
        dao.insertSession(Session(id = "activa", routineId = null, startTime = 9000L, state = "ACTIVE"))
        dao.insertSeries(Series(id = "s2", sessionId = "activa", exerciseId = "libre", orderIndex = 0))
        // No está en ninguna rutina: se archiva aunque la sesión activa lo usa.
        dao.archiveExercise("libre", 10000L)
        assertEquals(1, dao.getSeriesForSession("activa").first().size)
        assertTrue(dao.observeExercises().first().any { it.id == "libre" })
        assertTrue(dao.observeActiveExercises().first().none { it.id == "libre" })
    }

    @Test
    fun doble_archivado_no_pisa_la_marca() = runBlocking {
        val dao = scenario()
        dao.archiveExercise("legacy", 5000L)
        assertBlocked("ya estaba") { dao.archiveExercise("legacy", 6000L) }
        assertEquals(5000L, dao.getExerciseById("legacy")!!.archivedAt)
    }

    @Test
    fun la_semilla_se_marca_inicial_y_no_se_repite() = runBlocking {
        val dao = FakeGymDao()
        dao.seedExercisesIfEmpty()
        dao.seedExercisesIfEmpty()
        assertEquals(3, dao.exercises.size)
        assertTrue(dao.exercises.all { it.origin == "SEED" })
        assertBlocked("inicial") { dao.archiveExercise(dao.exercises.first().id, 1L) }
    }
}
