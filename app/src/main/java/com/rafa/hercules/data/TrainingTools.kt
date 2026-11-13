package com.rafa.hercules.data

import kotlin.math.abs
import kotlin.math.roundToLong

data class PlateLoad(val actualWeight: Double, val perSide: Map<Double, Int>)

/** Encuentra la carga simétrica más alta que no rebasa el objetivo. Inventario sin límite. */
fun calculatePlates(target: Double, bar: Double, unit: String): PlateLoad? {
    if (!target.isFinite() || !bar.isFinite() || target !in 0.0..2000.0 || bar !in 0.0..target) return null
    val plates = when (unit) {
        "kg" -> listOf(25.0, 20.0, 15.0, 10.0, 5.0, 2.5, 1.25)
        "lb" -> listOf(45.0, 35.0, 25.0, 10.0, 5.0, 2.5, 1.25)
        else -> return null
    }
    val cap = (((target - bar) / 2) * 4 + 1e-9).toInt()
    val steps = plates.map { (it * 4).roundToLong().toInt() }
    val counts = IntArray(cap + 1) { Int.MAX_VALUE }
    val last = IntArray(cap + 1) { -1 }
    counts[0] = 0
    for (i in 1..cap) for ((index, step) in steps.withIndex()) {
        if (i >= step && counts[i - step] != Int.MAX_VALUE && counts[i - step] + 1 < counts[i]) {
            counts[i] = counts[i - step] + 1
            last[i] = index
        }
    }
    val best = counts.indexOfLast { it != Int.MAX_VALUE }
    val used = IntArray(steps.size)
    var remaining = best
    while (remaining > 0) {
        val i = last[remaining]
        used[i]++
        remaining -= steps[i]
    }
    return PlateLoad(bar + best / 2.0, plates.mapIndexedNotNull { i, weight ->
        if (used[i] > 0) weight to used[i] else null
    }.toMap())
}

fun validRepRange(min: Int?, max: Int?): Boolean = max == null || (min != null && min in 1..999 && max in (min + 1)..1000)
fun repTargetLabel(min: Int, max: Int?): String = if (max == null) "$min" else "$min–$max"

data class ProgressionSuggestion(val previous: Double, val suggested: Double, val unit: String)

/** Solo el último bloque comparable: todas las series al máximo y con una misma carga externa. */
fun suggestProgression(planned: List<Series>, prior: List<Series>): ProgressionSuggestion? {
    val work = planned.filter { it.parentId == null && !it.isWarmup }
    val done = prior.filter { it.parentId == null && !it.isWarmup }
    val first = work.firstOrNull() ?: return null
    val max = first.targetRepsMax ?: return null
    if (!validRepRange(first.targetReps, max) || work.size != done.size || first.unit !in listOf("kg", "lb") ||
        work.any { it.targetReps != first.targetReps || it.targetRepsMax != max || it.exerciseId != first.exerciseId } ||
        done.any { !it.isCompleted || (it.reps ?: 0) < max || it.exerciseId != first.exerciseId ||
            it.targetReps != first.targetReps || it.targetRepsMax != max || it.unit !in listOf("kg", "lb") } ||
        (planned + prior).any { it.parentId != null || (it.targetDropsets ?: 0) > 0 }) return null
    val base = done.first().weight?.takeIf { it.isFinite() && it > 0 } ?: return null
    val kg = seriesWeightKg(base, done.first().unit)
    if (done.any { val load = it.weight; load == null || !load.isFinite() || abs(seriesWeightKg(load, it.unit) - kg) > 1e-6 }) return null
    val previous = if (first.unit == "lb") kg / LB_TO_KG else kg
    return ProgressionSuggestion(previous, previous + if (first.unit == "lb") 5.0 else 2.5, first.unit)
}

data class WorkoutRecord(val exercise: String, val weight: Double, val unit: String, val reps: Int)
data class FinishedWorkout(val name: String, val durationMs: Long, val totals: SessionSummary, val records: List<WorkoutRecord>)
