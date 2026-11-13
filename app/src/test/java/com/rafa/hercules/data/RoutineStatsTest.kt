package com.rafa.hercules.data

import org.junit.Assert.assertEquals
import org.junit.Test

class RoutineStatsTest {
    private val cardioId = "cardio-1"
    private val isCardio: (String) -> Boolean = { it == cardioId }

    @Test
    fun `cuenta ejercicios y series de trabajo`() {
        val rows = listOf(
            RoutineExercise(id = "a", routineId = "r", exerciseId = "e1", targetSets = 4, targetWarmups = 3),
            RoutineExercise(id = "b", routineId = "r", exerciseId = "e2", targetSets = 2),
            RoutineExercise(id = "c", routineId = "r", exerciseId = cardioId, targetSets = 3)
        )
        val stats = routineStats(rows, isCardio)
        assertEquals(3, stats.exerciseCount)
        // El cardio no aporta series de fuerza.
        assertEquals(6, stats.workSeriesCount)
    }

    @Test
    fun `las aproximaciones no se cuentan como series`() {
        val rows = listOf(
            RoutineExercise(id = "a", routineId = "r", exerciseId = "e1", targetSets = 3, targetWarmups = 5)
        )
        assertEquals(3, routineStats(rows, isCardio).workSeriesCount)
    }

    @Test
    fun `los dropsets configurados no añaden series extra`() {
        val rows = listOf(
            RoutineExercise(id = "a", routineId = "r", exerciseId = "e1", targetSets = 2, targetDropsets = 3)
        )
        assertEquals(2, routineStats(rows, isCardio).workSeriesCount)
    }

    @Test
    fun `sin filas da cero`() {
        val stats = routineStats(emptyList(), isCardio)
        assertEquals(0, stats.exerciseCount)
        assertEquals(0, stats.workSeriesCount)
    }
}
