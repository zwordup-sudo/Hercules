package com.rafa.hercules.data

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** Error de validación del respaldo con mensaje claro para la persona usuaria. */
class BackupFormatException(message: String) : Exception(message)

/** Contenido ya validado de un respaldo, listo para restaurar. */
data class BackupData(
    val routines: List<Routine>,
    val exercises: List<Exercise>,
    val routineExercises: List<RoutineExercise>,
    val sessions: List<Session>,
    val series: List<Series>,
    val goals: List<GoalChange>
)

/**
 * Codificación y validación del respaldo JSON, independiente de Android y Room para
 * poder probarla en la JVM.
 *
 * Reglas de validación:
 * - Versiones aceptadas: 1..5 (1: previo a cardio; 2: con cardio; 3: con instantánea de rutina;
 *   5: con origen y archivado de ejercicios/rutinas; los campos nuevos de 1..4 se
 *   reponen con los valores compatibles LEGACY y null).
 * - «origin» de ejercicio debe ser USER, SEED o LEGACY; «archivedAt» no puede ser negativo.
 * - Toda referencia con clave foránea real debe existir en el archivo:
 *   routine_exercises -> routines/exercises, series -> sessions/exercises,
 *   series.parentId -> series.
 * - sessions.routineId puede apuntar a una rutina ausente: la app permite borrar rutinas
 *   y conserva sus sesiones sin clave foránea.
 * - No se rechaza un archivo que la propia app pudo generar: la interfaz no limita
 *   las cotas superiores de effort ni el orden temporal de endTime, así que tampoco
 *   las valida aquí.
 * - Un respaldo sin «goals» (versiones previas) es válido: al restaurar se repone la
 *   meta por defecto igual que hizo la migración original.
 */
object BackupCodec {
    const val SCHEMA_VERSION = 6
    private val SUPPORTED_VERSIONS = 1..6
    private val SESSION_STATES = setOf("ACTIVE", "FINISHED", "CANCELLED", "ABANDONED")
    private val UNITS = setOf("kg", "lb")
    private val EFFORT_SCALES = setOf("RPE", "RIR")
    private val EXERCISE_ORIGINS = setOf("USER", "SEED", "LEGACY")

