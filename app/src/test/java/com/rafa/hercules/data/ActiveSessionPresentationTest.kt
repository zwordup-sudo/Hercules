package com.rafa.hercules.data

import org.junit.Assert.*
import org.junit.Test

class ActiveSessionPresentationTest {
    private val row = Series(id = "a", sessionId = "s", exerciseId = "a", occurrenceId = "a", exerciseOrder = 0, roundIndex = 0)

    @Test fun `avance excluye aproximaciones cardio y etapas`() {
        val rows = listOf(row.copy(isCompleted = true), row.copy(id = "b", roundIndex = 1),
            row.copy(id = "warm", isWarmup = true, isCompleted = true),
            row.copy(id = "drop", parentId = "a", isCompleted = true),
            row.copy(id = "cardio", exerciseId = "c", isCompleted = true))
        assertEquals(SessionProgress(1, 2, false), sessionProgress(rows, setOf("c")))
    }

    @Test fun `solo cardio cuenta ejercicios y vacio no inventa avance`() {
        assertEquals(SessionProgress(1, 2, true), sessionProgress(listOf(
            row.copy(exerciseId = "c", isCompleted = true),
            row.copy(id = "b", exerciseId = "c", occurrenceId = "b")), setOf("c")))
        assertEquals(SessionProgress(0, 0, false), sessionProgress(emptyList(), emptySet()))
    }

    @Test fun `copiar 100 lb a kg solo cambia peso y reps`() {
        val current = row.copy(effort = 3, effortScale = "RIR", notes = "Actual", targetReps = 12)
        val old = row.copy(weight = 100.0, unit = "lb", reps = 8, effort = 9, effortScale = "RPE", notes = "Vieja", isCompleted = true, completedAt = 100L)
        assertEquals(current.copy(weight = 45.359237, reps = 8), copyPreviousValues(current, old))
    }

    @Test fun `copiar kg a lb y misma unidad sin deriva`() {
        val old = row.copy(weight = 45.359237, reps = 10)
        val copied = copyPreviousValues(row.copy(unit = "lb"), old)
        assertEquals(100.0, copied.weight!!, 0.00000001)
        assertEquals(old.weight!!, copyPreviousValues(row, old).weight!!, 0.0)
        assertNull(copyPreviousValues(row.copy(weight = 10.0), old.copy(weight = null)).weight)
    }

    @Test fun `biserie ordena A B por ronda con aproximaciones separadas`() {
        val a1 = row.copy(id = "a1", groupId = "g")
        val a2 = a1.copy(id = "a2", roundIndex = 1)
        val b1 = a1.copy(id = "b1", exerciseId = "b", occurrenceId = "b", exerciseOrder = 1)
        val b2 = b1.copy(id = "b2", roundIndex = 1)
        val warm = a1.copy(id = "warm", isWarmup = true)
        val drop = a1.copy(id = "drop", parentId = "a1")
        assertEquals(listOf("warm", "a1", "b1", "a2", "b2"),
            sessionDisplayRoots(listOf(a1, a2, warm, drop, b1, b2)).map { it.id })
    }
}
