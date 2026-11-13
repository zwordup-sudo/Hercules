package com.rafa.hercules.data

/**
 * Resumen de una sesión finalizada, con el cardio separado de la fuerza.
 *
 * - El volumen de fuerza solo suma series de trabajo completadas con carga normalizada a kg;
 *   el cardio nunca aporta volumen.
 * - Las series de cardio cuentan como ejercicio realizado y aportan sus minutos,
 *   pero no se cuentan como series de fuerza.
 */
data class SessionSummary(
    val exerciseCount: Int,
    val workSeriesCount: Int,
    val workVolumeKg: Double?,
    val warmupVolumeKg: Double?,
    val cardioMinutes: Long,
    /** Series de fuerza completadas (incluye aproximaciones y etapas); 0 = no aplica volumen de fuerza. */
    val strengthSeriesCount: Int = 0,
    /** Series de cardio completadas (etapas aparte); 0 = no hubo cardio. */
    val cardioSeriesCount: Int = 0,
    /** Si alguna serie de cardio guardó duración; false evita confundir ausencia con 0 minutos. */
    val cardioDurationRegistered: Boolean = false
)

private fun Series.weightKg(): Double = seriesWeightKg(weight, unit)

/**
 * @param isCardioExercise decide si un ejercicio es de cardio según su ID.
 * Función pura para poder probarla en unit tests.
 */
fun buildSessionSummary(series: List<Series>, isCardioExercise: (String) -> Boolean): SessionSummary {
    val completed = series.filter { it.isCompleted }
    val strength = completed.filter { !isCardioExercise(it.exerciseId) }
    val cardio = completed.filter { isCardioExercise(it.exerciseId) }

    val workSeriesCount = strength.count { !it.isWarmup && it.parentId == null }
    val workVolumeKg = knownVolume(strength.filter { !it.isWarmup })
    val warmupVolumeKg = knownVolume(strength.filter { it.isWarmup })
    val cardioMinutes = cardio.filter { it.parentId == null }.sumOf { (it.durationMs ?: 0L) / 60000L }
    val exerciseCount = completed.map { it.exerciseId }.distinct().count()

    return SessionSummary(
        exerciseCount = exerciseCount,
        workSeriesCount = workSeriesCount,
        workVolumeKg = workVolumeKg,
        warmupVolumeKg = warmupVolumeKg,
        cardioMinutes = cardioMinutes,
        strengthSeriesCount = strength.size,
        cardioSeriesCount = cardio.count { it.parentId == null },
        cardioDurationRegistered = cardio.any { it.durationMs != null }
    )
}

/** Cero es un volumen registrado; null significa que falta peso o repeticiones. */
fun knownVolume(rows: List<Series>): Double? =
    if (rows.any { it.weight == null || it.reps == null }) null
    else rows.sumOf { seriesWeightKg(it.weight, it.unit) * it.reps!! }
