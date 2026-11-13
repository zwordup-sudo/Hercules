package com.rafa.hercules.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ExerciseCatalogTest {

    private fun exercise(id: String, name: String, type: String? = null) =
        Exercise(id = id, name = name, type = type)

    private val catalog = listOf(
        exercise("1", "Press banca"),
        exercise("2", "Elevación lateral"),
        exercise("3", "Correr"),
        exercise("4", "sentadilla"),
        exercise("5", "Bici estática", "CARDIO")
    )

    @Test
    fun `sin busqueda devuelve todo ordenado alfabeticamente`() {
        val result = filterExercises(catalog, query = "")
        assertEquals(listOf("Bici estática", "Correr", "Elevación lateral", "Press banca", "sentadilla"), result.map { it.name })
    }

    @Test
    fun `busca sin acentos y sin distinguir mayusculas`() {
        // La búsqueda sin tilde debe encontrar nombres con tilde y viceversa.
        assertEquals(listOf("Elevación lateral"), filterExercises(catalog, query = "elevacion").map { it.name })
        assertEquals(listOf("Elevación lateral"), filterExercises(catalog, query = "ELEVACIÓN").map { it.name })
        assertEquals(listOf("sentadilla"), filterExercises(catalog, query = "SENT").map { it.name })
    }

    @Test
    fun `el filtro de fuerza excluye el cardio`() {
        val names = filterExercises(catalog, query = "", filter = ExerciseFilter.STRENGTH).map { it.name }
        assertEquals(false, names.contains("Bici estática"))
        assertEquals(true, names.contains("Correr"))
    }

    @Test
    fun `el filtro de cardio solo deja cardio`() {
        val result = filterExercises(catalog, query = "", filter = ExerciseFilter.CARDIO)
        assertEquals(listOf("Bici estática"), result.map { it.name })
    }

    @Test
    fun `combina busqueda y filtro`() {
        val result = filterExercises(catalog, query = "bici", filter = ExerciseFilter.CARDIO)
        assertEquals(listOf("Bici estática"), result.map { it.name })
        // El mismo texto con filtro de fuerza no debe encontrar el cardio.
        assertEquals(emptyList<String>(), filterExercises(catalog, query = "bici", filter = ExerciseFilter.STRENGTH).map { it.name })
    }
}