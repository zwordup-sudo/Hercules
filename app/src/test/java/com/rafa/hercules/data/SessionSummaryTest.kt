package com.rafa.hercules.data

import org.junit.Assert.assertEquals
import org.junit.Test

class SessionSummaryTest {

    private val isCardio: (String) -> Boolean = { it == "cardio1" }

    @Test
    fun `el cardio aporta minutos pero no volumen ni series de fuerza`() {
        val series = listOf(
            Series(id = "s1", sessionId = "sess", exerciseId = "fuerza1", weight = 30.0, unit = "kg", reps = 10, isCompleted = true),
            Series(id = "s2", sessionId = "sess", exerciseId = "cardio1", durationMs = 45 * 60000L, isCompleted = true)
        )

        val summary = buildSessionSummary(series, isCardio)

        assertEquals(2, summary.exerciseCount)
        assertEquals(1, summary.workSeriesCount)
        assertEquals(300.0, summary.workVolumeKg!!, 0.001)
        assertEquals(45L, summary.cardioMinutes)
    }

    @Test
    fun `una sesion solo de cardio no aporta volumen de fuerza`() {
        val series = listOf(
            Series(id = "s1", sessionId = "sess", exerciseId = "cardio1", durationMs = 60 * 60000L, isCompleted = true)
        )

        val summary = buildSessionSummary(series, isCardio)

        assertEquals(0.0, summary.workVolumeKg!!, 0.001)
        assertEquals(0, summary.workSeriesCount)
        assertEquals(60L, summary.cardioMinutes)
        assertEquals(1, summary.exerciseCount)
    }

    @Test
    fun `un cardio sin duracion registrada no suma minutos`() {
        val series = listOf(
            Series(id = "s1", sessionId = "sess", exerciseId = "cardio1", durationMs = null, isCompleted = true)
        )

        val summary = buildSessionSummary(series, isCardio)

        assertEquals(0L, summary.cardioMinutes)
        // Sigue contando como ejercicio realizado, no como 0 minutos inventados
        assertEquals(1, summary.exerciseCount)
    }

    @Test
    fun `separa aproximaciones y dropsets del conteo de series de trabajo`() {
        val series = listOf(
            Series(id = "s1", sessionId = "sess", exerciseId = "fuerza1", isWarmup = true, weight = 20.0, unit = "kg", reps = 5, isCompleted = true),
            Series(id = "s2", sessionId = "sess", exerciseId = "fuerza1", weight = 30.0, unit = "kg", reps = 10, isCompleted = true),
            Series(id = "s3", sessionId = "sess", exerciseId = "fuerza1", weight = 25.0, unit = "kg", reps = 8, isCompleted = true, parentId = "s2")
        )

        val summary = buildSessionSummary(series, isCardio)

        assertEquals(1, summary.workSeriesCount) // la bajada del dropset no es otra serie principal
        assertEquals(30 * 10 + 25 * 8, summary.workVolumeKg!!.toInt()) // 500 kg·rep, sin doble conteo
        assertEquals(20 * 5, summary.warmupVolumeKg!!.toInt()) // 100 kg·rep aparte
        assertEquals(0L, summary.cardioMinutes)
    }

    @Test
    fun `convierte libras a kilogramos en el volumen de fuerza`() {
        val series = listOf(
            Series(id = "s1", sessionId = "sess", exerciseId = "fuerza1", weight = 100.0, unit = "lb", reps = 10, isCompleted = true)
        )

        val summary = buildSessionSummary(series, isCardio)

        assertEquals(100 * 0.453592 * 10, summary.workVolumeKg!!, 0.001)
    }
}
