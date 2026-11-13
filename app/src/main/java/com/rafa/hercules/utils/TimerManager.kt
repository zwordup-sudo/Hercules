package com.rafa.hercules.utils

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.media.AudioManager
import android.media.ToneGenerator
import android.util.Log
import com.rafa.hercules.data.AppDatabase
import com.rafa.hercules.data.LocalMutations
import kotlinx.coroutines.*
import java.util.UUID

private const val REST_PREFS = "rest_alarm_state"
private fun bootCount(context: Context): Int = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)

/** Consumir el sonido no borra el vencimiento que necesita el aviso visual. Llamar serializado. */
internal fun consumeRestAlarmToken(prefs: SharedPreferences, token: String): Boolean {
    if (prefs.getString("token", null) != token) return false
    return prefs.edit().remove("token").commit()
}

class RestAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            var tone: ToneGenerator? = null
            try {
                val prefs = context.getSharedPreferences(REST_PREFS, Context.MODE_PRIVATE)
                val token = intent.getStringExtra("token") ?: return@launch
                if (token != prefs.getString("token", null)) return@launch
                val sessionId = prefs.getString("sessionId", null) ?: return@launch
                val generation = LocalMutations.generation
                LocalMutations.run(generation) {
                    if (AppDatabase.getDatabase(context).gymDao().getSessionById(sessionId)?.state == "ACTIVE" && token == prefs.getString("token", null)) {
                         val generator = ToneGenerator(AudioManager.STREAM_ALARM, 100)
                         tone = generator
                         if (consumeRestAlarmToken(prefs, token)) generator.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 1000)
                    }
                }
                if (tone == null) return@launch
                delay(1500)
            } catch (e: Exception) {
                Log.e("RestAlarmReceiver", "No se pudo reproducir el aviso", e)
            } finally { tone?.release(); pending.finish() }
        }
    }
}

interface ITimerManager {
    fun setRestAlarm(durationMs: Long, requestCode: Int = 1)
    fun cancelRestAlarm(requestCode: Int = 1)
    fun setSessionRestAlarm(sessionId: String, durationMs: Long) { setRestAlarm(durationMs) }
    fun restEndTime(sessionId: String): Long? = null
    fun remainingRestMs(sessionId: String): Long? = null
    fun exactAlarmsAvailable(): Boolean = true
}

class TimerManager(context: Context, prefsName: String = REST_PREFS,
    private val elapsedRealtime: () -> Long = { SystemClock.elapsedRealtime() },
    private val wallTime: () -> Long = { System.currentTimeMillis() }) : ITimerManager {
    private val context = context.applicationContext
    private val alarmManager = this.context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val prefs = this.context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

    override fun exactAlarmsAvailable(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    override fun remainingRestMs(sessionId: String): Long? {
        if (prefs.getString("sessionId", null) != sessionId) return null
        if (prefs.getInt("boot", -2) != bootCount(context)) return null
        return (prefs.getLong("elapsedDeadline", 0L) - elapsedRealtime()).coerceAtLeast(0L)
    }

    override fun restEndTime(sessionId: String): Long? {
        if (prefs.getString("sessionId", null) != sessionId || !prefs.contains("deadline")) return null
        // Reiniciar cancela las alarmas Android; no mostramos un aviso inexistente.
        if (prefs.getInt("boot", -2) != bootCount(context)) return null
        val remaining = prefs.getLong("elapsedDeadline", 0L) - elapsedRealtime()
        // Un deadline vencido sigue identificando «Siguiente serie», incluso después del beep.
        return wallTime() + remaining
    }

    override fun setSessionRestAlarm(sessionId: String, durationMs: Long) {
        require(durationMs in 1L..86400000L) { "Descanso fuera de rango." }
        val token = UUID.randomUUID().toString()
        check(prefs.edit().putString("token", token).putString("sessionId", sessionId)
            .putLong("deadline", wallTime() + durationMs)
            .putLong("elapsedDeadline", elapsedRealtime() + durationMs)
            .putInt("boot", bootCount(context)).commit()) { "No se pudo guardar el descanso." }
        try { setRestAlarm(durationMs) } catch (e: Exception) { prefs.edit().clear().commit(); throw e }
    }

    override fun setRestAlarm(durationMs: Long, requestCode: Int) {
        val intent = Intent(context, RestAlarmReceiver::class.java).putExtra("token", prefs.getString("token", null))
        val pending = PendingIntent.getBroadcast(context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val trigger = elapsedRealtime() + durationMs
        if (exactAlarmsAvailable()) {
            try { alarmManager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, trigger, pending) }
            catch (e: SecurityException) { alarmManager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, trigger, pending) }
        } else alarmManager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, trigger, pending)
    }

    override fun cancelRestAlarm(requestCode: Int) {
        prefs.edit().clear().commit()
        val pending = PendingIntent.getBroadcast(context, requestCode, Intent(context, RestAlarmReceiver::class.java),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
        if (pending != null) alarmManager.cancel(pending)
    }
}
