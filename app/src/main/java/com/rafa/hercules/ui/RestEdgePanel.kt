package com.rafa.hercules.ui

import android.app.KeyguardManager
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.rafa.hercules.utils.TimerManager
import java.util.Locale

internal const val EDGE_PREFS = "rest_edge_panel"
internal const val EDGE_ENABLED = "enabled"

/** Ventana acotada al descanso; el beep sigue a cargo del AlarmManager existente. */
internal class RestEdgePanel(
    private val context: Context,
    private val timer: TimerManager,
    private val openWorkout: () -> Unit,
    private val skipRest: () -> Unit
) {
    private val handler = Handler(Looper.getMainLooper())
    private val window = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var panel: LinearLayout? = null
    private var label: TextView? = null
    private var sessionId: String? = null
    private var expanded = false
    private val tick = Runnable { refresh() }

    fun show(id: String) {
        hide()
        sessionId = id
        refresh()
    }

    fun hide() {
        handler.removeCallbacks(tick)
        sessionId = null
        detach()
        expanded = false
    }

    private fun detach() {
        panel?.let { runCatching { window.removeViewImmediate(it) } }
        panel = null
        label = null
    }

    private fun refresh() {
        val remaining = sessionId?.let(timer::remainingRestMs) ?: 0L
        if (remaining <= 0 || !Settings.canDrawOverlays(context) ||
            !context.getSharedPreferences(EDGE_PREFS, Context.MODE_PRIVATE).getBoolean(EDGE_ENABLED, false)) {
            hide()
            return
        }
        val unlocked = !(context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).isKeyguardLocked &&
            (context.getSystemService(Context.POWER_SERVICE) as PowerManager).isInteractive
        if (!unlocked) detach() else if (panel == null && !attach()) {
            hide()
            return
        }
        val seconds = (remaining + 999) / 1000
        label?.text = String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60)
        handler.removeCallbacks(tick)
        handler.postDelayed(tick, if (unlocked) 500L else 1000L)
    }

    private fun dp(n: Int) = (n * context.resources.displayMetrics.density + 0.5f).toInt()

    private fun attach(): Boolean {
        val view = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(4), dp(8), dp(4))
            background = GradientDrawable().apply {
                setColor(Color.rgb(24, 18, 38))
                val r = dp(20).toFloat()
                cornerRadii = floatArrayOf(r, r, 0f, 0f, 0f, 0f, r, r)
            }
        }
        fun control(text: String, description: String, action: () -> Unit) = TextView(context).apply {
            this.text = text
            contentDescription = description
            textSize = 15f
            setTextColor(Color.rgb(226, 209, 255))
            gravity = Gravity.CENTER
            minHeight = dp(48)
            setOnClickListener { action() }
            view.addView(this, LinearLayout.LayoutParams(-1, -2))
        }
        val countdown = control("", "Temporizador de descanso. Toca para expandir o contraer") {
            expanded = !expanded
            detach()
            refresh()
        }
        if (expanded) {
            control("Abrir Hércules", "Abrir Hércules") { openWorkout() }
            control("Saltar", "Saltar descanso") { skipRest(); hide() }
            control("Ocultar", "Ocultar este panel hasta volver a Hércules") { hide() }
        }
        val params = WindowManager.LayoutParams(
            dp(if (expanded) 152 else 76), WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.RIGHT or Gravity.CENTER_VERTICAL }
        return runCatching {
            window.addView(view, params)
            panel = view
            label = countdown
        }.isSuccess
    }
}
