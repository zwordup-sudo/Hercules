package com.rafa.hercules.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutineShareTest {

    private val bench = Exercise(id = "e1", name = "Press de Banca")
    private val row = Exercise(id = "e2", name = "Remo sentado")
    private val walk = Exercise(id = "e3", name = "Caminata", type = "CARDIO")

    @Test
    fun `incluye nombre orden y configuracion de fuerza`() {
        val text = buildRoutineShareText(
            routineName = "Full Body",
            routineExercises = listOf(
                RoutineExercise(routineId = "r1", exerciseId = "e2", orderIndex = 1, targetSets = 3, targetWarmups = 1, targetReps = 10, targetRestMs = 120000L),
                RoutineExercise(routineId = "r1", exerciseId = "e1", orderIndex = 0, targetSets = 4, targetWarmups = 2, targetReps = 8, targetRestMs = 90000L)
            ),
            exercises = listOf(bench, row)
        )

        val lines = text.lines()
        assertEquals("Rutina: Full Body", lines[0])
        assertTrue(lines[1].startsWith("1. Press de Banca"))
        assertTrue(lines[1].contains("aprox 2, series 4, reps 8, descanso 90 s"))
        assertTrue(lines[2].startsWith("2. Remo sentado"))
        assertTrue(lines[2].contains("aprox 1, series 3, reps 10, descanso 120 s"))
    }

    @Test
    fun `incluye la duracion objetivo del cardio sin peso ni repeticiones`() {
        val text = buildRoutineShareText(
            routineName = "Mixta",
            routineExercises = listOf(
                RoutineExercise(routineId = "r1", exerciseId = "e3", orderIndex = 0, targetDurationMs = 45 * 60000L)
            ),
            exercises = listOf(walk)
        )

        val lines = text.lines()
        assertEquals(2, lines.size)
        assertTrue(lines[1].startsWith("1. Caminata (cardio)"))
        assertTrue(lines[1].contains("duración objetivo: 45 min"))
        assertFalse(lines[1].contains("reps"))
        assertFalse(lines[1].contains("descanso"))
    }

    @Test
    fun `marca las biseries con el nombre del compañero`() {
        val text = buildRoutineShareText(
            routineName = "Pecho y brazo",
            routineExercises = listOf(
                RoutineExercise(routineId = "r1", exerciseId = "e1", orderIndex = 0, groupId = "g1", targetRestMs = 60000L),
                RoutineExercise(routineId = "r1", exerciseId = "e2", orderIndex = 1, groupId = "g1", targetRestMs = 60000L)
            ),
            exercises = listOf(bench, row)
        )

        val lines = text.lines()
        assertTrue(lines[1].contains("(biserie con Remo sentado)"))
        assertTrue(lines[2].contains("(biserie con Press de Banca)"))
    }

    @Test
    fun `incluye los dropsets configurados por serie`() {
        val text = buildRoutineShareText(
            routineName = "Espalda",
            routineExercises = listOf(
                RoutineExercise(routineId = "r1", exerciseId = "e2", orderIndex = 0, targetDropsets = 2)
            ),
            exercises = listOf(row)
        )

        assertTrue(text.contains("dropsets: 2 por serie"))
    }

    @Test
    fun `solo incluye la plantilla sin datos personales`() {
        val text = buildRoutineShareText(
            routineName = "Pierna",
            routineExercises = listOf(
                RoutineExercise(routineId = "r1", exerciseId = "e1", orderIndex = 0)
            ),
            exercises = listOf(bench)
        )

        // Cabecera + una línea por ejercicio; nada de sesiones, volúmenes ni fechas
        assertEquals(2, text.lines().size)
        assertFalse(text.contains("Sesión"))
        assertFalse(text.contains("Volumen"))
        assertFalse(text.contains("Esfuerzo"))
    }
}
