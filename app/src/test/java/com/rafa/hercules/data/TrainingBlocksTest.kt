package com.rafa.hercules.data

import org.junit.Assert.*
import org.junit.Test

class TrainingBlocksTest {
    private fun row(id: String, ex: String = id, round: Int = 0, warmup: Boolean = false) = Series(id = id, sessionId = "s", exerciseId = ex, occurrenceId = ex, groupId = "g", roundIndex = round, isWarmup = warmup, reps = 10)
    @Test fun una_bajada_no_cierra_la_biserie() {
        val a = row("a").copy(isCompleted = true)
        val b = row("b")
        val drop = row("d", "a").copy(parentId = "a", isCompleted = true, stageIndex = 1)
        assertNull(completedRound(a, listOf(a, b, drop)))
        assertEquals(setOf("a", "b"), completedRound(a, listOf(a, b.copy(isCompleted = true), drop))!!.map { it.id }.toSet())
    }
    @Test fun aproximacion_no_consume_ronda_de_trabajo() {
        val warm = row("warm", "a", warmup = true).copy(isCompleted = true)
        val a = row("a").copy(orderIndex = 1, isCompleted = true)
        val b = row("b").copy(orderIndex = 0, isCompleted = true)
        assertEquals(listOf(warm), completedRound(warm, listOf(warm, a, b)))
        assertEquals(2, completedRound(a, listOf(warm, a, b))!!.size)
    }
    @Test fun ronda_desigual_continua_individualmente() {
        val a = row("a", round = 1).copy(isCompleted = true)
        val b = row("b", round = 0).copy(isCompleted = true)
        assertEquals(listOf(a), completedRound(a, listOf(a, b)))
    }
    @Test fun ronda_ya_descansada_no_se_reinicia() {
        val a = row("a").copy(isCompleted = true, blockRestStartedAt = 123L)
        assertNull(completedRound(a, listOf(a)))
    }
    @Test fun anterior_separa_bajadas_y_principales() {
        val now = (0..2).map { row("n$it", "e", it).copy(orderIndex = it, groupId = null) }
        val old = (0..2).map { row("p$it", "e", it).copy(orderIndex = it, isCompleted = true) } + row("drop", "e").copy(parentId = "p0", orderIndex = 1, isCompleted = true)
        assertEquals("p2", previousFor(now[2], now, old)?.id)
    }
    @Test fun anterior_no_desplaza_trabajo_por_aproximaciones() {
        val now = listOf(row("w", "e", warmup = true), row("n", "e").copy(orderIndex = 1))
        val old = listOf(row("p", "e").copy(isCompleted = true))
        assertEquals("p", previousFor(now[1], now, old)?.id)
        assertNull(previousFor(now[0], now, old))
    }
    @Test fun reordenar_desagrupa_bloques_separados() {
        val a = RoutineExercise(id = "a", routineId = "r", exerciseId = "a", groupId = "g")
        val b = a.copy(id = "b", exerciseId = "b")
        val c = a.copy(id = "c", exerciseId = "c", groupId = null)
        assertTrue(normalizeRoutineGroups(listOf(a, c, b)).all { it.groupId == null })
    }
    @Test fun borrar_miembro_desagrupa_el_restante() {
        val a = RoutineExercise(routineId = "r", exerciseId = "a", groupId = "g")
        assertNull(normalizeRoutineGroups(listOf(a)).single().groupId)
    }
    @Test fun volumen_desconocido_no_es_cero() {
        assertNull(knownVolume(listOf(row("a").copy(weight = null))))
        assertEquals(0.0, knownVolume(listOf(row("a").copy(weight = 0.0)))!!, 0.0)
    }
    @Test fun anterior_incompleto_no_desplaza_indices() {
        val now = row("n", "e", round = 1).copy(orderIndex = 1)
        val old = row("p", "e", round = 2).copy(orderIndex = 2, isCompleted = true)
        assertNull(previousFor(now, listOf(now), listOf(old)))
    }
    @Test fun anterior_bajada_incompleta_no_desplaza_etapas() {
        val root = row("n", "e")
        val child = root.copy(id = "nc", parentId = "n", stageIndex = 1)
        val oldRoot = root.copy(id = "p", isCompleted = true)
        val oldChild = oldRoot.copy(id = "pc", parentId = "p", stageIndex = 2)
        assertNull(previousFor(child, listOf(root, child), listOf(oldRoot, oldChild)))
    }
}
