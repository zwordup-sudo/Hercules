package com.rafa.hercules.data

enum class RestDisplay { IDLE, COUNTING, READY }

fun restDisplay(hasRest: Boolean, remainingMs: Long): RestDisplay = when {
    !hasRest -> RestDisplay.IDLE
    remainingMs > 0 -> RestDisplay.COUNTING
    else -> RestDisplay.READY
}

data class SessionProgress(val completed: Int, val total: Int, val cardioOnly: Boolean)

fun sessionProgress(rows: List<Series>, cardioIds: Set<String>): SessionProgress {
    val strength = rows.filter { it.exerciseId !in cardioIds && it.parentId == null && !it.isWarmup }
    val cardioOnly = rows.isNotEmpty() && rows.all { it.exerciseId in cardioIds }
    val counted = if (cardioOnly) rows.filter { it.parentId == null }.distinctBy { it.occurrenceKey() } else strength
    return SessionProgress(counted.count { it.isCompleted }, counted.size, cardioOnly)
}

/** Solo resultados de carga/reps; nunca copia estado, esfuerzo, notas u objetivos. */
fun copyPreviousValues(current: Series, previous: Series): Series = current.copy(
    weight = previous.weight?.let {
        when {
            previous.unit == current.unit -> it
            current.unit == "kg" -> it * LB_TO_KG
            else -> it / LB_TO_KG
        }
    },
    reps = previous.reps
)

/** Aproximaciones primero; A/B intercalados por ronda, hijos debajo del padre. */
fun sessionDisplayRoots(rows: List<Series>): List<Series> = rows.filter { it.parentId == null }
    .sortedWith(compareBy<Series>({ if (it.isWarmup) 0 else 1 },
        { if (it.isWarmup) it.exerciseOrder ?: 0 else it.workRound() },
        { it.exerciseOrder ?: 0 }, { it.orderIndex }))
