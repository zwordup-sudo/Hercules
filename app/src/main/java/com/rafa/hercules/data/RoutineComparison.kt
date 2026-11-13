package com.rafa.hercules.data

/**
 * Fila de comparación de una sesión finalizada dentro de una rutina.
 *
 * - durationMinutes solo existe si la sesión tiene fin registrado; vacío no es cero.
 * - overallEffort es el esfuerzo general de la sesión (escala 0-10), separado
 *   del esfuerzo por serie (RPE/RIR), que no se mezcla aquí.
 * - strengthVolumeKg es el volumen de fuerza de trabajo; el cardio no aporta.
 */
data class RoutineComparisonRow(
    val sessionId: String,
    val startTime: Long,
    val durationMinutes: Long?,
    val overallEffort: Int?,
    val strengthVolumeKg: Double?
)

/**
 * Resultado de comparar sesiones equivalentes de una rutina.
 *
 * Las sesiones se agrupan por la firma de su instantánea de composición.
 * Sin instantánea no se supone equivalencia: se marca comparabilidad limitada.
 * Los cálculos se derivan de las sesiones actuales, de modo que corregir el
 * historial recalcula las filas sin tocar otras sesiones.
 */
data class RoutineComparison(
    val routineId: String?,
    val rows: List<RoutineComparisonRow>,
    /** Número de grupos de composición distintos (1 = todas equivalentes). */
    val compositionGroups: Int,
    /** Sesiones sin instantánea registrada (comparabilidad limitada). */
    val sessionsWithoutSnapshot: Int
) {
    val comparabilityLimited: Boolean
        get() = sessionsWithoutSnapshot > 0 || compositionGroups > 1
}

/**
 * Compara las sesiones finales de una rutina concreta.
 * @param routineId null solo agrupa sesiones libres; normalmente se pasa la rutina elegida.
 */
fun buildRoutineComparison(
    sessions: List<SessionWithSeries>,
    routineId: String?,
    cardioExerciseIds: Set<String> = emptySet()
): RoutineComparison {
    val matched = sessions.filter { it.session.state == "FINISHED" && it.session.routineId == routineId }

    val rows = matched.mapNotNull { sws ->
        val session = sws.session
        val durationMs = session.endTime?.let { it - session.startTime }
        val strength = knownVolume(sws.series.filter { it.isCompleted && !it.isWarmup && it.exerciseId !in cardioExerciseIds })
        RoutineComparisonRow(
            sessionId = session.id,
            startTime = session.startTime,
            durationMinutes = durationMs?.let { it.coerceAtLeast(0L) / 60000L },
            overallEffort = session.overallEffort,
            strengthVolumeKg = strength
        )
    }.sortedByDescending { it.startTime }

    val signatures = matched.map { routineSnapshotSignature(it.session.routineSnapshot) }
    val withoutSnapshot = signatures.count { it == null }

    return RoutineComparison(
        routineId = routineId,
        rows = rows,
        compositionGroups = signatures.filterNotNull().toSet().size,
        sessionsWithoutSnapshot = withoutSnapshot
    )
}
