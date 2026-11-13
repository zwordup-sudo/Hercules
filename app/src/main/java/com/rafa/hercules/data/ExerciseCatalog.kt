package com.rafa.hercules.data

import java.text.Normalizer

/** Filtros del catálogo (v1.16). Usa los datos existentes: tipo del ejercicio, no grupos musculares. */
enum class ExerciseFilter(val label: String) {
    ALL("Todos"),
    STRENGTH("Fuerza"),
    CARDIO("Cardio")
}

/**
 * Filtra el catálogo por texto y tipo. Función pura para poder probarla:
 * - El texto busca en el nombre, sin distinguir mayúsculas ni acentos.
 * - El tipo usa `Exercise.isCardio` (Fuerza = todo lo que no es cardio).
 * - El orden es alfabético y estable para no reordenar entre recomposiciones.
 */
fun filterExercises(
    exercises: List<Exercise>,
    query: String,
    filter: ExerciseFilter = ExerciseFilter.ALL
): List<Exercise> {
    val needle = query.trim().let(::foldAccents)
    return exercises
        .asSequence()
        .filter { ex ->
            when (filter) {
                ExerciseFilter.ALL -> true
                ExerciseFilter.STRENGTH -> !ex.isCardio
                ExerciseFilter.CARDIO -> ex.isCardio
            }
        }
        .filter { ex -> needle.isEmpty() || foldAccents(ex.name).contains(needle) }
        .sortedBy { it.name.lowercase() }
        .toList()
}

/** Normaliza a minúsculas sin acentos para comparar nombres de forma tolerante. */
private fun foldAccents(value: String): String {
    val decomposed = Normalizer.normalize(value.lowercase(), Normalizer.Form.NFD)
    return decomposed.replace(Regex("\\p{Mn}+"), "")
}