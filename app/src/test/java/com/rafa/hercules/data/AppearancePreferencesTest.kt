package com.rafa.hercules.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * Pruebas de guardar/cargar la selección de apariencia (v1.15).
 * DataStore se usa sobre un archivo temporal real en la JVM.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AppearancePreferencesTest {

    private fun newStore(file: File, scope: CoroutineScope) =
        AppearancePreferences(PreferenceDataStoreFactory.create(scope = scope) { file })

    @Test
    fun `el valor inicial es Sistema`() = runTest {
        val file = File("build/tmp-tests/apariencia-inicial.preferences_pb")
        file.parentFile?.mkdirs()
        file.delete()
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        try {
            assertEquals(AppearanceMode.SYSTEM, newStore(file, scope).mode.first())
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `valores vacíos o desconocidos caen en Sistema`() {
        assertEquals(AppearanceMode.SYSTEM, AppearanceMode.fromStorage(null))
        assertEquals(AppearanceMode.SYSTEM, AppearanceMode.fromStorage(""))
        assertEquals(AppearanceMode.SYSTEM, AppearanceMode.fromStorage("otro-valor"))
        assertEquals(AppearanceMode.LIGHT, AppearanceMode.fromStorage("light"))
        assertEquals(AppearanceMode.DARK, AppearanceMode.fromStorage("dark"))
    }

    @Test
    fun `la selección se conserva al reabrir la app`() = runTest {
        val file = File("build/tmp-tests/apariencia-reabrir.preferences_pb")
        file.parentFile?.mkdirs()
        file.delete()
        val firstScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        newStore(file, firstScope).setMode(AppearanceMode.DARK)
        firstScope.cancel()
        testScheduler.advanceUntilIdle()
        val secondScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        try {
            assertEquals(AppearanceMode.DARK, newStore(file, secondScope).mode.first())
        } finally {
            secondScope.cancel()
        }
    }

    @Test
    fun `guardar y cargar un ciclo Claro y Sistema`() = runTest {
        val file = File("build/tmp-tests/apariencia-ciclo.preferences_pb")
        file.parentFile?.mkdirs()
        file.delete()
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        try {
            val store = newStore(file, scope)
            store.setMode(AppearanceMode.LIGHT)
            assertEquals(AppearanceMode.LIGHT, store.mode.first())
            store.setMode(AppearanceMode.SYSTEM)
            assertEquals(AppearanceMode.SYSTEM, store.mode.first())
        } finally {
            scope.cancel()
        }
    }
}
