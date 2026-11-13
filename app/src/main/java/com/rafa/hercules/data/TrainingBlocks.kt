package com.rafa.hercules.data

fun Series.occurrenceKey(): String = occurrenceId ?: exerciseId
fun Series.workRound(): Int = roundIndex ?: orderIndex

/** Aproximaciones descansan individualmente; rondas desiguales continúan solas. */
fun roundRoots(parent: Series, rows: List<Series>): List<Series> =
    if (parent.isWarmup || parent.groupId == null) listOf(parent)
    else rows.filter { it.parentId == null && !it.isWarmup && it.groupId == parent.groupId && it.workRound() == parent.workRound() }

fun completedRound(parent: Series, rows: List<Series>): List<Series>? {
    val roots = roundRoots(parent, rows)
    if (roots.isEmpty() || roots.any { it.blockRestStartedAt != null }) return null
    val ids = roots.map { it.id }.toSet()
    if (rows.any { (it.id in ids || it.parentId in ids) && !it.isCompleted }) return null
    return roots
}

fun previousFor(current: Series, currentRows: List<Series>, previous: List<Series>): Series? {
    val occurrences = currentRows.filter { it.parentId == null }.map { it.occurrenceKey() }.distinct()
    val previousOccurrences = previous.filter { it.parentId == null }.map { it.occurrenceKey() }.distinct()
    val occurrence = when {
        current.occurrenceKey() in previousOccurrences -> current.occurrenceKey()
        occurrences.size == 1 && previousOccurrences.size == 1 -> previousOccurrences.single()
        else -> return null // Una aparición ambigua no es una referencia comprobable.
    }
    val parent = if (current.parentId == null) current else currentRows.find { it.id == current.parentId } ?: return null
    val previousRoots = previous.filter { it.parentId == null && it.occurrenceKey() == occurrence && it.isWarmup == parent.isWarmup }.sortedBy { it.orderIndex }
    val oldParent = previousRoots.singleOrNull {
        if (parent.roundIndex != null && it.roundIndex != null) it.roundIndex == parent.roundIndex
        else it.orderIndex == parent.orderIndex
    } ?: return null
    if (current.parentId == null) return oldParent
    val oldChildren = previous.filter { it.parentId == oldParent.id }.sortedWith(compareBy({ it.stageIndex ?: it.orderIndex }, { it.id }))
    return oldChildren.singleOrNull {
        if (current.stageIndex != null && it.stageIndex != null) it.stageIndex == current.stageIndex
        else it.orderIndex == current.orderIndex
    }
}

fun validateSeriesInput(row: Series) {
    require(row.weight == null || (row.weight.isFinite() && row.weight >= 0)) { "El peso debe ser positivo o cero." }
    require(row.reps == null || row.reps >= 0) { "Las repeticiones no pueden ser negativas." }
    require(row.effort == null || row.effort >= 0) { "El esfuerzo no puede ser negativo." }
    require(row.durationMs == null || row.durationMs in 0L..86400000L) { "La duración debe estar entre 0 y 1440 minutos." }
    require(row.unit in setOf("kg", "lb")) { "Unidad no válida." }
    require(row.effortScale == null || row.effortScale in setOf("RPE", "RIR")) { "Escala no válida." }
}

/** Permite corregir por partes datos legados inválidos sin introducir otro valor inválido. */
fun validateSeriesChange(old: Series, next: Series) {
    validateSeriesInput(next.copy(
        weight = next.weight.takeIf { it != old.weight },
        reps = next.reps.takeIf { it != old.reps },
        effort = next.effort.takeIf { it != old.effort },
        durationMs = next.durationMs.takeIf { it != old.durationMs },
        unit = if (next.unit != old.unit) next.unit else "kg",
        effortScale = next.effortScale.takeIf { it != old.effortScale }))
    if ((next.effort != old.effort || next.effortScale != old.effortScale) && next.effortScale == "RPE") {
        require(next.effort == null || next.effort in 0..10) { "El RPE debe estar entre 0 y 10." }
    }
}
