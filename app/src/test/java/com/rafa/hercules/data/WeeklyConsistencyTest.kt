package com.rafa.hercules.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneOffset

class WeeklyConsistencyTest {
    private val zone = ZoneOffset.UTC
    private fun ms(day: String) = LocalDate.parse(day).atStartOfDay(zone).toInstant().toEpochMilli()
    private fun workout(day: String, warmup: Boolean = false) = SessionWithSeries(
        Session(id = day, routineId = null, startTime = ms(day), state = "FINISHED"),
        listOf(Series(sessionId = day, exerciseId = "e", isWarmup = warmup, isCompleted = true))
    )

    @Test
    fun `sin entrenamientos da cero y la meta vigente`() {
        val result = calculateWeeklyConsistency(
            emptyList(),
            listOf(GoalChange(timestamp = 0, targetDays = 5)),
            LocalDate.parse("2026-10-07"), zone
        )
        assertEquals(0, result.trainedCount)
        assertEquals(5, result.goalDays)
        assertTrue(result.trainedDays.isEmpty())
    }

    @Test
    fun `cuenta dias unicos de la semana en curso`() {
        // Hoy miércoles 2026-10-07; lunes 05, martes 05+06 doble y miércoles 07.
        val sessions = listOf(
            workout("2026-10-05"),
            workout("2026-10-06"),
            workout("2026-10-06").copy(session = Session(id = "otra", routineId = null, startTime = ms("2026-10-06"), state = "FINISHED")),
            workout("2026-10-07")
        )
        val result = calculateWeeklyConsistency(sessions, emptyList(), LocalDate.parse("2026-10-07"), zone)
        assertEquals(3, result.trainedCount)
        assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY), result.trainedDays)
    }

    @Test
    fun `las sesiones de la semana pasada no cuentan`() {
        val sessions = listOf(workout("2026-09-29"), workout("2026-10-05"))
        val result = calculateWeeklyConsistency(sessions, emptyList(), LocalDate.parse("2026-10-07"), zone)
        assertEquals(1, result.trainedCount)
        assertEquals(setOf(DayOfWeek.MONDAY), result.trainedDays)
    }

    @Test
    fun `aproximaciones y sesiones vacias no cuentan`() {
        val warmup = workout("2026-10-05", warmup = true)
        val empty = warmup.copy(series = emptyList())
        val result = calculateWeeklyConsistency(listOf(warmup, empty), emptyList(), LocalDate.parse("2026-10-07"), zone)
        assertEquals(0, result.trainedCount)
        assertTrue(result.trainedDays.isEmpty())
    }

    @Test
    fun `los dias futuros no cuentan`() {
        val future = workout("2026-10-09") // viernes, hoy es miércoles
        val result = calculateWeeklyConsistency(listOf(future), emptyList(), LocalDate.parse("2026-10-07"), zone)
        assertEquals(0, result.trainedCount)
    }

    @Test
    fun `el domingo cuenta dentro de la misma semana`() {
        val sessions = listOf(workout("2026-10-11")) // domingo de esa semana
        val result = calculateWeeklyConsistency(sessions, emptyList(), LocalDate.parse("2026-10-11"), zone)
        assertEquals(1, result.trainedCount)
        assertEquals(setOf(DayOfWeek.SUNDAY), result.trainedDays)
    }
}
