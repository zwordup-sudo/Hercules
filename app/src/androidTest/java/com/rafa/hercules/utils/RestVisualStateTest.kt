package com.rafa.hercules.utils

import android.content.Context
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** No programa alarmas ni emite sonido; prueba preferencias reales con reloj controlado. */
@RunWith(AndroidJUnit4::class)
class RestVisualStateTest {
    @Test fun consumir_sonido_conserva_vencimiento_reabrir_no_repite_y_saltar_limpia() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "test_rest_visual"
        val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        var elapsed = 100L
        try {
            prefs.edit().clear().putString("sessionId", "s").putString("token", "token")
                .putLong("deadline", 1000).putLong("elapsedDeadline", 200)
                .putInt("boot", Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)).commit()
            val manager = TimerManager(context, name, { elapsed }, { 1000L })
            assertEquals(100L, manager.remainingRestMs("s"))
            elapsed = 250L
            assertEquals(0L, manager.remainingRestMs("s"))
            assertTrue(consumeRestAlarmToken(prefs, "token"))
            assertFalse(consumeRestAlarmToken(prefs, "token"))
            assertEquals("s", prefs.getString("sessionId", null))
            val reopened = TimerManager(context, name, { elapsed }, { 1000L })
            assertEquals(950L, reopened.restEndTime("s"))
            assertEquals(0L, reopened.remainingRestMs("s"))
            assertNull(reopened.restEndTime("other"))
            reopened.cancelRestAlarm(987654) // RequestCode de prueba, sin tocar alarmas del producto.
            assertNull(reopened.restEndTime("s"))
        } finally { prefs.edit().clear().commit() }
    }

    @Test fun token_antiguo_y_reinicio_no_generan_listo_ni_consumen_alarma_nueva() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("test_rest_boot", Context.MODE_PRIVATE)
        try {
            prefs.edit().clear().putString("sessionId", "s").putString("token", "new")
                .putLong("deadline", 1000).putLong("elapsedDeadline", 200).putInt("boot", -999).commit()
            assertFalse(consumeRestAlarmToken(prefs, "old"))
            val manager = TimerManager(context, "test_rest_boot", { 300L }, { 1000L })
            assertNull(manager.restEndTime("s")); assertNull(manager.remainingRestMs("s"))
            assertEquals("new", prefs.getString("token", null))
        } finally { prefs.edit().clear().commit() }
    }
}
