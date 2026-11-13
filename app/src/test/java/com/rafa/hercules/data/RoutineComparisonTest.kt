package com.rafa.hercules.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutineComparisonTest {

    private fun finishedSession(
        id: String,
        startTime: Long = 1_000L,
        endTime: Long? = 61_000L,
        effort: Int? = 7,
        routineId: String? = "r1",
        snapshot: String? = null,
        volume: Double = 0.0
    ): SessionWithSeries {
        val series = if (volume > 0) listOf(
            Series(id = "$id-w", sessionId = id, exerciseId = "ex1", weight = volume / 10, reps = 10, isCompleted = true)
        ) else emptyList()
        return SessionWithSeries(
            session = Session(
                id = id, routineId = routineId, startTime = startTime,
                endTime = endTime, state = "FINISHED", overallEffort = effort,
                routineSnapshot = snapshot
            ),
            series = series
        )
    }

    @Test
    fun `filas incluyen duracion esfuerzo general y volumen de fuerza`() {
        val sessions = listOf(finishedSession("s1", volume = 500.0))

        val comparison = buildRoutineComparison(sessions, "r1")

        assertEquals(1, comparison.rows.size)
        val row = comparison.rows[0]
        assertEquals(1L, row.durationMinutes) // 60 s = 1 min
        assertEquals(7, row.overallEffort) // esfuerzo general, no RPE por serie
        assertEquals(500.0, row.strengthVolumeKg!!, 0.0001)
    }

    @Test
    fun `sin fin de sesion la duracion es vacia no cero`() {
        val sessions = listOf(finishedSession("s1", endTime = null))

        val row = buildRoutineComparison(sessions, "r1").rows.single()

        assertNull(row.durationMinutes)
    }

    @Test
    fun `solo sesiones finalizadas de la rutina elegida`() {
        val sessions = listOf(
            finishedSession("s1", routineId = "r1"),
            finishedSession("s2", routineId = "r2"),
            SessionWithSeries(
                session = Session(id = "s3", routineId = "r1", startTime = 1L, state = "ACTIVE"),
                series = emptyList()
            )
        )

        val comparison = buildRoutineComparison(sessions, "r1")

        assertEquals(listOf("s1"), comparison.rows.map { it.sessionId })
    }

    @Test
    fun `instantaneas iguales son comparables sin limitacion`() {
        val snap = buildRoutineSnapshot("Full", listOf(
            RoutineExercise(id = "re1", routineId = "r1", exerciseId = "ex1", orderIndex = 0, targetSets = 3, targetReps = 8)
        ))
        val sessions = listOf(
            finishedSession("s1", snapshot = snap),
            finishedSession("s2", startTime = 2_000L, snapshot = snap)
        )

        val comparison = buildRoutineComparison(sessions, "r1")

        assertFalse(comparison.comparabilityLimited)
        assertEquals(1, comparison.compositionGroups)
        assertEquals(0, comparison.sessionsWithoutSnapshot)
    }

    @Test
    fun `instantaneas distintas marcian comparabilidad limitada`() {
        val snapA = buildRoutineSnapshot("Full", listOf(
            RoutineExercise(id = "re1", routineId = "r1", exerciseId = "ex1", targetSets = 3, targetReps = 8)
        ))
        val snapB = buildRoutineSnapshot("Full", listOf(
            RoutineExercise(id = "re1", routineId = "r1", exerciseId = "ex1", targetSets = 5, targetReps = 5)
        ))
        val sessions = listOf(
            finishedSession("s1", snapshot = snapA),
            finishedSession("s2", startTime = 2_000L, snapshot = snapB)
        )

        val comparison = buildRoutineComparison(sessions, "r1")

        assertTrue(comparison.comparabilityLimited)
        assertEquals(2, comparison.compositionGroups)
    }

    @Test
    fun `sesiones antiguas sin instantanea marcian comparabilidad limitada`() {
        val sessions = listOf(
            finishedSession("s1", snapshot = null),
            finishedSession("s2", startTime = 2_000L, snapshot = null)
        )

        val comparison = buildRoutineComparison(sessions, "r1")

        assertTrue(comparison.comparabilityLimited)
        assertEquals(2, comparison.sessionsWithoutSnapshot)
        assertEquals(0, comparison.compositionGroups)
    }

    @Test
    fun `corregir el historial recalcula las filas sin tocar otras sesiones`() {
        val s1 = finishedSession("s1", volume = 500.0)
        val s2 = finishedSession("s2", startTime = 2_000L, volume = 300.0)

        val before = buildRoutineComparison(listOf(s1, s2), "r1")
        assertEquals(500.0, before.rows.first { it.sessionId == "s1" }.strengthVolumeKg!!, 0.0001)

        // Corrección puntual de la sesión s1 (misma fila, otro peso)
        val corrected = s1.copy(series = s1.series.map { it.copy(weight = 60.0) })
        val after = buildRoutineComparison(listOf(corrected, s2), "r1")

        assertEquals(600.0, after.rows.first { it.sessionId == "s1" }.strengthVolumeKg!!, 0.0001)
        assertEquals(300.0, after.rows.first { it.sessionId == "s2" }.strengthVolumeKg!!, 0.0001)
    }

    // ---- Instantánea ----

    @Test
    fun `instantanea es determinista y captura los objetivos`() {
        val exercises = listOf(
            RoutineExercise(id = "re2", routineId = "r1", exerciseId = "ex2", orderIndex = 1, targetSets = 4, targetReps = 12),
            RoutineExercise(id = "re1", routineId = "r1", exerciseId = "ex1", orderIndex = 0, targetSets = 3, targetReps = 8, targetDurationMs = null, targetDropsets = 1)
        )

        val a = buildRoutineSnapshot("Push", exercises)
        val b = buildRoutineSnapshot("Push", exercises.reversed())

        assertEquals(a, b) // mismo contenido => misma firma, sin importar el orden de entrada
        assertTrue(a.contains("ex1"))
        assertTrue(a.contains("v1;Push;"))
        assertNotEquals(a, buildRoutineSnapshot("Pull", exercises))
    }

    @Test
    fun `firma nula para instantanea ausente o de version desconocida`() {
        assertNull(routineSnapshotSignature(null))
        assertNull(routineSnapshotSignature("v999;otra;ex1"))
        val snap = buildRoutineSnapshot("Full", emptyList())
        assertEquals(snap, routineSnapshotSignature(snap))
    }
}
