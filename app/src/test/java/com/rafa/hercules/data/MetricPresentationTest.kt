package com.rafa.hercules.data

import org.junit.Assert.assertEquals
import org.junit.Test

class MetricPresentationTest {

    private val isCardio: (String) -> Boolean = { it == "cardio1" }

    @Test
    fun `fuerza con carga completa da valor`() {
        val summary = buildSessionSummary(
            listOf(Series(id = "1", sessionId = "s", exerciseId = "f1", weight = 50.0, unit = "kg", reps = 10, isCompleted = true)),
            isCardio
        )
        assertEquals(StrengthVolumeState.VALUE, strengthVolumeState(summary))
    }

    @Test
    fun `sesion solo de cardio no aplica el volumen de fuerza`() {
        val summary = buildSessionSummary(
            listOf(Series(id = "1", sessionId = "s", exerciseId = "cardio1", durationMs = 600000L, isCompleted = true)),
            isCardio
        )
        assertEquals(StrengthVolumeState.NOT_APPLICABLE, strengthVolumeState(summary))
    }

    @Test
    fun `fuerza sin peso o reps marca incompleto`() {
        val summary = buildSessionSummary(
            listOf(Series(id = "1", sessionId = "s", exerciseId = "f1", weight = null, reps = null, isCompleted = true)),
            isCardio
        )
        assertEquals(StrengthVolumeState.INCOMPLETE, strengthVolumeState(summary))
    }

    @Test
    fun `cardio sin duracion no se confunde con cero`() {
        val summary = buildSessionSummary(
            listOf(Series(id = "1", sessionId = "s", exerciseId = "cardio1", isCompleted = true)),
            isCardio
        )
        assertEquals(CardioDurationState.MISSING, cardioDurationState(summary))
    }

    @Test
    fun `sin cardio no aplica`() {
        val summary = buildSessionSummary(
            listOf(Series(id = "1", sessionId = "s", exerciseId = "f1", weight = 40.0, unit = "kg", reps = 10, isCompleted = true)),
            isCardio
        )
        assertEquals(CardioDurationState.NOT_APPLICABLE, cardioDurationState(summary))
    }

    @Test
    fun `cardio con duracion registrada da valor aunque sean cero minutos`() {
        val summary = buildSessionSummary(
            listOf(Series(id = "1", sessionId = "s", exerciseId = "cardio1", durationMs = 0L, isCompleted = true)),
            isCardio
        )
        assertEquals(CardioDurationState.VALUE, cardioDurationState(summary))
    }
}