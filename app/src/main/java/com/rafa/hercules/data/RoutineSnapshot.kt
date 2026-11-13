package com.rafa.hercules.data

/**
 * Instantánea de la composición de una rutina al iniciar una sesión.
 *
 * Se guarda en la sesión para que editar la plantilla después no cambie el
 * historial ni la comparación entre sesiones. Cadena determinista: mismo
 * contenido de rutina => misma cadena, así se detectan sesiones equivalentes.
 *
 * Formato v1: ejercicios ordenados por orderIndex y id, unidos por "|",
 * cada uno "exerciseId/orderIndex/sets/warmups/reps/restMs/groupId/durationMs/dropsets" (máximo de reps opcional al final).
 */
const val ROUTINE_SNAPSHOT_VERSION = 1

fun buildRoutineSnapshot(routineName: String, exercises: List<RoutineExercise>): String {
    val entries = exercises
        .sortedWith(compareBy({ it.orderIndex }, { it.id }))
        .joinToString("|") { re ->
            listOf(
                re.exerciseId,
                re.orderIndex,
                re.targetSets,
                re.targetWarmups,
                re.targetReps,
                re.targetRestMs,
                re.groupId ?: "",
                re.targetDurationMs ?: "",
                re.targetDropsets ?: ""
            ).joinToString("/") + (re.targetRepsMax?.let { "/$it" } ?: "")
        }
    return "v$ROUTINE_SNAPSHOT_VERSION;$routineName;$entries"
}

/**
 * Identidad de composición de una instantánea para agrupar sesiones equivalentes.
 * Dos sesiones son comparables si comparten esta firma (misma rutina lógica).
 * Instantánea ausente => firma null y comparabilidad limitada.
 */
fun routineSnapshotSignature(snapshot: String?): String? =
    if (snapshot == null || !snapshot.startsWith("v$ROUTINE_SNAPSHOT_VERSION;")) null else snapshot
