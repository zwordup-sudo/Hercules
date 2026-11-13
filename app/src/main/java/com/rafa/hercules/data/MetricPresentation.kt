package com.rafa.hercules.data

/**
 * Presentación de métricas que no aplican (v1.16).
 *
 * El volumen de fuerza solo tiene sentido si la sesión registró alguna serie de fuerza.
 * Distingue tres situaciones que NO son lo mismo:
 * - No aplica: no hubo ninguna serie de fuerza completada (p. ej. sesión solo de cardio).
 * - Incompleto: hubo fuerza, pero falta peso o repeticiones para calcular el volumen.
 * - Valor: hay volumen calculable; 0.0 es un volumen registrado, no un dato ausente.
 */
enum class StrengthVolumeState { NOT_APPLICABLE, INCOMPLETE, VALUE }

fun strengthVolumeState(summary: SessionSummary): StrengthVolumeState = when {
    summary.strengthSeriesCount == 0 -> StrengthVolumeState.NOT_APPLICABLE
    summary.workVolumeKg == null -> StrengthVolumeState.INCOMPLETE
    else -> StrengthVolumeState.VALUE
}

/**
 * Estado del cardio registrado en una sesión:
 * - No aplica: no hubo cardio.
 * - Sin duración: hubo cardio pero no se registró duración (no se inventa 0).
 * - Valor: cardio con duración registrada (puede ser 0 minutos).
 */
enum class CardioDurationState { NOT_APPLICABLE, MISSING, VALUE }

fun cardioDurationState(summary: SessionSummary): CardioDurationState = when {
    summary.cardioSeriesCount == 0 -> CardioDurationState.NOT_APPLICABLE
    !summary.cardioDurationRegistered -> CardioDurationState.MISSING
    else -> CardioDurationState.VALUE
}