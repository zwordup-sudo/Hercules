package com.rafa.hercules.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * Esquema v10 en SQL real (copiado de app/schemas/com.rafa.hercules.data.AppDatabase/10.json)
 * para la prueba de migración 10→11. Vive en constantes fuera de las llamadas
 * `old.execSQL("...")` para que tools/check_sql.py no lo ejecute en su base parcial.
 */
private val V10_STATEMENTS = listOf(
    "CREATE TABLE IF NOT EXISTS `exercises` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `defaultUnit` TEXT NOT NULL, `type` TEXT, PRIMARY KEY(`id`))",
    "CREATE TABLE IF NOT EXISTS `routines` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `assignedDay` INTEGER, PRIMARY KEY(`id`))",
    "CREATE TABLE IF NOT EXISTS `sessions` (`id` TEXT NOT NULL, `routineId` TEXT, `startTime` INTEGER NOT NULL, `endTime` INTEGER, `state` TEXT NOT NULL, `overallEffort` INTEGER, `routineSnapshot` TEXT, PRIMARY KEY(`id`))",
    "CREATE TABLE IF NOT EXISTS `routine_exercises` (`id` TEXT NOT NULL, `routineId` TEXT NOT NULL, `exerciseId` TEXT NOT NULL, `orderIndex` INTEGER NOT NULL, `targetSets` INTEGER NOT NULL, `targetWarmups` INTEGER NOT NULL, `targetReps` INTEGER NOT NULL, `targetRestMs` INTEGER NOT NULL, `groupId` TEXT, `targetDurationMs` INTEGER, `targetDropsets` INTEGER, PRIMARY KEY(`id`), FOREIGN KEY(`routineId`) REFERENCES `routines`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`exerciseId`) REFERENCES `exercises`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
    "CREATE TABLE IF NOT EXISTS `series` (`id` TEXT NOT NULL, `sessionId` TEXT NOT NULL, `exerciseId` TEXT NOT NULL, `isWarmup` INTEGER NOT NULL, `weight` REAL, `unit` TEXT NOT NULL, `reps` INTEGER, `effort` INTEGER, `effortScale` TEXT, `isCompleted` INTEGER NOT NULL, `completedAt` INTEGER, `orderIndex` INTEGER NOT NULL, `parentId` TEXT, `groupId` TEXT, `durationMs` INTEGER, `occurrenceId` TEXT, `exerciseOrder` INTEGER, `roundIndex` INTEGER, `stageIndex` INTEGER, `targetReps` INTEGER, `targetRestMs` INTEGER, `targetDurationMs` INTEGER, `targetDropsets` INTEGER, `blockRestStartedAt` INTEGER, `notes` TEXT, PRIMARY KEY(`id`), FOREIGN KEY(`sessionId`) REFERENCES `sessions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`exerciseId`) REFERENCES `exercises`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
    "CREATE TABLE IF NOT EXISTS `goal_changes` (`id` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, `targetDays` INTEGER NOT NULL, PRIMARY KEY(`id`))",
    "CREATE INDEX IF NOT EXISTS `index_series_sessionId` ON `series` (`sessionId`)",
    "CREATE INDEX IF NOT EXISTS `index_series_exerciseId` ON `series` (`exerciseId`)",
    "CREATE INDEX IF NOT EXISTS `index_routine_exercises_routineId` ON `routine_exercises` (`routineId`)",
    "CREATE INDEX IF NOT EXISTS `index_routine_exercises_exerciseId` ON `routine_exercises` (`exerciseId`)",
    "INSERT INTO exercises VALUES ('e10','Remo con Barra','kg',NULL)",
    "INSERT INTO exercises VALUES ('c10','Cinta','kg','CARDIO')",
    "INSERT INTO routines VALUES ('r10','Full Body',1)",
    "INSERT INTO sessions VALUES ('s10','r10',10000,20000,'FINISHED',7,NULL)",
    "INSERT INTO routine_exercises VALUES ('rx10','r10','e10',0,3,1,10,120000,NULL,NULL,NULL)",
    "INSERT INTO series VALUES ('sr10','s10','e10',0,50,'kg',8,8,'RPE',1,10500,0,NULL,NULL,NULL,'e10',0,0,0,8,120000,NULL,NULL,NULL,NULL)"
)

