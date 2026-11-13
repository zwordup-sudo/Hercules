package com.rafa.hercules.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutineDraftTest {

    private var counter = 0

    private fun row(exerciseId: String, group: String? = null, sets: Int = 3): RoutineExercise {
        counter += 1
        return RoutineExercise(
            id = "row$counter",
            routineId = "r",
            exerciseId = exerciseId,
            orderIndex = 0,
            targetSets = sets,
            groupId = group
        )
    }

    @Test
    fun `sin cambios no pide confirmacion`() {
        val rows = listOf(row("a"), row("b"))
        assertFalse(routineDraftChanged("Push", rows, "Push", rows.map { it.copy() }))
    }

    @Test
    fun `cambiar nombre o series detecta cambios`() {
        val rows = listOf(row("a"))
        assertTrue(routineDraftChanged("Push", rows, "Pull", rows))
        assertTrue(routineDraftChanged("Push", rows, "Push", rows.map { it.copy(targetSets = 5) }))
    }

    @Test
    fun `un orderIndex distinto sin reordenar no es un cambio`() {
        val rows = listOf(row("a"), row("b"))
        val sameOrderDifferentIndex = rows.mapIndexed { index, r -> r.copy(orderIndex = 10 + index) }
        assertFalse(routineDraftChanged("Push", rows, "Push", sameOrderDifferentIndex))
    }

    @Test
    fun `reordenar filas cuenta como cambio`() {
        val rows = listOf(row("a"), row("b"))
        assertTrue(routineDraftChanged("Push", rows, "Push", rows.reversed()))
    }

    @Test
    fun `agrupa solo biseries consecutivas`() {
        val rows = listOf(row("a"), row("b", group = "g1"), row("c", group = "g1"), row("d"))
        assertEquals(listOf(listOf(0), listOf(1, 2), listOf(3)), routineEditorGroups(rows))
    }

    @Test
    fun `normaliza y descarta grupos no consecutivos`() {
        // Un grupo cuyas filas no son consecutivas (b en 0 y c en 2) deja de ser biserie.
        val rows = listOf(row("b", group = "g1"), row("a"), row("c", group = "g1"))
        val normalized = normalizeRoutineGroups(rows)
        assertTrue(normalized.all { it.groupId == null })
    }
}