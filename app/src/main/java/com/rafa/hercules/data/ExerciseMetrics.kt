package com.rafa.hercules.data

/**
 * Métricas de un ejercicio/variante concreto en una sesión finalizada.
 *
 * Reglas:
 * - Solo sesiones FINISHED y solo series completadas del ejercicio seleccionado.
 * - Aproximaciones (isWarmup) excluidas de todas las métricas de fuerza.
 * - Cada etapa de dropset se cuenta una sola vez en el volumen; la distinción
 *   se conserva en workSeriesCount (solo series principales, sin hijas).
 * - El cardio nunca aporta volumen ni repeticiones; solo su duración realizada.
 * - Vacío no equivale a cero: sin datos de carga, maxLoadKg es null.
 */
data class ExerciseSessionMetrics(
    val sessionId: String,
    val startTime: Long,
    /** Mayor carga completada de la sesión normalizada a kg (null si no hay carga registrada). */
    val maxLoadKg: Double?,
    /** Repeticiones de la misma serie que aportó la mayor carga (null si no hay carga). */
    val maxLoadReps: Int?,
    /** Repeticiones totales de trabajo completadas en la sesión para este ejercicio. */
    val totalReps: Int?,
    /** Volumen de fuerza kg·rep de series de trabajo completadas (etapas de dropset incluidas una vez). */
    val volumeKg: Double?,
    /** Series principales completadas (sin aproximaciones ni etapas hijas de dropset). */
    val workSeriesCount: Int,
    /** Minutos realizados de cardio (null si el ejercicio no es cardio o no hay duración). */
    val cardioMinutes: Long?
)

/**
 * Construye el histórico por sesión de un ejercicio/variante.
 *
 * @param isCardio si el ejercicio seleccionado es de cardio.
 * @param fromMillis / toMillis filtro opcional de rango de fechas por startTime de la sesión.
 * No inventa sesiones: solo aparece quien tenga registros del ejercicio.
 */
fun buildExerciseMetrics(
    sessions: List<SessionWithSeries>,
    exerciseId: String,
    isCardio: Boolean,
    fromMillis: Long? = null,
    toMillis: Long? = null
): List<ExerciseSessionMetrics> {
    return sessions.asSequence()
        .filter { it.session.state == "FINISHED" }
        .filter { fromMillis == null || it.session.startTime >= fromMillis }
        .filter { toMillis == null || it.session.startTime <= toMillis }
        .mapNotNull { sws ->
            val rows = sws.series.filter { it.exerciseId == exerciseId && it.isCompleted }
            if (rows.isEmpty()) return@mapNotNull null // no inventar datos ausentes

            if (isCardio) {
                val withDuration = rows.filter { it.parentId == null && it.durationMs != null }
                if (withDuration.isEmpty()) return@mapNotNull null
                ExerciseSessionMetrics(
                    sessionId = sws.session.id,
                    startTime = sws.session.startTime,
                    maxLoadKg = null,
                    maxLoadReps = null,
                    totalReps = null,
                    volumeKg = null,
                    workSeriesCount = 0,
                    cardioMinutes = withDuration.sumOf { it.durationMs!! / 60000L }
                )
            } else {
                val work = rows.filter { !it.isWarmup }
                if (work.isEmpty()) return@mapNotNull null
                val withLoad = work.filter { it.weight != null }
                val heaviest = withLoad.maxByOrNull { seriesWeightKg(it.weight, it.unit) }
                ExerciseSessionMetrics(
                    sessionId = sws.session.id,
                    startTime = sws.session.startTime,
                    maxLoadKg = heaviest?.let { seriesWeightKg(it.weight, it.unit) },
                    maxLoadReps = heaviest?.reps,
                    totalReps = if (work.all { it.reps != null }) work.sumOf { it.reps!! } else null,
                    volumeKg = knownVolume(work),
                    workSeriesCount = work.count { it.parentId == null },
                    cardioMinutes = null
                )
            }
        }
        .sortedBy { it.startTime }
        .toList()
}
