package com.rafa.hercules.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "exercises")
data class Exercise(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val defaultUnit: String = "kg",
    // Null = fuerza (compatibilidad con filas anteriores a la migración 7->8)
    val type: String? = null,
    // Origen: USER = creado por la persona; SEED = catálogo inicial;
    // LEGACY = filas previas a la v1.14 (inicial y propio eran indistinguibles).
    @ColumnInfo(defaultValue = "'LEGACY'")
    val origin: String = "LEGACY",
    // Marca de archivado (ocultar del catálogo sin borrar historial); null = activo.
    val archivedAt: Long? = null
)

/** Los ejercicios de cardio no usan peso, repeticiones ni volumen de fuerza. */
val Exercise.isCardio: Boolean
    get() = type == "CARDIO"

@Entity(tableName = "routines")
data class Routine(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val assignedDay: Int? = null, // 1=Lunes, 7=Domingo
    // Marca de archivado (ocultar de Inicio sin borrar sus sesiones); null = activa.
    val archivedAt: Long? = null
)

@Entity(
    tableName = "routine_exercises",
    foreignKeys = [
        ForeignKey(entity = Routine::class, parentColumns = ["id"], childColumns = ["routineId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Exercise::class, parentColumns = ["id"], childColumns = ["exerciseId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [
        androidx.room.Index(value = ["routineId"]),
        androidx.room.Index(value = ["exerciseId"])
    ]
)
data class RoutineExercise(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val routineId: String,
    val exerciseId: String,
    val orderIndex: Int = 0,
    val targetSets: Int = 3,
    val targetWarmups: Int = 0,
    val targetReps: Int = 10,
    val targetRestMs: Long = 90000L,
    val groupId: String? = null,
    // Duración objetivo en ms para ejercicios de cardio (null = sin objetivo)
    val targetDurationMs: Long? = null,
    // Bajadas de dropset previstas por serie de trabajo (null = 0)
    val targetDropsets: Int? = null,
    val targetRepsMax: Int? = null
)

/** Duración objetivo del cardio en minutos (0 si no está configurada). */
val RoutineExercise.durationMinutes: Long
    get() = (targetDurationMs ?: 0L) / 60000L

/** Bajadas de dropset previstas por serie de trabajo (0 si no están configuradas). */
val RoutineExercise.dropsetStages: Int
    get() = targetDropsets ?: 0

@Entity(tableName = "sessions")
data class Session(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val routineId: String?,
    val startTime: Long = System.currentTimeMillis(),
    val endTime: Long? = null,
    val state: String = "ACTIVE", // ACTIVE, FINISHED, ABANDONED
    val overallEffort: Int? = null, // 0-10
    // Instantánea de la composición de la rutina capturada al iniciar la sesión.
    // Null en sesiones anteriores a la migración 8->9 o en sesiones sin rutina.
    // Editar la plantilla después no altera esta copia ni el historial.
    val routineSnapshot: String? = null
)

@Entity(
    tableName = "series",
    foreignKeys = [
        ForeignKey(entity = Session::class, parentColumns = ["id"], childColumns = ["sessionId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Exercise::class, parentColumns = ["id"], childColumns = ["exerciseId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [
        androidx.room.Index(value = ["sessionId"]),
        androidx.room.Index(value = ["exerciseId"])
    ]
)
data class Series(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val sessionId: String,
    val exerciseId: String,
    val isWarmup: Boolean = false,
    val weight: Double? = null,
    val unit: String = "kg",
    val reps: Int? = null,
    val effort: Int? = null, 
    val effortScale: String? = null, 
    val isCompleted: Boolean = false,
    val completedAt: Long? = null, // Para manejar temporizadores
    val orderIndex: Int = 0,
    val parentId: String? = null, // Para dropsets (ID de la serie principal)
    val groupId: String? = null, // Para biseries (ID de agrupación de ejercicios)
    // Duración realizada en ms; solo ejercicios de cardio. Vacío no equivale a cero.
    val durationMs: Long? = null,
    // Configuración inmutable de esta aparición del ejercicio en la sesión.
    val occurrenceId: String? = null,
    val exerciseOrder: Int? = null,
    val roundIndex: Int? = null,
    val stageIndex: Int? = null,
    val targetReps: Int? = null,
    val targetRestMs: Long? = null,
    val targetDurationMs: Long? = null,
    val targetDropsets: Int? = null,
    val blockRestStartedAt: Long? = null,
    val notes: String? = null,
    val targetRepsMax: Int? = null
)

data class SessionWithSeries(
    @androidx.room.Embedded val session: Session,
    @androidx.room.Relation(
        parentColumn = "id",
        entityColumn = "sessionId"
    )
    val series: List<Series>
)

@Entity(tableName = "goal_changes")
data class GoalChange(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val targetDays: Int
)
