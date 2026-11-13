package com.rafa.hercules.data

import java.time.*
import org.junit.Assert.*
import org.junit.Test

class StreaksTest {
    private val zone = ZoneOffset.UTC
    private fun ms(day: String) = LocalDate.parse(day).atStartOfDay(zone).toInstant().toEpochMilli()
    private fun workout(day: String, warmup: Boolean = false) = SessionWithSeries(Session(id = day, routineId = null, startTime = ms(day), state = "FINISHED"), listOf(Series(sessionId = day, exerciseId = "e", isWarmup = warmup, isCompleted = true)))
    @Test fun lunes_no_cambia_la_meta_de_la_semana_anterior() {
        val sessions = listOf(workout("2026-09-28"), workout("2026-09-29"), workout("2026-09-30"))
        val goals = listOf(GoalChange(timestamp = ms("2026-10-05"), targetDays = 4))
        assertEquals(1, calculateStreak(sessions, goals, LocalDate.parse("2026-10-07"), zone).currentStreak)
    }
    @Test fun antes_de_la_primera_meta_se_usa_tres() {
        val sessions = listOf(workout("2026-09-21"), workout("2026-09-22"))
        val goals = listOf(GoalChange(timestamp = ms("2026-10-05"), targetDays = 2))
        assertEquals(0, calculateStreak(sessions, goals, LocalDate.parse("2026-10-07"), zone).bestStreak)
    }
    @Test fun semana_actual_no_rompe_la_racha_y_semana_cerrada_si() {
        val sessions = listOf(workout("2026-09-28"), workout("2026-09-29"), workout("2026-09-30"))
        assertEquals(1, calculateStreak(sessions, emptyList(), LocalDate.parse("2026-10-07"), zone).currentStreak)
        assertEquals(0, calculateStreak(sessions, emptyList(), LocalDate.parse("2026-10-12"), zone).currentStreak)
    }
    @Test fun aproximaciones_y_sesiones_vacias_no_cuentan() {
        val warmup = workout("2026-10-05", true)
        val empty = warmup.copy(series = emptyList())
        assertFalse(isTrainingSession(warmup)); assertFalse(isTrainingSession(empty))
    }
    @Test fun dos_sesiones_el_mismo_dia_cuentan_un_dia() {
        val a = workout("2026-10-05")
        assertEquals(0, calculateStreak(listOf(a, a.copy(session = a.session.copy(id = "second"))), listOf(GoalChange(timestamp = 0, targetDays = 2)), LocalDate.parse("2026-10-07"), zone).currentStreak)
    }
    @Test fun cruce_de_ano_mantiene_lunes_a_domingo() {
        val sessions = listOf(workout("2025-12-29"), workout("2025-12-30"), workout("2026-01-02"))
        assertEquals(1, calculateStreak(sessions, emptyList(), LocalDate.parse("2026-01-06"), zone).currentStreak)
    }
}