/** Base v9 construida con SQL real; Room valida su esquema después de migrar a v10. */
@RunWith(AndroidJUnit4::class)
class SchemaMigrationTest {
    private val name = "migration_9_10_test"
    private val context = ApplicationProvider.getApplicationContext<Context>()
    @After fun cleanup() { context.deleteDatabase(name) }
    @Test fun migracion_conserva_registros_y_alinea_rondas() = runBlocking {
        context.deleteDatabase(name)
        context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { old ->
            old.execSQL("CREATE TABLE exercises (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, defaultUnit TEXT NOT NULL, type TEXT)")
            old.execSQL("CREATE TABLE routines (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, assignedDay INTEGER)")
            old.execSQL("CREATE TABLE sessions (id TEXT NOT NULL PRIMARY KEY, routineId TEXT, startTime INTEGER NOT NULL, endTime INTEGER, state TEXT NOT NULL, overallEffort INTEGER, routineSnapshot TEXT)")
            old.execSQL("CREATE TABLE routine_exercises (id TEXT NOT NULL PRIMARY KEY, routineId TEXT NOT NULL, exerciseId TEXT NOT NULL, orderIndex INTEGER NOT NULL, targetSets INTEGER NOT NULL, targetWarmups INTEGER NOT NULL DEFAULT 0, targetReps INTEGER NOT NULL, targetRestMs INTEGER NOT NULL, groupId TEXT, targetDurationMs INTEGER, targetDropsets INTEGER, FOREIGN KEY(routineId) REFERENCES routines(id) ON DELETE CASCADE, FOREIGN KEY(exerciseId) REFERENCES exercises(id) ON DELETE CASCADE)")
            old.execSQL("CREATE TABLE series (id TEXT NOT NULL PRIMARY KEY, sessionId TEXT NOT NULL, exerciseId TEXT NOT NULL, isWarmup INTEGER NOT NULL, weight REAL, unit TEXT NOT NULL, reps INTEGER, effort INTEGER, effortScale TEXT, isCompleted INTEGER NOT NULL, completedAt INTEGER, orderIndex INTEGER NOT NULL, parentId TEXT, groupId TEXT, durationMs INTEGER, FOREIGN KEY(sessionId) REFERENCES sessions(id) ON DELETE CASCADE, FOREIGN KEY(exerciseId) REFERENCES exercises(id) ON DELETE CASCADE)")
            old.execSQL("CREATE TABLE goal_changes (id TEXT NOT NULL PRIMARY KEY, timestamp INTEGER NOT NULL, targetDays INTEGER NOT NULL)")
            listOf("routine_exercises" to "routineId", "routine_exercises" to "exerciseId", "series" to "sessionId", "series" to "exerciseId").forEach { (table, column) -> old.execSQL("CREATE INDEX index_$table" + "_$column ON $table($column)") }
            old.execSQL("INSERT INTO exercises VALUES ('e','E','kg',NULL)")
            old.execSQL("INSERT INTO routines VALUES ('r','R',1)")
            old.execSQL("INSERT INTO routine_exercises VALUES ('re','r','e',0,2,1,10,120000,'g',NULL,1)")
            old.execSQL("INSERT INTO sessions VALUES ('s','r',1000,2000,'FINISHED',7,NULL)")
            old.execSQL("INSERT INTO series VALUES ('w','s','e',1,20,'kg',10,NULL,NULL,1,1100,0,NULL,'g',NULL)")
            old.execSQL("INSERT INTO series VALUES ('p','s','e',0,50,'kg',10,NULL,NULL,1,1200,1,NULL,'g',NULL)")
            old.execSQL("INSERT INTO series VALUES ('d','s','e',0,30,'kg',8,NULL,NULL,1,1300,2,'p',NULL,NULL)")
            old.version = 9
        }
        val migrated = Room.databaseBuilder(context, AppDatabase::class.java, name).addMigrations(MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12).build()
        try {
            val dao = migrated.gymDao() // La primera consulta abre y valida el esquema completo.
            assertEquals(3, dao.getAllSeriesSync().size)
            val parent = dao.getSeriesById("p")!!
            val drop = dao.getSeriesById("d")!!
            assertEquals(50.0, parent.weight!!, 0.0)
            assertEquals(0, parent.roundIndex); assertEquals(0, drop.roundIndex)
            assertEquals("g", drop.groupId); assertEquals(120000L, parent.targetRestMs)
            assertEquals(1, drop.stageIndex)
            assertEquals("FINISHED", dao.getSessionById("s")!!.state)
        } finally { migrated.close() }
    }

    /** Base v10 construida con SQL real; Room valida su esquema después de migrar a v11. */
    @Test fun migracion_10_11_marca_legacy_y_conserva_el_historial() = runBlocking {
        val dbName = "migration_10_11_test"
        context.deleteDatabase(dbName)
        context.openOrCreateDatabase(dbName, Context.MODE_PRIVATE, null).use { old ->
            V10_STATEMENTS.forEach { old.execSQL(it) }
            old.version = 10
        }
        val migrated = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(MIGRATION_10_11, MIGRATION_11_12).build()
        try {
            val dao = migrated.gymDao() // La primera consulta abre y valida el esquema completo.
            val rows = dao.getAllExercisesSync().associateBy { it.id }
            assertEquals(2, rows.size)
            assertEquals("LEGACY", rows.getValue("e10").origin)
            assertEquals("LEGACY", rows.getValue("c10").origin)
            assertNull(rows.getValue("e10").archivedAt)
            assertNull(dao.getRoutineById("r10")!!.archivedAt)
            // Las filas existentes siguen activas hasta que se archiven a propósito.
            assertEquals(2, dao.observeActiveExercises().first().size)
            assertTrue(dao.observeActiveRoutines().first().any { it.id == "r10" })
            // El cardio no está en ninguna rutina: LEGACY sí es archivable.
            dao.archiveExercise("c10", 999L)
            assertEquals(1, dao.observeActiveExercises().first().size)
            assertEquals(999L, dao.getExerciseById("c10")!!.archivedAt)
            // El histórico completo sigue disponible para el respaldo.
            assertEquals(2, dao.getAllExercisesSync().size)
            assertEquals("FINISHED", dao.getSessionById("s10")!!.state)
        } finally { migrated.close(); context.deleteDatabase(dbName) }
    }
}