    fun serialize(
        routines: List<Routine>,
        exercises: List<Exercise>,
        routineExercises: List<RoutineExercise>,
        sessions: List<Session>,
        series: List<Series>,
        goals: List<GoalChange>
    ): String {
        val backup = JSONObject()
        backup.put("version", SCHEMA_VERSION)

        val routinesArray = JSONArray()
        routines.forEach { r ->
            val obj = JSONObject()
            obj.put("id", r.id)
            obj.put("name", r.name)
            obj.put("assignedDay", r.assignedDay ?: JSONObject.NULL)
            obj.put("archivedAt", r.archivedAt ?: JSONObject.NULL)
            routinesArray.put(obj)
        }
        backup.put("routines", routinesArray)

        val exercisesArray = JSONArray()
        exercises.forEach { e ->
            val obj = JSONObject()
            obj.put("id", e.id)
            obj.put("name", e.name)
            obj.put("defaultUnit", e.defaultUnit)
            obj.put("type", e.type ?: JSONObject.NULL)
            obj.put("origin", e.origin)
            obj.put("archivedAt", e.archivedAt ?: JSONObject.NULL)
            exercisesArray.put(obj)
        }
        backup.put("exercises", exercisesArray)

        val routineExercisesArray = JSONArray()
        routineExercises.forEach { re ->
            val obj = JSONObject()
            obj.put("id", re.id)
            obj.put("routineId", re.routineId)
            obj.put("exerciseId", re.exerciseId)
            obj.put("orderIndex", re.orderIndex)
            obj.put("targetSets", re.targetSets)
            obj.put("targetWarmups", re.targetWarmups)
            obj.put("targetReps", re.targetReps)
            obj.put("targetRepsMax", re.targetRepsMax ?: JSONObject.NULL)
            obj.put("targetRestMs", re.targetRestMs)
            obj.put("groupId", re.groupId ?: JSONObject.NULL)
            obj.put("targetDurationMs", re.targetDurationMs ?: JSONObject.NULL)
            obj.put("targetDropsets", re.targetDropsets ?: JSONObject.NULL)
            routineExercisesArray.put(obj)
        }
        backup.put("routineExercises", routineExercisesArray)

        val sessionsArray = JSONArray()
        sessions.forEach { s ->
            val obj = JSONObject()
            obj.put("id", s.id)
            obj.put("routineId", s.routineId ?: JSONObject.NULL)
            obj.put("startTime", s.startTime)
            obj.put("endTime", s.endTime ?: JSONObject.NULL)
            obj.put("state", s.state)
            obj.put("overallEffort", s.overallEffort ?: JSONObject.NULL)
            obj.put("routineSnapshot", s.routineSnapshot ?: JSONObject.NULL)
            sessionsArray.put(obj)
        }
        backup.put("sessions", sessionsArray)

        val seriesArray = JSONArray()
        series.forEach { s ->
            val obj = JSONObject()
            obj.put("id", s.id)
            obj.put("sessionId", s.sessionId)
            obj.put("exerciseId", s.exerciseId)
            obj.put("isWarmup", s.isWarmup)
            obj.put("weight", s.weight ?: JSONObject.NULL)
            obj.put("unit", s.unit)
            obj.put("reps", s.reps ?: JSONObject.NULL)
            obj.put("effort", s.effort ?: JSONObject.NULL)
            obj.put("effortScale", s.effortScale ?: JSONObject.NULL)
            obj.put("isCompleted", s.isCompleted)
            obj.put("completedAt", s.completedAt ?: JSONObject.NULL)
            obj.put("orderIndex", s.orderIndex)
            obj.put("parentId", s.parentId ?: JSONObject.NULL)
            obj.put("groupId", s.groupId ?: JSONObject.NULL)
            obj.put("durationMs", s.durationMs ?: JSONObject.NULL)
            obj.put("occurrenceId", s.occurrenceId ?: JSONObject.NULL)
            obj.put("exerciseOrder", s.exerciseOrder ?: JSONObject.NULL)
            obj.put("roundIndex", s.roundIndex ?: JSONObject.NULL)
            obj.put("stageIndex", s.stageIndex ?: JSONObject.NULL)
            obj.put("targetReps", s.targetReps ?: JSONObject.NULL)
            obj.put("targetRepsMax", s.targetRepsMax ?: JSONObject.NULL)
            obj.put("targetRestMs", s.targetRestMs ?: JSONObject.NULL)
            obj.put("targetDurationMs", s.targetDurationMs ?: JSONObject.NULL)
            obj.put("targetDropsets", s.targetDropsets ?: JSONObject.NULL)
            obj.put("blockRestStartedAt", s.blockRestStartedAt ?: JSONObject.NULL)
            obj.put("notes", s.notes ?: JSONObject.NULL)
            seriesArray.put(obj)
        }
        backup.put("series", seriesArray)

        val goalsArray = JSONArray()
        goals.forEach { g ->
            val obj = JSONObject()
            obj.put("id", g.id)
            obj.put("timestamp", g.timestamp)
            obj.put("targetDays", g.targetDays)
            goalsArray.put(obj)
        }
        backup.put("goals", goalsArray)

        return backup.toString()
    }

