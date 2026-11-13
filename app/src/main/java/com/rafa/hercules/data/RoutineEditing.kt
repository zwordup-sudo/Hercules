package com.rafa.hercules.data

import org.json.JSONObject

fun normalizeRoutineGroups(rows: List<RoutineExercise>): List<RoutineExercise> {
    val valid = rows.withIndex().filter { it.value.groupId != null }.groupBy { it.value.groupId!! }
        .filterValues { it.size == 2 && it[1].index == it[0].index + 1 }.keys
    return rows.mapIndexed { index, row -> row.copy(orderIndex = index, groupId = row.groupId?.takeIf { it in valid }) }
}

/**
 * Firma canónica de una fila del editor: ignora ids y el orderIndex guardado,
 * para comparar solo lo que el usuario ve y edita.
 */
private fun routineRowSignature(row: RoutineExercise): List<Any?> = listOf(
    row.exerciseId,
    row.targetSets,
    row.targetWarmups,
    row.targetReps,
    row.targetRepsMax,
    row.targetRestMs,
    row.groupId,
    row.targetDurationMs,
    row.targetDropsets
)

/**
 * Detecta si el borrador difiere de la rutina inicial (nombre o filas, en orden).
 * Función pura usada para pedir confirmación al salir del editor.
 */
fun routineDraftChanged(
    initialName: String,
    initialRows: List<RoutineExercise>,
    name: String,
    rows: List<RoutineExercise>
): Boolean {
    if (name.trim() != initialName.trim()) return true
    val before = normalizeRoutineGroups(initialRows).map(::routineRowSignature)
    val after = normalizeRoutineGroups(rows).map(::routineRowSignature)
    return before != after
}

/**
 * Posiciones consecutivas que comparten biserie se agrupan; el resto son tarjetas sueltas.
 * Devuelve listas de índices en orden de aparición (una biserie siempre son dos filas).
 */
fun routineEditorGroups(rows: List<RoutineExercise>): List<List<Int>> {
    val groups = mutableListOf<List<Int>>()
    var index = 0
    while (index < rows.size) {
        val groupId = rows[index].groupId
        if (groupId != null && index + 1 < rows.size && rows[index + 1].groupId == groupId) {
            groups.add(listOf(index, index + 1))
            index += 2
        } else {
            groups.add(listOf(index))
            index += 1
        }
    }
    return groups
}

fun validateRoutine(rows: List<RoutineExercise>, isCardio: (String) -> Boolean = { false }) {
    require(rows.isNotEmpty()) { "Añade al menos un ejercicio." }
    require(rows.map { it.id }.distinct().size == rows.size) { "La rutina contiene identificadores repetidos." }
    rows.forEach {
        require(isCardio(it.exerciseId) || (it.targetSets in 1..100 && it.targetWarmups in 0..20 && it.targetReps in 0..1000)) { "Revisa series (1–100), aproximaciones (0–20) y repeticiones (0–1000)." }
        require(it.targetRestMs in 0L..86400000L) { "El descanso debe estar entre 0 y 86400 segundos." }
        require(validRepRange(it.targetReps, it.targetRepsMax)) { "Las reps máximas deben superar las mínimas (1–1000)." }
        require(it.targetDurationMs == null || it.targetDurationMs in 0L..86400000L) { "La duración debe estar entre 0 y 1440 minutos." }
        require(it.targetDropsets == null || it.targetDropsets in 0..20) { "Las bajadas deben estar entre 0 y 20." }
    }
}

// Strings JSON individuales son guardables en Bundle; no se serializan entidades Room.
fun encodeRoutineDraft(row: RoutineExercise): String = JSONObject().apply {
    put("id", row.id); put("exerciseId", row.exerciseId); put("orderIndex", row.orderIndex)
    put("targetSets", row.targetSets); put("targetWarmups", row.targetWarmups); put("targetReps", row.targetReps)
    put("targetRepsMax", row.targetRepsMax ?: JSONObject.NULL)
    put("targetRestMs", row.targetRestMs); put("groupId", row.groupId ?: JSONObject.NULL)
    put("targetDurationMs", row.targetDurationMs ?: JSONObject.NULL); put("targetDropsets", row.targetDropsets ?: JSONObject.NULL)
}.toString()
fun decodeRoutineDraft(text: String): RoutineExercise = JSONObject(text).let { o ->
    RoutineExercise(id = o.getString("id"), routineId = "", exerciseId = o.getString("exerciseId"),
        orderIndex = o.getInt("orderIndex"), targetSets = o.getInt("targetSets"), targetWarmups = o.getInt("targetWarmups"),
        targetReps = o.getInt("targetReps"), targetRestMs = o.getLong("targetRestMs"),
        groupId = if (o.isNull("groupId")) null else o.getString("groupId"),
        targetDurationMs = if (o.isNull("targetDurationMs")) null else o.getLong("targetDurationMs"),
        targetRepsMax = if (o.isNull("targetRepsMax")) null else o.getInt("targetRepsMax"),
        targetDropsets = if (o.isNull("targetDropsets")) null else o.getInt("targetDropsets"))
}
