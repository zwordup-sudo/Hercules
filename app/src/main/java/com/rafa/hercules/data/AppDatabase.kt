package com.rafa.hercules.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Dao
interface GymDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExercise(exercise: Exercise)

    @Update
    suspend fun updateExercise(exercise: Exercise)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoutine(routine: Routine)
    
    @Update
    suspend fun updateRoutine(routine: Routine)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: Session)

    @Update
    suspend fun updateSession(session: Session)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoutineExercise(routineExercise: RoutineExercise)
    
    @Query("SELECT * FROM routine_exercises WHERE routineId = :routineId ORDER BY orderIndex")
    suspend fun getRoutineExercises(routineId: String): List<RoutineExercise>

    @Query("SELECT * FROM routines")
    fun getAllRoutines(): Flow<List<Routine>>

    /** Rutinas visibles en Inicio: excluye las archivadas (sus sesiones siguen en historial). */
    @Query("SELECT * FROM routines WHERE archivedAt IS NULL")
    fun observeActiveRoutines(): Flow<List<Routine>>

    @Query("SELECT * FROM routines WHERE id = :routineId LIMIT 1")
    suspend fun getRoutineById(routineId: String): Routine?

    @Query("SELECT * FROM sessions WHERE routineId = :routineId AND state = 'ACTIVE' LIMIT 1")
    suspend fun getActiveSessionForRoutine(routineId: String): Session?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSeries(series: Series)

    @Update
    suspend fun updateSeries(series: Series)

    @Update
    suspend fun updateSeriesRows(rows: List<Series>)

    @Query("SELECT * FROM series WHERE sessionId = :sessionId ORDER BY exerciseOrder, orderIndex, stageIndex, id")
    suspend fun getSessionSeries(sessionId: String): List<Series>

    /** Validación y sustitución se ejecutan en la misma transacción Room. */
    @Transaction
    suspend fun substituteSessionExercise(sessionId: String, occurrenceId: String, originalId: String,
        replacementId: String, discardConfirmed: Boolean) {
        val session = getSessionById(sessionId) ?: error("La sesión ya no existe.")
        require(session.state == "ACTIVE") { "Solo puedes sustituir ejercicios de una sesión activa." }
        val rows = getSessionSeries(sessionId).filter { it.occurrenceKey() == occurrenceId }
        require(rows.isNotEmpty() && rows.all { it.exerciseId == originalId }) { "Esta aparición cambió. Vuelve a abrir el menú." }
        require(!rows.hasCompletedSeries()) { "No se puede sustituir: esta aparición ya tiene una serie completada (incluidas aproximaciones y bajadas)." }
        require(discardConfirmed || !rows.hasRecordedValues()) { "Confirma el descarte de los datos ingresados antes de sustituir." }
        val original = getExerciseById(originalId) ?: error("El ejercicio original ya no existe.")
        val replacement = getExerciseById(replacementId) ?: error("El sustituto ya no existe.")
        require(replacement.id != original.id && replacement.archivedAt == null && replacement.isCardio == original.isCardio) {
            "Elige otro ejercicio activo del mismo tipo."
        }
        updateSeriesRows(substituteRows(rows, replacement))
        updateSession(session.copy(routineSnapshot = substituteSnapshotExercise(session.routineSnapshot, rows.first(), replacement.id)))
    }
    
    @Query("SELECT * FROM series WHERE sessionId = :sessionId ORDER BY exerciseOrder, orderIndex, stageIndex, id")
    fun getSeriesForSession(sessionId: String): Flow<List<Series>>

    @Query("SELECT * FROM series WHERE id = :id LIMIT 1")
    suspend fun getSeriesById(id: String): Series?

    @Query("DELETE FROM routine_exercises WHERE routineId = :routineId")
    suspend fun deleteRoutineExercises(routineId: String)

    @Transaction
    suspend fun seedExercisesIfEmpty() {
        if (getAllExercises().isEmpty()) insertExercises(listOf(
            Exercise(name = "Press de Banca", origin = "SEED"),
            Exercise(name = "Sentadilla", origin = "SEED"),
            Exercise(name = "Peso Muerto", origin = "SEED")))
    }

    /**
     * Archiva un ejercicio (v1.14): oculta del catálogo de selección sin borrar
     * series ni sesiones históricas. Bloquea los iniciales y los que siguen
     * en uso por rutinas no archivadas, indicando cuáles.
     */
    @Transaction
    suspend fun archiveExercise(exerciseId: String, now: Long) {
        val exercise = getExerciseById(exerciseId) ?: error("El ejercicio ya no existe.")
        require(exercise.archivedAt == null) { "«${exercise.name}» ya estaba eliminado." }
        require(exercise.origin != "SEED") { "«${exercise.name}» es un ejercicio inicial: no se puede eliminar." }
        val usedIn = activeRoutinesUsingExercise(exerciseId)
        require(usedIn.isEmpty()) {
            "«${exercise.name}» se usa en: ${usedIn.joinToString(", ")}. Retíralo de esas rutinas antes de eliminarlo."
        }
        updateExercise(exercise.copy(archivedAt = now))
    }

    /**
     * Archiva una rutina (v1.14): sale de Inicio y del selector semanal sin
     * borrar sesiones históricas. Bloquea si tiene un entrenamiento activo.
     */
    @Transaction
    suspend fun archiveRoutine(routineId: String, now: Long) {
        val routine = getRoutineById(routineId) ?: error("La rutina ya no existe.")
        require(routine.archivedAt == null) { "«${routine.name}» ya estaba eliminada." }
        require(getActiveSessionForRoutine(routineId) == null) {
            "«${routine.name}» tiene un entrenamiento activo: finalízalo o cancélalo antes de eliminar la rutina."
        }
        updateRoutine(routine.copy(archivedAt = now))
    }

    /** Nombres de rutinas no archivadas que contienen el ejercicio (para el aviso de bloqueo). */
    @Query("""
        SELECT r.name FROM routines r
        INNER JOIN routine_exercises re ON re.routineId = r.id
        WHERE re.exerciseId = :exerciseId AND r.archivedAt IS NULL
        ORDER BY r.name
    """)
    suspend fun activeRoutinesUsingExercise(exerciseId: String): List<String>

    @Transaction
    suspend fun markRoundRest(roots: List<Series>, now: Long) {
        roots.forEach { updateSeries(it.copy(blockRestStartedAt = now)) }
    }

    @Transaction
    suspend fun createSessionIfNone(session: Session, rows: List<Series>): Session {
        getActiveSession()?.let { return it }
        insertSession(session)
        insertSeries(rows)
        return session
    }

    @Transaction
    suspend fun saveRoutineTemplate(routine: Routine, rows: List<RoutineExercise>) {
        // UPDATE preserva el ID; REPLACE de un padre podría disparar CASCADE.
        if (getRoutineById(routine.id) == null) insertRoutine(routine) else updateRoutine(routine)
        deleteRoutineExercises(routine.id)
        insertRoutineExercises(rows)
    }

    @Query("SELECT * FROM sessions WHERE id = :sessionId LIMIT 1")
    suspend fun getSessionById(sessionId: String): Session?

    @Query("SELECT * FROM exercises WHERE id = :exerciseId LIMIT 1")
    suspend fun getExerciseById(exerciseId: String): Exercise?

    @Query("SELECT * FROM exercises")
    suspend fun getAllExercises(): List<Exercise>

    /** Emite el catálogo de ejercicios y se actualiza cuando cambia (nuevo ejercicio cardio, etc.). */
    @Query("SELECT * FROM exercises")
    fun observeExercises(): Flow<List<Exercise>>

    /** Catálogo de selección: excluye los archivados; el histórico conserva todos. */
    @Query("SELECT * FROM exercises WHERE archivedAt IS NULL")
    fun observeActiveExercises(): Flow<List<Exercise>>

    @Query("SELECT * FROM sessions WHERE state = 'ACTIVE' ORDER BY startTime DESC LIMIT 1")
    suspend fun getActiveSession(): Session?

    /** Sesión en curso para el aviso de reanudación en Inicio (v1.15). */
    @Query("SELECT * FROM sessions WHERE state = 'ACTIVE' ORDER BY startTime DESC LIMIT 1")
    fun observeActiveSession(): Flow<Session?>

    /** Configuración completa para los contadores de tarjetas en Inicio (v1.15). */
    @Query("SELECT * FROM routine_exercises")
    fun observeRoutineExercises(): Flow<List<RoutineExercise>>

    @Query("SELECT * FROM sessions WHERE state = 'FINISHED' ORDER BY startTime DESC")
    fun getFinishedSessions(): Flow<List<Session>>

    @androidx.room.Transaction
    @Query("SELECT * FROM sessions WHERE state = 'FINISHED' ORDER BY startTime DESC")
    fun getFinishedSessionsWithSeries(): Flow<List<SessionWithSeries>>
    
    @Query("""
        SELECT s.* FROM series s
        JOIN sessions sess ON sess.id = s.sessionId
        WHERE sess.state = 'FINISHED' AND sess.startTime < :beforeStartTime
          AND s.sessionId != :sessionId AND s.isCompleted = 1 AND s.isWarmup = 0
          AND s.parentId IS NULL AND s.weight > 0 AND s.reps > 0
          AND s.exerciseId IN (SELECT exerciseId FROM series WHERE sessionId = :sessionId)
          AND NOT EXISTS (
              SELECT 1 FROM series child WHERE child.sessionId = s.sessionId AND child.parentId = s.id
          )
    """)
    fun observeRecordHistory(sessionId: String, beforeStartTime: Long): Flow<List<Series>>

    @Query("SELECT * FROM goal_changes ORDER BY timestamp ASC")
    fun getGoalChanges(): Flow<List<GoalChange>>
    
    @Insert
    suspend fun insertGoalChange(goalChange: GoalChange)

    @Query("""
        SELECT s.* FROM series s
        INNER JOIN sessions sess ON s.sessionId = sess.id
        WHERE s.exerciseId = :exerciseId AND s.isCompleted = 1
        AND sess.id = (
            SELECT sess2.id FROM sessions sess2
            INNER JOIN series s2 ON s2.sessionId = sess2.id
            WHERE s2.exerciseId = :exerciseId AND sess2.state = 'FINISHED' AND s2.isCompleted = 1 AND sess2.id != :excludingSessionId AND sess2.startTime < :beforeStartTime
            ORDER BY sess2.endTime DESC LIMIT 1
        )
        ORDER BY s.exerciseOrder, s.orderIndex, s.stageIndex, s.id
    """)
    suspend fun getPreviousSeriesForExercise(exerciseId: String, excludingSessionId: String, beforeStartTime: Long): List<Series>

    /**
     * Obtiene la configuración de RoutineExercise asociada al ejercicio de una sesión.
     * Lo necesita completeSeries para leer targetRestMs en lugar del valor fijo.
     */
    @Query("""
        SELECT re.* FROM routine_exercises re
        INNER JOIN sessions s ON s.routineId = re.routineId
        WHERE s.id = :sessionId AND re.exerciseId = :exerciseId
        LIMIT 1
    """)
    suspend fun getRoutineExerciseForSession(sessionId: String, exerciseId: String): RoutineExercise?

    @Query("SELECT * FROM routines")
    suspend fun getAllRoutinesSync(): List<Routine>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoutines(routines: List<Routine>)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExercises(exercises: List<Exercise>)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoutineExercises(routineExercises: List<RoutineExercise>)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSessions(sessions: List<Session>)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSeries(series: List<Series>)

    @Query("SELECT * FROM exercises")
    suspend fun getAllExercisesSync(): List<Exercise>

    @Query("SELECT * FROM routine_exercises")
    suspend fun getAllRoutineExercisesSync(): List<RoutineExercise>

    @Query("SELECT * FROM sessions")
    suspend fun getAllSessionsSync(): List<Session>

    @Query("SELECT * FROM series")
    suspend fun getAllSeriesSync(): List<Series>

    @Query("SELECT * FROM goal_changes")
    suspend fun getAllGoalChangesSync(): List<GoalChange>

    @Query("DELETE FROM routines")
    suspend fun clearRoutines()
    @Query("DELETE FROM exercises")
    suspend fun clearExercises()
    @Query("DELETE FROM routine_exercises")
    suspend fun clearRoutineExercises()
    @Query("DELETE FROM sessions")
    suspend fun clearSessions()
    @Query("DELETE FROM series")
    suspend fun clearSeries()
    @Query("DELETE FROM goal_changes")
    suspend fun clearGoalChanges()

    @Transaction
    suspend fun clearAllTables() {
        clearSeries()
        clearSessions()
        clearRoutineExercises()
        clearRoutines()
        clearExercises()
        clearGoalChanges()
    }
}

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE series ADD COLUMN parentId TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE series ADD COLUMN groupId TEXT DEFAULT NULL")
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `routine_exercises` (`id` TEXT NOT NULL, `routineId` TEXT NOT NULL, `exerciseId` TEXT NOT NULL, `orderIndex` INTEGER NOT NULL, `targetSets` INTEGER NOT NULL, `targetReps` INTEGER NOT NULL, `targetRestMs` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`routineId`) REFERENCES `routines`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`exerciseId`) REFERENCES `exercises`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_routine_exercises_routineId` ON `routine_exercises` (`routineId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_routine_exercises_exerciseId` ON `routine_exercises` (`exerciseId`)")
        db.execSQL("ALTER TABLE routines ADD COLUMN assignedDay INTEGER DEFAULT NULL")
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE routine_exercises ADD COLUMN targetWarmups INTEGER NOT NULL DEFAULT 0")
    }
}

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE routine_exercises ADD COLUMN groupId TEXT")
    }
}

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE sessions ADD COLUMN overallEffort INTEGER DEFAULT NULL")
    }
}

val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `goal_changes` (`id` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, `targetDays` INTEGER NOT NULL, PRIMARY KEY(`id`))")
        // Insert a default goal of 3 days a week if it doesn't exist
        db.execSQL("INSERT INTO goal_changes (id, timestamp, targetDays) VALUES ('default_goal', 0, 3)")
    }
}

/**
 * Cardio en rutinas + dropsets configurados en plantilla.
 * Migración conservadora: solo ALTER TABLE ADD COLUMN de columnas anulables,
 * sin tocar filas existentes ni datos previos (null = comportamiento anterior).
 */
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE exercises ADD COLUMN type TEXT")
        db.execSQL("ALTER TABLE routine_exercises ADD COLUMN targetDurationMs INTEGER")
        db.execSQL("ALTER TABLE routine_exercises ADD COLUMN targetDropsets INTEGER")
        db.execSQL("ALTER TABLE series ADD COLUMN durationMs INTEGER")
    }
}

/**
 * Instantánea de composición de rutina en la sesión.
 * Migración conservadora: solo ALTER TABLE ADD COLUMN anulable,
 * sin tocar filas existentes (null = sesión anterior, comparabilidad limitada).
 */
val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE sessions ADD COLUMN routineSnapshot TEXT")
    }
}

val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        listOf("occurrenceId" to "TEXT", "exerciseOrder" to "INTEGER", "roundIndex" to "INTEGER",
            "stageIndex" to "INTEGER", "targetReps" to "INTEGER", "targetRestMs" to "INTEGER",
            "targetDurationMs" to "INTEGER", "targetDropsets" to "INTEGER",
            "blockRestStartedAt" to "INTEGER", "notes" to "TEXT").forEach { (name, type) ->
            db.execSQL("ALTER TABLE series ADD COLUMN $name $type")
        }
        // Filas legadas no permiten recuperar dos apariciones del mismo ejercicio.
        // Conservamos sus datos y fijamos una aparición por ejercicio, sin inventar series.
        db.execSQL("UPDATE series SET occurrenceId = exerciseId, stageIndex = CASE WHEN parentId IS NULL THEN 0 ELSE (SELECT COUNT(*) FROM series sibling WHERE sibling.parentId = series.parentId AND sibling.rowid <= series.rowid) END")
        db.execSQL("UPDATE series SET exerciseOrder = COALESCE((SELECT MIN(re.orderIndex) FROM routine_exercises re JOIN sessions se ON se.routineId = re.routineId WHERE se.id = series.sessionId AND re.exerciseId = series.exerciseId), (SELECT MIN(x.rowid) FROM series x WHERE x.sessionId = series.sessionId AND x.exerciseId = series.exerciseId))")
        db.execSQL("UPDATE series SET roundIndex = CASE WHEN isWarmup = 1 THEN orderIndex ELSE orderIndex - (SELECT COUNT(*) FROM series w WHERE w.sessionId = series.sessionId AND w.exerciseId = series.exerciseId AND w.parentId IS NULL AND w.isWarmup = 1 AND w.orderIndex < series.orderIndex) END WHERE parentId IS NULL")
        db.execSQL("UPDATE series SET roundIndex = (SELECT p.roundIndex FROM series p WHERE p.id = series.parentId), groupId = (SELECT p.groupId FROM series p WHERE p.id = series.parentId), isWarmup = (SELECT p.isWarmup FROM series p WHERE p.id = series.parentId) WHERE parentId IS NOT NULL")
        listOf("targetReps", "targetRestMs", "targetDurationMs", "targetDropsets").forEach { name ->
            db.execSQL("UPDATE series SET $name = (SELECT re.$name FROM routine_exercises re JOIN sessions se ON se.routineId = re.routineId WHERE se.id = series.sessionId AND re.exerciseId = series.exerciseId ORDER BY re.orderIndex LIMIT 1)")
        }
    }
}

/**
 * Archivado de ejercicios y rutinas (v1.14).
 * Migración conservadora: solo ALTER TABLE ADD COLUMN; todas las filas
 * existentes quedan en LEGACY porque la semilla inicial y los ejercicios
 * propios usaban el mismo generador de id y no pueden distinguirse.
 */
val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE exercises ADD COLUMN origin TEXT NOT NULL DEFAULT 'LEGACY'")
        db.execSQL("ALTER TABLE exercises ADD COLUMN archivedAt INTEGER")
        db.execSQL("ALTER TABLE routines ADD COLUMN archivedAt INTEGER")
    }
}

val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE routine_exercises ADD COLUMN targetRepsMax INTEGER")
        db.execSQL("ALTER TABLE series ADD COLUMN targetRepsMax INTEGER")
    }
}

@Database(entities = [Exercise::class, Routine::class, Session::class, Series::class, RoutineExercise::class, GoalChange::class], version = 12, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun gymDao(): GymDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "hercules_db"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
