package com.rafa.hercules.data

/**
 * Construye el texto plano de una plantilla de rutina para el menú de compartir de Android.
 *
 * Solo incluye configuración de plantilla: nombre, orden, aproximaciones, series,
 * repeticiones, descansos, biseries, dropsets configurados y duración objetivo del cardio.
 * No incluye historial, sesiones, registros ni esfuerzos personales.
 *
 * Función pura: no depende de Android para poder probarse en unit tests.
 */
fun buildRoutineShareText(
    routineName: String,
    routineExercises: List<RoutineExercise>,
    exercises: List<Exercise>
): String {
    val byId = exercises.associateBy { it.id }
    val ordered = routineExercises.sortedBy { it.orderIndex }

    val groupMemberIds: Map<String, List<String>> = ordered
        .filter { it.groupId != null }
        .groupBy { it.groupId!! }
        .mapValues { (_, members) -> members.map { it.exerciseId } }

    val lines = mutableListOf<String>()
    lines += "Rutina: $routineName"

    ordered.forEachIndexed { index, re ->
        val exercise = byId[re.exerciseId]
        val name = exercise?.name ?: "Ejercicio desconocido"
        val number = index + 1

        if (exercise != null && exercise.isCardio) {
            lines += "$number. $name (cardio) - duración objetivo: ${re.durationMinutes} min"
            return@forEachIndexed
        }

        val biserieSuffix = re.groupId
            ?.let { gid -> groupMemberIds[gid] }
            ?.filter { it != re.exerciseId }
            ?.map { byId[it]?.name ?: "Ejercicio desconocido" }
            ?.takeIf { it.isNotEmpty() }
            ?.joinToString(" + ")
            ?.let { " (biserie con $it)" }
            ?: ""

        val dropsetsSuffix = if (re.dropsetStages > 0) ", dropsets: ${re.dropsetStages} por serie" else ""

        lines += "$number. $name$biserieSuffix - aprox ${re.targetWarmups}, series ${re.targetSets}, " +
            "reps ${repTargetLabel(re.targetReps, re.targetRepsMax)}, descanso ${re.targetRestMs / 1000} s$dropsetsSuffix"
    }

    return lines.joinToString("\n")
}
