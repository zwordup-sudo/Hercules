package com.rafa.hercules.data

/** Cantidades reales que muestra cada tarjeta de rutina en Inicio (v1.15). */
data class RoutineStats(
    /** Entradas configuradas (cada fila de la plantilla). */
    val exerciseCount: Int,
    /** Series de trabajo configuradas; no incluye aproximaciones ni etapas de dropset. */
    val workSeriesCount: Int
)

/**
 * Calcula los contadores de una rutina a partir de su configuración.
 * Las filas de cardio no aportan series de fuerza.
 */
fun routineStats(rows: List<RoutineExercise>, isCardio: (String) -> Boolean): RoutineStats =
    RoutineStats(
        exerciseCount = rows.size,
        workSeriesCount = rows.filter { !isCardio(it.exerciseId) }.sumOf { it.targetSets.coerceAtLeast(0) }
    )
