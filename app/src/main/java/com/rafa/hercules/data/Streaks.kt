package com.rafa.hercules.data

import java.time.*

data class StreakInfo(val currentStreak: Int = 0, val bestStreak: Int = 0, val currentGoal: Int = 3)

/**
 * Resumen de constancia semanal para Inicio (v1.15): días únicos con entrenamiento
 * completado en la semana en curso (lunes–domingo), sin inventar actividad.
 */
data class WeeklyConsistency(
    val trainedDays: Set<DayOfWeek> = emptySet(),
    val trainedCount: Int = 0,
    val goalDays: Int = 3
)

/** Cuenta días distintos (no sesiones) de la semana actual; excluye vacías y no futuros. */
fun calculateWeeklyConsistency(
    sessions: List<SessionWithSeries>,
    goals: List<GoalChange>,
    today: LocalDate,
    zone: ZoneId = ZoneId.systemDefault()
): WeeklyConsistency {
    val monday = today.minusDays(today.dayOfWeek.value.toLong() - 1)
    val days = sessions.filter(::isTrainingSession)
        .map { Instant.ofEpochMilli(it.session.startTime).atZone(zone).toLocalDate() }
        .filter { !it.isBefore(monday) && !it.isAfter(today) }
        .toSet()
    val nowMillis = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
    val goal = goals.filter { it.timestamp <= nowMillis }.maxByOrNull { it.timestamp }?.targetDays ?: 3
    return WeeklyConsistency(days.map { it.dayOfWeek }.toSet(), days.size, goal)
}

fun isTrainingSession(session: SessionWithSeries): Boolean = session.session.state == "FINISHED" &&
    session.series.any { !it.isWarmup && it.parentId == null && it.isCompleted }

fun calculateStreak(sessions: List<SessionWithSeries>, goals: List<GoalChange>, today: LocalDate,
    zone: ZoneId = ZoneId.systemDefault(), nowMillis: Long = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1): StreakInfo {
    val orderedGoals = goals.filter { it.timestamp <= nowMillis }.sortedBy { it.timestamp }
    val goalNow = orderedGoals.lastOrNull()?.targetDays ?: 3
    val days = sessions.filter(::isTrainingSession).map { Instant.ofEpochMilli(it.session.startTime).atZone(zone).toLocalDate() }
        .filter { !it.isAfter(today) }.toSet()
    if (days.isEmpty()) return StreakInfo(currentGoal = goalNow)
    fun monday(day: LocalDate) = day.minusDays(day.dayOfWeek.value.toLong() - 1)
    val weeks = days.groupBy(::monday)
    val currentMonday = monday(today)
    var week = monday(days.minOrNull()!!)
    var current = 0
    var best = 0
    while (!week.isAfter(currentMonday)) {
        val endExclusive = week.plusDays(7).atStartOfDay(zone).toInstant().toEpochMilli()
        val goal = orderedGoals.lastOrNull { it.timestamp < endExclusive }?.targetDays ?: 3
        if ((weeks[week]?.size ?: 0) >= goal) { current++; best = maxOf(best, current) }
        else if (week != currentMonday) current = 0
        week = week.plusWeeks(1)
    }
    return StreakInfo(current, best, goalNow)
}
