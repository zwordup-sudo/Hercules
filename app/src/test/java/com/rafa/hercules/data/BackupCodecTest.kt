package com.rafa.hercules.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Pruebas de validación y round-trip del respaldo JSON (BackupCodec).
 * No tocan la base de datos: usan JSON en memoria y entidades ficticias.
 */
class BackupCodecTest {

    private fun fixtureData(): BackupData {
        val exercises = listOf(
            Exercise(id = "ex-fuerza", name = "Press de Banca", defaultUnit = "kg"),
            Exercise(id = "ex-lb", name = "Remo", defaultUnit = "lb"),
            Exercise(id = "ex-cardio", name = "Cinta", defaultUnit = "kg", type = "CARDIO")
        )
        val routines = listOf(
            Routine(id = "rt-1", name = "Full Body", assignedDay = 1),
            Routine(id = "rt-2", name = "Descanso", assignedDay = null)
        )
        val routineExercises = listOf(
            RoutineExercise(
                id = "rx-1", routineId = "rt-1", exerciseId = "ex-fuerza",
                orderIndex = 0, targetSets = 3, targetWarmups = 2, targetReps = 8,
                targetRestMs = 90000L, groupId = "grp-biserie", targetDropsets = 2
            ),
            RoutineExercise(
                id = "rx-2", routineId = "rt-1", exerciseId = "ex-cardio",
                orderIndex = 1, targetSets = 1, targetWarmups = 0, targetReps = 0,
                targetRestMs = 0L, targetDurationMs = 600000L
            )
        )
        val sessions = listOf(
            Session(id = "sn-activa", routineId = null, startTime = 1000L, endTime = null, state = "ACTIVE"),
            Session(
                id = "sn-terminada", routineId = "rt-1", startTime = 2000L, endTime = 5000L,
                state = "FINISHED", overallEffort = 8, routineSnapshot = "v1|Press de Banca:3x8@90000"
            ),
            Session(id = "sn-cancelada", routineId = "rt-2", startTime = 6000L, endTime = 6100L, state = "CANCELLED")
        )
        val series = listOf(
            Series(
                id = "sr-aprox", sessionId = "sn-terminada", exerciseId = "ex-fuerza",
                isWarmup = true, weight = 20.0, unit = "kg", reps = 12, isCompleted = true,
                completedAt = 2500L, orderIndex = 0
            ),
            Series(
                id = "sr-trabajo", sessionId = "sn-terminada", exerciseId = "ex-lb",
                isWarmup = false, weight = 40.5, unit = "lb", reps = 8, effort = 9,
                effortScale = "RIR", isCompleted = true, completedAt = 3000L, orderIndex = 1,
                groupId = "grp-biserie"
            ),
            Series(
                id = "sr-dropset", sessionId = "sn-terminada", exerciseId = "ex-lb",
                isWarmup = false, weight = 30.0, unit = "lb", reps = 10, effort = 8,
                effortScale = "RPE", isCompleted = true, completedAt = 3500L, orderIndex = 2,
                parentId = "sr-trabajo"
            ),
            Series(
                id = "sr-cardio", sessionId = "sn-activa", exerciseId = "ex-cardio",
                isWarmup = false, weight = null, unit = "kg", reps = null, effort = null,
                effortScale = null, isCompleted = false, completedAt = null, orderIndex = 0,
                durationMs = 300000L
            ),
            Series(
                id = "sr-vacia", sessionId = "sn-activa", exerciseId = "ex-fuerza",
                isWarmup = false, weight = null, unit = "kg", reps = null, effort = null,
                effortScale = null, isCompleted = false, completedAt = null, orderIndex = 1
            )
        )
        val goals = listOf(
            GoalChange(id = "default_goal", timestamp = 0L, targetDays = 3),
            GoalChange(id = "meta-2", timestamp = 1111L, targetDays = 4)
        )
        return BackupData(routines, exercises, routineExercises, sessions, series, goals)
    }

    @Test fun roundTripPreservesRepRanges() {
        val original = fixtureData().let { data -> data.copy(
            routineExercises = data.routineExercises.map { if (it.targetReps == 8) it.copy(targetRepsMax = 12) else it },
            series = data.series.map { if (it.id == "sr-trabajo") it.copy(targetReps = 8, targetRepsMax = 12) else it }
        ) }
        assertEquals(original, BackupCodec.parse(serialize(original)))
        assertInvalid(mutatedFixture {
            it.getJSONArray("routineExercises").getJSONObject(0).put("targetRepsMax", 7)
        }, "Rango de repeticiones")
    }