    fun parse(jsonString: String): BackupData {
        val root = try {
            JSONObject(jsonString)
        } catch (e: JSONException) {
            throw BackupFormatException("El archivo no es un respaldo JSON válido o está incompleto.")
        }

        if (!root.has("version")) {
            throw BackupFormatException("Falta el campo «version» del respaldo.")
        }
        val version = try {
            root.strictInt("version")
        } catch (e: JSONException) {
            throw BackupFormatException("El campo «version» del respaldo no es un número válido.")
        }
        if (version !in SUPPORTED_VERSIONS) {
            throw BackupFormatException(
                "Versión de respaldo no soportada: $version. Usa un respaldo con versión entre ${SUPPORTED_VERSIONS.first} y ${SUPPORTED_VERSIONS.last}."
            )
        }

        val routines = parseRows("routines", rows(root, "routines")) { routineRow(it) }
        val exercises = parseRows("exercises", rows(root, "exercises")) { exerciseRow(it) }
        val routineExercises = parseRows("routineExercises", rows(root, "routineExercises")) { routineExerciseRow(it) }
        val sessions = parseRows("sessions", rows(root, "sessions")) { sessionRow(it) }
        val series = parseRows("series", rows(root, "series")) { seriesRow(it) }
        val goals = if (root.has("goals")) {
            parseRows("goals", rows(root, "goals")) { goalRow(it) }
        } else {
            emptyList()
        }

        requireUnique(routines.map { it.id }, "routines")
        requireUnique(exercises.map { it.id }, "exercises")
        requireUnique(routineExercises.map { it.id }, "routineExercises")
        requireUnique(sessions.map { it.id }, "sessions")
        requireUnique(series.map { it.id }, "series")
        requireUnique(goals.map { it.id }, "goals")

        val routineIds = routines.mapTo(HashSet()) { it.id }
        val exerciseIds = exercises.mapTo(HashSet()) { it.id }
        val sessionIds = sessions.mapTo(HashSet()) { it.id }
        val seriesIds = series.mapTo(HashSet()) { it.id }

        for (re in routineExercises) {
            if (re.routineId !in routineIds) {
                throw BackupFormatException("«routineExercises» referencia una rutina inexistente: ${re.routineId}.")
            }
            if (re.exerciseId !in exerciseIds) {
                throw BackupFormatException("«routineExercises» referencia un ejercicio inexistente: ${re.exerciseId}.")
            }
        }
        for (s in series) {
            if (s.sessionId !in sessionIds) {
                throw BackupFormatException("«series» referencia una sesión inexistente: ${s.sessionId}.")
            }
            if (s.exerciseId !in exerciseIds) {
                throw BackupFormatException("«series» referencia un ejercicio inexistente: ${s.exerciseId}.")
            }
            if (s.parentId != null && s.parentId !in seriesIds) {
                throw BackupFormatException("«series» referencia una serie principal inexistente: ${s.parentId}.")
            }
        }

        val byId = series.associateBy { it.id }
        series.forEach { row ->
            row.parentId?.let { id ->
                val parent = byId.getValue(id)
                if (id == row.id || parent.parentId != null || parent.sessionId != row.sessionId || parent.exerciseId != row.exerciseId) {
                    throw BackupFormatException("Jerarquía de dropset inválida: ${row.id}.")
                }
            }
            listOf(row.exerciseOrder, row.roundIndex, row.stageIndex, row.targetReps, row.targetDropsets).forEach {
                requireNonNegative(it, "configuración de serie")
            }
            listOf(row.targetRestMs, row.targetDurationMs).forEach { requireNonNegative(it, "configuración de serie") }
        }
        series.groupBy { it.sessionId to it.occurrenceKey() }.values.forEach { rows ->
            if (rows.map { it.exerciseId }.distinct().size > 1) throw BackupFormatException("Una aparición de ejercicio contiene ejercicios distintos.")
        }
        if (sessions.count { it.state == "ACTIVE" } > 1) throw BackupFormatException("El respaldo contiene más de una sesión activa.")
        routineExercises.filter { it.groupId != null }.groupBy { it.routineId to it.groupId }.values.forEach { group ->
            if (group.size > 2) throw BackupFormatException("Una biserie no puede contener más de dos ejercicios.")
        }
        series.filter { it.parentId == null && it.groupId != null }.groupBy { it.sessionId to it.groupId }.values.forEach { group ->
            if (group.map { it.occurrenceKey() }.distinct().size > 2) throw BackupFormatException("Una biserie de sesión contiene más de dos ejercicios.")
        }
        // Hijas legadas de aproximación también son aproximación, nunca volumen de trabajo.
        val normalized = series.map { row -> row.parentId?.let { row.copy(isWarmup = byId.getValue(it).isWarmup) } ?: row }
        return BackupData(routines, exercises, routineExercises, sessions, normalized, goals)
    }

    private fun rows(root: JSONObject, name: String): List<JSONObject> {
        if (!root.has(name)) {
            throw BackupFormatException("Falta la colección «$name» en el respaldo.")
        }
        val array = try {
            root.getJSONArray(name)
        } catch (e: JSONException) {
            throw BackupFormatException("La colección «$name» debe ser una lista.")
        }
        return (0 until array.length()).map { i ->
            try {
                array.getJSONObject(i)
            } catch (e: JSONException) {
                throw BackupFormatException("La fila #${i + 1} de «$name» no es un objeto válido.")
            }
        }
    }

    private fun <T> parseRows(name: String, rows: List<JSONObject>, mapper: (JSONObject) -> T): List<T> =
        rows.mapIndexed { i, obj ->
            try {
                mapper(obj)
            } catch (e: JSONException) {
                throw BackupFormatException("Fila inválida en «$name» #${i + 1}: ${e.message}")
            } catch (e: BackupFormatException) {
                throw BackupFormatException("Fila inválida en «$name» #${i + 1}: ${e.message}")
            }
        }

    private fun requireUnique(ids: List<String>, collection: String) {
        val seen = HashSet<String>(ids.size)
        for (id in ids) {
            if (!seen.add(id)) {
                throw BackupFormatException("Id duplicado en «$collection»: $id.")
            }
        }
    }

