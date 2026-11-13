package com.rafa.hercules.data

fun List<Series>.hasCompletedSeries(): Boolean = any { it.isCompleted || it.completedAt != null }

fun List<Series>.hasRecordedValues(): Boolean = any {
    it.weight != null || it.reps != null || it.effort != null || it.durationMs != null || !it.notes.isNullOrBlank()
}

fun substitutionCandidates(exercises: List<Exercise>, original: Exercise): List<Exercise> =
    exercises.filter { it.archivedAt == null && it.id != original.id && it.isCardio == original.isCardio }

/** Conserva identidad/estructura y plan; nunca arrastra resultados de otro ejercicio. */
fun substituteRows(rows: List<Series>, replacement: Exercise): List<Series> = rows.map {
    it.copy(exerciseId = replacement.id, occurrenceId = it.occurrenceKey(), unit = replacement.defaultUnit,
        weight = null, reps = null, effort = null, effortScale = null, durationMs = null, notes = null,
        isCompleted = false, completedAt = null)
}

/** Actualiza la composición efectiva para no comparar como idénticas rutinas sustituidas. */
fun substituteSnapshotExercise(snapshot: String?, row: Series, replacementId: String): String? {
    if (snapshot == null || !snapshot.startsWith("v1;")) return snapshot
    val entries = snapshot.substringAfterLast(';').split('|').map { it.split('/').toMutableList() }
    val matching = entries.filter { it.size in 9..10 && it[0] == row.exerciseId && it[1].toIntOrNull() == row.exerciseOrder }
    if (matching.size != 1) return snapshot
    matching.single()[0] = replacementId
    return snapshot.substringBeforeLast(';') + ";" + entries.joinToString("|") { it.joinToString("/") }
}