    private fun serialize(data: BackupData): String = BackupCodec.serialize(
        data.routines, data.exercises, data.routineExercises, data.sessions, data.series, data.goals
    )

    private fun mutatedFixture(mutator: (JSONObject) -> Unit): String {
        val root = JSONObject(serialize(fixtureData()))
        mutator(root)
        return root.toString()
    }

    private fun assertInvalid(json: String, fragment: String) {
        try {
            BackupCodec.parse(json)
            fail("El respaldo debía rechazarse y fue aceptado")
        } catch (e: BackupFormatException) {
            assertTrue(
                "El mensaje «${e.message}» no contiene «$fragment»",
                e.message.orEmpty().contains(fragment)
            )
        }
    }

    @Test
    fun roundTrip_preserva_todo_el_contenido() {
        val original = fixtureData()
        assertEquals(original, BackupCodec.parse(serialize(original)))
    }

    @Test
    fun acepta_version_1_sin_coleccion_goals() {
        val json = """
            {"version":1,
             "routines":[{"id":"r1","name":"Full Body","assignedDay":null}],
             "exercises":[{"id":"e1","name":"Press","defaultUnit":"kg","type":null}],
             "routineExercises":[{"id":"re1","routineId":"r1","exerciseId":"e1","orderIndex":0,
               "targetSets":3,"targetWarmups":1,"targetReps":10,"targetRestMs":90000,
               "groupId":null,"targetDurationMs":null,"targetDropsets":null}],
             "sessions":[{"id":"s1","routineId":"r1","startTime":1000,"endTime":2000,
               "state":"FINISHED","overallEffort":7,"routineSnapshot":null}],
             "series":[{"id":"se1","sessionId":"s1","exerciseId":"e1","isWarmup":false,
               "weight":40,"unit":"kg","reps":10,"effort":8,"effortScale":"RPE",
               "isCompleted":true,"completedAt":1500,"orderIndex":0,"parentId":null,
               "groupId":null,"durationMs":null}]}
        """.trimIndent()
        val data = BackupCodec.parse(json)
        assertEquals(1, data.routines.size)
        assertEquals(1, data.sessions.size)
        assertTrue(data.goals.isEmpty())
        assertEquals(3, data.routineExercises[0].targetSets)
        assertEquals(7, data.sessions[0].overallEffort)
        // Los campos nuevos ausentes en v1 reciben los valores compatibles.
        assertEquals("LEGACY", data.exercises[0].origin)
        assertNull(data.exercises[0].archivedAt)
        assertNull(data.routines[0].archivedAt)
    }

    @Test
    fun rechaza_version_no_soportada() {
        assertInvalid(mutatedFixture { it.put("version", 99) }, "no soportada")
    }

    @Test
    fun rechaza_version_ausente() {
        assertInvalid(mutatedFixture { it.remove("version") }, "Falta el campo «version»")
    }

    @Test
    fun rechaza_version_no_numerica() {
        assertInvalid(mutatedFixture { it.put("version", "tres") }, "no es un número")
    }

    @Test
    fun rechaza_json_truncado() {
        val full = serialize(fixtureData())
        assertInvalid(full.substring(0, full.length / 2), "JSON")
    }

    @Test
    fun rechaza_coleccion_ausente() {
        assertInvalid(mutatedFixture { it.remove("series") }, "Falta la colección «series»")
    }

    @Test
    fun rechaza_fila_que_no_es_objeto() {
        assertInvalid(
            mutatedFixture { root ->
                root.getJSONArray("series").put(0, "no-soy-una-fila")
            },
            "no es un objeto"
        )
    }

    @Test
    fun rechaza_id_duplicado() {
        assertInvalid(
            mutatedFixture { root ->
                root.getJSONArray("routines").getJSONObject(1).put("id", "rt-1")
            },
            "duplicado"
        )
    }

    @Test
    fun rechaza_id_vacio() {
        assertInvalid(
            mutatedFixture { root ->
                root.getJSONArray("exercises").getJSONObject(0).put("id", "  ")
            },
            "no puede estar vacío"
        )
    }