    private fun requiredId(obj: JSONObject): String {
        val id = obj.getString("id")
        if (id.isBlank()) {
            throw BackupFormatException("El campo «id» no puede estar vacío.")
        }
        return id
    }

    private fun JSONObject.strictLong(name: String): Long {
        val value = get(name)
        if (value is Byte || value is Short || value is Int || value is Long) return (value as Number).toLong()
        throw BackupFormatException("El campo «$name» no es un número entero válido.")
    }
    private fun JSONObject.strictInt(name: String): Int {
        val value = strictLong(name)
        if (value !in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) throw BackupFormatException("«$name» fuera de rango entero.")
        return value.toInt()
    }

    private fun JSONObject.intOrNull(name: String): Int? = if (isNull(name)) null else strictInt(name)
    private fun JSONObject.longOrNull(name: String): Long? = if (isNull(name)) null else strictLong(name)
    private fun JSONObject.stringOrNull(name: String): String? = if (isNull(name)) null else getString(name)
    private fun JSONObject.doubleOrNull(name: String): Double? = if (isNull(name)) null else getDouble(name)

    private fun requireNonNegative(value: Long?, field: String) {
        if (value != null && value < 0L) {
            throw BackupFormatException("«$field» no puede ser negativo: $value.")
        }
    }

    private fun requireNonNegative(value: Int?, field: String) {
        if (value != null && value < 0) {
            throw BackupFormatException("«$field» no puede ser negativo: $value.")
        }
    }

    private fun routineRow(obj: JSONObject): Routine {
        val assignedDay = obj.intOrNull("assignedDay")
        if (assignedDay != null && assignedDay !in 1..7) {
            throw BackupFormatException("«assignedDay» fuera de rango: $assignedDay (válido 1..7).")
        }
        // Ausente en v1..v4: null = activa (valor compatible con el formato anterior).
        val archivedAt = obj.longOrNull("archivedAt")
        requireNonNegative(archivedAt, "archivedAt")
        return Routine(
            id = requiredId(obj),
            name = obj.getString("name"),
            assignedDay = assignedDay,
            archivedAt = archivedAt
        )
    }

    private fun exerciseRow(obj: JSONObject): Exercise {
        val defaultUnit = obj.stringOrNull("defaultUnit") ?: "kg"
        if (defaultUnit !in UNITS) {
            throw BackupFormatException("«defaultUnit» no reconocida: «$defaultUnit» (válidas: kg, lb).")
        }
        val type = obj.stringOrNull("type")
        if (type != null && type.isBlank()) {
            throw BackupFormatException("«type» de ejercicio no puede estar vacío.")
        }
        // Ausente en v1..v4: LEGACY = filas anteriores al archivado (valor compatible).
        val origin = obj.stringOrNull("origin") ?: "LEGACY"
        if (origin !in EXERCISE_ORIGINS) {
            throw BackupFormatException("«origin» de ejercicio no reconocida: «$origin» (válidas: USER, SEED, LEGACY).")
        }
        val archivedAt = obj.longOrNull("archivedAt")
        requireNonNegative(archivedAt, "archivedAt")
        return Exercise(
            id = requiredId(obj),
            name = obj.getString("name"),
            defaultUnit = defaultUnit,
            type = type,
            origin = origin,
            archivedAt = archivedAt
        )
    }

    private fun parseRepRange(obj: JSONObject, min: Int?): Int? {
        val max = obj.intOrNull("targetRepsMax")
        if (!validRepRange(min, max)) throw BackupFormatException("Rango de repeticiones inválido.")
        return max
    }

    private fun routineExerciseRow(obj: JSONObject): RoutineExercise {
        val orderIndex = obj.strictInt("orderIndex")
        val targetSets = obj.strictInt("targetSets")
        val targetWarmups = obj.intOrNull("targetWarmups") ?: 0
        val targetReps = obj.strictInt("targetReps")
        val targetRestMs = obj.strictLong("targetRestMs")
        val targetDurationMs = obj.longOrNull("targetDurationMs")
        val targetDropsets = obj.intOrNull("targetDropsets")
        requireNonNegative(orderIndex, "orderIndex")
        requireNonNegative(targetSets, "targetSets")
        requireNonNegative(targetWarmups, "targetWarmups")
        requireNonNegative(targetReps, "targetReps")
        requireNonNegative(targetRestMs, "targetRestMs")
        requireNonNegative(targetDurationMs, "targetDurationMs")
        requireNonNegative(targetDropsets, "targetDropsets")
        return RoutineExercise(
            id = requiredId(obj),
            routineId = obj.getString("routineId"),
            exerciseId = obj.getString("exerciseId"),
            orderIndex = orderIndex,
            targetSets = targetSets,
            targetWarmups = targetWarmups,
            targetReps = targetReps,
            targetRepsMax = parseRepRange(obj, targetReps),
            targetRestMs = targetRestMs,
            groupId = obj.stringOrNull("groupId"),
            targetDurationMs = targetDurationMs,
            targetDropsets = targetDropsets
        )
    }

