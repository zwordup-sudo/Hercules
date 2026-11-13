package com.rafa.hercules.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseMetricsTest {

    private fun s(
        id: String,
        exerciseId: String = "ex1",
        weight: Double? = null,
        unit: String = "kg",
        reps: Int? = null,
        isWarmup: Boolean = false,
        isCompleted: Boolean = true,
        parentId: String? = null,
        durationMs: Long? = null
    ) = Series(
        id = id, sessionId = "sess", exerciseId = exerciseId, weight = weight, unit = unit,
        reps = reps, isWarmup = isWarmup, isCompleted = isCompleted, parentId = parentId,
        durationMs = durationMs
    )

    private fun session(
        id: String = "s1",
        startTime: Long = 1_000L,
        state: String = "FINISHED",
        series: List<Series>
    ) = SessionWithSeries(
        session = Session(id = id, routineId = null, startTime = startTime, state = state),
        series = series.map { it.copy(sessionId = id) }
    )

    @Test
    fun `mayor carga usa la serie mas pesada y sus propias repeticiones`() {
        val sws = session(series = listOf(
            s("a", weight = 60.0, reps = 5),
            s("b", weight = 72.5, reps = 3),
            s("c", weight = 50.0, reps = 8)
        ))

        val metrics = buildExerciseMetrics(listOf(sws), "ex1", isCardio = false).single()

        assertEquals(72.5, metrics.maxLoadKg!!, 0.0001)
        assertEquals(3, metrics.maxLoadReps) // reps de la MISMA serie, no de otra
        assertEquals(16, metrics.totalReps)
        assertEquals(60.0 * 5 + 72.5 * 3 + 50.0 * 8, metrics.volumeKg!!, 0.0001)
        assertEquals(3, metrics.workSeriesCount)
    }

    @Test
    fun `aproximaciones excluidas de carga volumen y repeticiones`() {
        val sws = session(series = listOf(
            s("w", weight = 100.0, reps = 1, isWarmup = true),
            s("a", weight = 50.0, reps = 8)
        ))

        val metrics = buildExerciseMetrics(listOf(sws), "ex1", isCardio = false).single()

        assertEquals(50.0, metrics.maxLoadKg!!, 0.0001)
        assertEquals(400.0, metrics.volumeKg!!, 0.0001)
        assertEquals(8, metrics.totalReps)
        assertEquals(1, metrics.workSeriesCount)
    }

    @Test
    fun `dropset cuenta la etapa una sola vez en volumen y no es serie principal`() {
        val sws = session(series = listOf(
            s("p", weight = 60.0, reps = 6),
            s("d", weight = 50.0, reps = 8, parentId = "p")
        ))

        val metrics = buildExerciseMetrics(listOf(sws), "ex1", isCardio = false).single()

        assertEquals(60.0 * 6 + 50.0 * 8, metrics.volumeKg!!, 0.0001) // cada etapa una vez
        assertEquals(1, metrics.workSeriesCount) // la hija no es serie principal
        assertEquals(60.0, metrics.maxLoadKg!!, 0.0001)
    }

    @Test
    fun `libras se convierten con el factor exacto`() {
        val sws = session(series = listOf(
            s("a", weight = 100.0, unit = "lb", reps = 10)
        ))

        val metrics = buildExerciseMetrics(listOf(sws), "ex1", isCardio = false).single()

        assertEquals(100 * 0.45359237, metrics.maxLoadKg!!, 0.0000001)
        assertEquals(100 * 0.45359237 * 10, metrics.volumeKg!!, 0.0000001)
    }

    @Test
    fun `sin carga registrada no inventa cero`() {
        val sws = session(series = listOf(
            s("a", weight = null, reps = null)
        ))

        val metrics = buildExerciseMetrics(listOf(sws), "ex1", isCardio = false).single()

        assertNull(metrics.maxLoadKg)
        assertNull(metrics.maxLoadReps)
        assertNull(metrics.totalReps)
        assertNull(metrics.volumeKg)
    }

    @Test
    fun `serie sin completar y ejercicios distintos no se mezclan`() {
        val sws = session(series = listOf(
            s("a", weight = 50.0, reps = 8, isCompleted = false),
            s("b", exerciseId = "ex2", weight = 90.0, reps = 8)
        ))

        val metrics = buildExerciseMetrics(listOf(sws), "ex1", isCardio = false)

        assertTrue(metrics.isEmpty()) // sin registros completados de ex1: vacío, no cero
    }

    @Test
    fun `sesiones no finalizadas se excluyen`() {
        val sws = SessionWithSeries(
            session = Session(id = "s1", routineId = null, startTime = 1L, state = "ACTIVE"),
            series = listOf(s("a", weight = 50.0, reps = 8).copy(sessionId = "s1"))
        )

        assertTrue(buildExerciseMetrics(listOf(sws), "ex1", isCardio = false).isEmpty())
    }

    @Test
    fun `rango de fechas filtra por startTime de la sesion`() {
        val old = session(id = "old", startTime = 1_000L, series = listOf(
            s("a", weight = 50.0, reps = 8)
        ))
        val recent = session(id = "new", startTime = 100_000L, series = listOf(
            s("b", weight = 60.0, reps = 8)
        ))

        val result = buildExerciseMetrics(listOf(old, recent), "ex1", isCardio = false, fromMillis = 50_000L)

        assertEquals(1, result.size)
        assertEquals("new", result[0].sessionId)
        // sin filtro aparecen ambas, ordenadas por fecha
        assertEquals(listOf("old", "new"), buildExerciseMetrics(listOf(recent, old), "ex1", isCardio = false).map { it.sessionId })
    }

    @Test
    fun `cardio reporta minutos realizados y nunca volumen`() {
        val sws = session(series = listOf(
            s("c", exerciseId = "cardio1", durationMs = 30 * 60000L)
        ))

        val metrics = buildExerciseMetrics(listOf(sws), "cardio1", isCardio = true).single()

        assertEquals(30L, metrics.cardioMinutes)
        assertNull(metrics.volumeKg)
        assertNull(metrics.maxLoadKg)
    }

    @Test
    fun `cardio sin duracion registrada no aporta minutos inventados`() {
        val sws = session(series = listOf(
            s("c", exerciseId = "cardio1", durationMs = null)
        ))

        assertTrue(buildExerciseMetrics(listOf(sws), "cardio1", isCardio = true).isEmpty())
    }
}