    @Test
    fun rechaza_rutina_inexistente_en_routineExercises() {
        assertInvalid(
            mutatedFixture { root ->
                root.getJSONArray("routineExercises").getJSONObject(0).put("routineId", "borrada")
            },
            "rutina inexistente"
        )
    }

    @Test
    fun rechaza_ejercicio_inexistente_en_series() {
        assertInvalid(
            mutatedFixture { root ->
                root.getJSONArray("series").getJSONObject(0).put("exerciseId", "borrado")
            },
            "ejercicio inexistente"
        )
    }

    @Test
    fun rechaza_sesion_inexistente_en_series() {
        assertInvalid(
            mutatedFixture { root ->
                root.getJSONArray("series").getJSONObject(0).put("sessionId", "borrada")
            },
            "sesión inexistente"
        )
    }

    @Test
    fun rechaza_serie_padre_de_dropset_inexistente() {
        assertInvalid(
            mutatedFixture { root ->
                // sr-dropset es hijo de sr-trabajo
                root.getJSONArray("series").getJSONObject(2).put("parentId", "no-existe")
            },
            "serie principal inexistente"
        )
    }

    @Test
    fun acepta_sesion_con_rutina_ausente() {
        // La app permite borrar rutinas y conserva sus sesiones sin clave foránea.
        val data = BackupCodec.parse(mutatedFixture { root ->
            root.getJSONArray("sessions").getJSONObject(0).put("routineId", "rutina-borrada")
        })
        assertEquals("rutina-borrada", data.sessions[0].routineId)
    }

    @Test
    fun rechaza_estado_de_sesion_invalido() {
        assertInvalid(
            mutatedFixture { root ->
                root.getJSONArray("sessions").getJSONObject(0).put("state", "EXPLOTA")
            },
            "no reconocido"
        )
    }

    @Test
    fun rechaza_unidad_invalida_en_serie() {
        assertInvalid(
            mutatedFixture { root ->
                root.getJSONArray("series").getJSONObject(0).put("unit", "grams")
            },
            "«unit» no reconocida"
        )
    }

    @Test
    fun rechaza_unidad_invalida_en_ejercicio() {
        assertInvalid(
            mutatedFixture { root ->
                root.getJSONArray("exercises").getJSONObject(0).put("defaultUnit", "stones")
            },
            "«defaultUnit» no reconocida"
        )
    }

    @Test
    fun rechaza_escala_de_esfuerzo_invalida() {
        assertInvalid(
            mutatedFixture { root ->
                root.getJSONArray("series").getJSONObject(1).put("effortScale", "RPEX")
            },
            "«effortScale» no reconocida"
        )
    }

    @Test
    fun rechaza_peso_negativo() {
        assertInvalid(
            mutatedFixture { root ->
                root.getJSONArray("series").getJSONObject(0).put("weight", -5.0)
            },
            "«weight» inválido"
        )
    }

    @Test
    fun rechaza_repeticiones_negativas() {
        assertInvalid(
            mutatedFixture { root ->
                root.getJSONArray("series").getJSONObject(0).put("reps", -1)
            },
            "«reps» no puede ser negativo"
        )
    }

    @Test
    fun rechaza_overallEffort_fuera_de_rango() {
        assertInvalid(
            mutatedFixture { root ->
                root.getJSONArray("sessions").getJSONObject(1).put("overallEffort", 11)
            },
            "«overallEffort» fuera de rango"
        )
    }

    @Test
    fun rechaza_assignedDay_fuera_de_rango() {
        assertInvalid(
            mutatedFixture { root ->
                root.getJSONArray("routines").getJSONObject(0).put("assignedDay", 8)
            },
            "«assignedDay» fuera de rango"
        )
    }

    @Test
    fun rechaza_targetDays_fuera_de_rango() {
        assertInvalid(
            mutatedFixture { root ->
                root.getJSONArray("goals").getJSONObject(1).put("targetDays", 9)
            },
            "«targetDays» fuera de rango"
        )
    }

    @Test
    fun acepta_effort_sin_cota_superior() {
        // La interfaz escribe cualquier entero >= 0 en effort; el respaldo no debe
        // rechazar un archivo que la propia app pudo generar.
        val data = BackupCodec.parse(mutatedFixture { root ->
            root.getJSONArray("series").getJSONObject(1).put("effort", 99)
        })
        assertEquals(99, data.series[1].effort)
    }

