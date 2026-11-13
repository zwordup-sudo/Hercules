package com.rafa.hercules.data

import androidx.room.withTransaction

/**
 * Exporta e importa el respaldo JSON de la base de datos.
 *
 * - exportData: lectura de todas las colecciones dentro de una transacción para que el
 *   archivo refleje un estado consistente aunque haya escrituras concurrentes.
 * - importData: valida y parsea el archivo completo ANTES de tocar la base de datos;
 *   solo entonces reemplaza todo dentro de una transacción (o se restaura el estado
 *   anterior completo si algo falla a mitad).
 */
class BackupManager(private val db: AppDatabase) {

    /**
     * Solo para pruebas instrumentadas: se ejecuta dentro de la transacción de importación,
     * después de borrar las tablas y antes de insertar, para provocar un fallo y comprobar
     * el rollback con datos reales.
     */
    internal var testFailureAfterClear: (() -> Unit)? = null

    suspend fun exportData(): String = db.withTransaction {
        val dao = db.gymDao()
        val json = BackupCodec.serialize(
            routines = dao.getAllRoutinesSync(),
            exercises = dao.getAllExercisesSync(),
            routineExercises = dao.getAllRoutineExercisesSync(),
            sessions = dao.getAllSessionsSync(),
            series = dao.getAllSeriesSync(),
            goals = dao.getAllGoalChangesSync()
        )
        BackupCodec.parse(json) // Un archivo propio también debe ser restaurable.
        json
    }

    suspend fun importData(jsonString: String) {
        // 1..3 = formatos anteriores a la validación; 4 = con metas; 5 = con origen y archivado.
        // Los formatos 1..3 necesitan reponer la configuración legada de sesiones.
        val parsed = BackupCodec.parse(jsonString)
        val data = if (org.json.JSONObject(jsonString).getInt("version") < 4) restoreLegacyConfig(parsed) else parsed

        // 2. Transacción para reemplazar todo o restaurar el estado anterior completo.
        LocalMutations.replace {
          db.withTransaction {
            val dao = db.gymDao()
            dao.clearAllTables()
            testFailureAfterClear?.invoke()
            dao.insertExercises(data.exercises)
            dao.insertRoutines(data.routines)
            dao.insertRoutineExercises(data.routineExercises)
            dao.insertSessions(data.sessions)
            dao.insertSeries(data.series)
            if (data.goals.isEmpty()) {
                // Réplica de la migración original: respaldos sin metas repone la meta por defecto.
                dao.insertGoalChange(GoalChange(id = "default_goal", timestamp = 0L, targetDays = 3))
            } else {
                data.goals.forEach { dao.insertGoalChange(it) }
            }
          }
        }
    }
}
