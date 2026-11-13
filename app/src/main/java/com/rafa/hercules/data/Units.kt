package com.rafa.hercules.data

/** Conversión exacta de libras a kilogramos: 1 lb = 0.45359237 kg. */
const val LB_TO_KG = 0.45359237

/**
 * Peso de una serie normalizado a kg según su unidad original.
 * Peso vacío no equivale a cero: aquí solo se convierte, la decisión de
 * incluir o excluir la serie es de quien calcula la métrica.
 */
fun seriesWeightKg(weight: Double?, unit: String): Double =
    if (unit == "lb") (weight ?: 0.0) * LB_TO_KG else (weight ?: 0.0)