    private fun sessionRow(obj: JSONObject): Session {
        val state = obj.getString("state")
        if (state !in SESSION_STATES) {
            throw BackupFormatException("«state» de sesión no reconocido: «$state» (válidos: ACTIVE, FINISHED, CANCELLED, ABANDONED).")
        }
        val overallEffort = obj.intOrNull("overallEffort")
        if (overallEffort != null && overallEffort !in 0..10) {
            throw BackupFormatException("«overallEffort» fuera de rango: $overallEffort (válido 0..10).")
        }
        return Session(
            id = requiredId(obj),
            routineId = obj.stringOrNull("routineId"),
            startTime = obj.strictLong("startTime"),
            // endTime no se exige mayor que startTime: el reloj puede cambiar durante la sesión.
            endTime = obj.longOrNull("endTime"),
            state = state,
            overallEffort = overallEffort,
            routineSnapshot = obj.stringOrNull("routineSnapshot")
        )
    }

    private fun seriesRow(obj: JSONObject): Series {
        val weight = obj.doubleOrNull("weight")
        if (weight != null && (weight.isNaN() || weight.isInfinite() || weight < 0.0)) {
            throw BackupFormatException("«weight» inválido: debe ser un número mayor o igual a 0.")
        }
        val unit = obj.getString("unit")
        if (unit !in UNITS) {
            throw BackupFormatException("«unit» no reconocida: «$unit» (válidas: kg, lb).")
        }
        val reps = obj.intOrNull("reps")
        requireNonNegative(reps, "reps")
        val effort = obj.intOrNull("effort")
        requireNonNegative(effort, "effort")
        val effortScale = obj.stringOrNull("effortScale")
        if (effortScale != null && effortScale !in EFFORT_SCALES) {
            throw BackupFormatException("«effortScale» no reconocida: «$effortScale» (válidas: RPE, RIR).")
        }
        val completedAt = obj.longOrNull("completedAt")
        val orderIndex = obj.strictInt("orderIndex")
        requireNonNegative(orderIndex, "orderIndex")
        val durationMs = obj.longOrNull("durationMs")
        requireNonNegative(durationMs, "durationMs")
        return Series(
            id = requiredId(obj),
            sessionId = obj.getString("sessionId"),
            exerciseId = obj.getString("exerciseId"),
            isWarmup = obj.getBoolean("isWarmup"),
            weight = weight,
            unit = unit,
            reps = reps,
            effort = effort,
            effortScale = effortScale,
            isCompleted = obj.getBoolean("isCompleted"),
            completedAt = completedAt,
            orderIndex = orderIndex,
            parentId = obj.stringOrNull("parentId"),
            groupId = obj.stringOrNull("groupId"),
            durationMs = durationMs,
            occurrenceId = obj.stringOrNull("occurrenceId"),
            exerciseOrder = obj.intOrNull("exerciseOrder"),
            roundIndex = obj.intOrNull("roundIndex"),
            stageIndex = obj.intOrNull("stageIndex"),
            targetReps = obj.intOrNull("targetReps"),
            targetRepsMax = parseRepRange(obj, obj.intOrNull("targetReps")),
            targetRestMs = obj.longOrNull("targetRestMs"),
            targetDurationMs = obj.longOrNull("targetDurationMs"),
            targetDropsets = obj.intOrNull("targetDropsets"),
            blockRestStartedAt = obj.longOrNull("blockRestStartedAt"),
            notes = obj.stringOrNull("notes")
        )
    }

    private fun goalRow(obj: JSONObject): GoalChange {
        val timestamp = obj.strictLong("timestamp")
        requireNonNegative(timestamp, "timestamp")
        val targetDays = obj.strictInt("targetDays")
        if (targetDays !in 1..7) {
            throw BackupFormatException("«targetDays» fuera de rango: $targetDays (válido 1..7).")
        }
        return GoalChange(
            id = requiredId(obj),
            timestamp = timestamp,
            targetDays = targetDays
        )
    }
}