    @Test
    fun serialize_emite_version_6_con_origen_y_archivado() {
        val root = JSONObject(serialize(fixtureData()))
        assertEquals(6, root.getInt("version"))
        assertEquals(5, root.getJSONArray("series").length())
        assertEquals(3, root.getJSONArray("sessions").length())
        assertEquals(2, root.getJSONArray("goals").length())
        val ex = root.getJSONArray("exercises").getJSONObject(0)
        assertEquals("LEGACY", ex.getString("origin"))
        assertTrue(ex.isNull("archivedAt"))
        val rt = root.getJSONArray("routines").getJSONObject(0)
        assertTrue(rt.isNull("archivedAt"))
    }

    @Test
    fun roundTrip_preserva_origen_y_archivado() {
        val original = fixtureData().let { data ->
            data.copy(
                exercises = listOf(
                    data.exercises[0].copy(origin = "USER", archivedAt = 111L),
                    data.exercises[1].copy(origin = "SEED"),
                    data.exercises[2].copy(origin = "LEGACY", archivedAt = 222L)
                ),
                routines = listOf(
                    data.routines[0].copy(archivedAt = 333L),
                    data.routines[1]
                )
            )
        }
        assertEquals(original, BackupCodec.parse(serialize(original)))
    }

    @Test
    fun version_4_sin_campos_nuevos_recae_en_legacy_y_null() {
        // Un respaldo generado por la v1.13 no trae origin/archivedAt.
        val root = JSONObject(serialize(fixtureData()))
        root.put("version", 4)
        root.getJSONArray("exercises").forEach { row ->
            (row as JSONObject).remove("origin"); row.remove("archivedAt")
        }
        root.getJSONArray("routines").forEach { row -> (row as JSONObject).remove("archivedAt") }
        val data = BackupCodec.parse(root.toString())
        assertTrue(data.exercises.all { it.origin == "LEGACY" && it.archivedAt == null })
        assertTrue(data.routines.all { it.archivedAt == null })
    }

    @Test
    fun rechaza_origin_de_ejercicio_invalida() {
        assertInvalid(
            mutatedFixture { root ->
                root.getJSONArray("exercises").getJSONObject(0).put("origin", "OLD")
            },
            "de ejercicio no reconocida"
        )
    }

    @Test
    fun rechaza_archivedAt_negativo() {
        assertInvalid(
            mutatedFixture { root ->
                root.getJSONArray("exercises").getJSONObject(0).put("archivedAt", -1)
            },
            "«archivedAt» no puede ser negativo"
        )
        assertInvalid(
            mutatedFixture { root ->
                root.getJSONArray("routines").getJSONObject(0).put("archivedAt", -5L)
            },
            "«archivedAt» no puede ser negativo"
        )
    }
    @Test fun rechaza_auto_referencia() { assertInvalid(mutatedFixture { it.getJSONArray("series").getJSONObject(2).put("parentId", "sr-dropset") }, "Jerarquía") }
    @Test fun rechaza_padre_de_otra_sesion() { assertInvalid(mutatedFixture { it.getJSONArray("series").getJSONObject(2).put("parentId", "sr-cardio") }, "Jerarquía") }
    @Test fun rechaza_padre_de_otro_ejercicio() { assertInvalid(mutatedFixture { it.getJSONArray("series").getJSONObject(2).put("parentId", "sr-aprox") }, "Jerarquía") }
    @Test fun rechaza_nietos_y_ciclos() { assertInvalid(mutatedFixture { it.getJSONArray("series").getJSONObject(1).put("parentId", "sr-dropset") }, "Jerarquía") }
    @Test fun rechaza_version_fraccionaria() { assertInvalid(mutatedFixture { it.put("version", 1.5) }, "entero") }
    @Test fun rechaza_dos_sesiones_activas() { assertInvalid(mutatedFixture { it.getJSONArray("sessions").getJSONObject(1).put("state", "ACTIVE") }, "sesión activa") }
    @Test fun esquema_4_preserva_config_y_notas() {
        val original = fixtureData().let { data -> data.copy(series = data.series.map { it.copy(occurrenceId = "appearance-${it.exerciseId}", exerciseOrder = 2, roundIndex = 3, stageIndex = 0, targetReps = 12, targetRestMs = 120000L, targetDropsets = 2, notes = "agarre neutro") }) }
        assertEquals(original, BackupCodec.parse(serialize(original)))
    }

}
