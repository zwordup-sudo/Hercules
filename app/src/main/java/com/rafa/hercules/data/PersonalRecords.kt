package com.rafa.hercules.data

private data class RecordKey(val exerciseId: String, val reps: Int)

private fun Series.recordLoadKg(): Double? {
    if (!isCompleted || isWarmup || parentId != null || durationMs != null ||
        (reps ?: 0) <= 0 || unit !in setOf("kg", "lb")) return null
    val load = weight?.takeIf { it.isFinite() && it > 0 } ?: return null
    return seriesWeightKg(load, unit)
}

/**
 * Identifica las series de esta sesión que superaron una marca para las mismas reps.
 * El primer registro fija la referencia; solo una mejora posterior cuenta como récord.
 * La identidad del catálogo separa variantes, incluso si comparten nombre.
 */
fun personalRecordIds(
    history: List<Series>,
    current: List<Series>,
    strengthExerciseIds: Set<String>
): Set<String> {
    val best = mutableMapOf<RecordKey, Double>()
    val records = mutableSetOf<String>()

    fun consider(row: Series, inCurrentSession: Boolean) {
        if (row.exerciseId !in strengthExerciseIds) return
        val load = row.recordLoadKg() ?: return
        val key = RecordKey(row.exerciseId, requireNotNull(row.reps))
        val previous = best[key]
        // Tolerancia para que una conversión de ida y vuelta no convierta un empate en récord.
        if (previous == null || load - previous > maxOf(1e-9, previous * 1e-12)) {
            best[key] = load
            if (inCurrentSession && previous != null) records += row.id
        }
    }

    history.forEach { consider(it, false) }
    val dropsetRoots = current.mapNotNull { it.parentId }.toSet()
    current.asSequence()
        .filterNot { it.id in dropsetRoots }
        .sortedWith(compareBy({ it.completedAt ?: Long.MIN_VALUE }, { it.exerciseOrder }, { it.orderIndex }, { it.id }))
        .forEach { consider(it, true) }
    return records
}
