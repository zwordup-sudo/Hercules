package com.rafa.hercules

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rafa.hercules.data.AppDatabase
import com.rafa.hercules.data.AppearanceMode
import com.rafa.hercules.data.appearancePreferences
import com.rafa.hercules.ui.HistoryScreen
import com.rafa.hercules.ui.HistoryViewModel
import com.rafa.hercules.ui.HistoryViewModelFactory
import com.rafa.hercules.ui.RoutineScreen
import com.rafa.hercules.ui.RoutineViewModel
import com.rafa.hercules.ui.RoutineViewModelFactory
import com.rafa.hercules.ui.SessionScreen
import com.rafa.hercules.ui.SettingsScreen
import com.rafa.hercules.ui.theme.HerculesTheme
import com.rafa.hercules.utils.TimerManager
import com.rafa.hercules.viewmodel.SessionViewModel
import com.rafa.hercules.viewmodel.SessionViewModelFactory
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var edgePanel: com.rafa.hercules.ui.RestEdgePanel? = null
    private var workoutModel: SessionViewModel? = null

    override fun onStart() {
        super.onStart()
        edgePanel?.hide()
    }

    override fun onStop() {
        super.onStop()
        if (!isFinishing && !isChangingConfigurations) {
            workoutModel?.activeSession?.value?.takeIf { it.state == "ACTIVE" }?.let { edgePanel?.show(it.id) }
        }
    }

    override fun onDestroy() {
        edgePanel?.hide()
        super.onDestroy()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val db = AppDatabase.getDatabase(this)
        val timerManager = TimerManager(this)
        val sessionViewModel = ViewModelProvider(
            this, SessionViewModelFactory(db.gymDao(), timerManager)
        )[SessionViewModel::class.java]
        workoutModel = sessionViewModel
        edgePanel = com.rafa.hercules.ui.RestEdgePanel(applicationContext, timerManager,
            openWorkout = {
                startActivity(android.content.Intent(this, MainActivity::class.java)
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
            },
            skipRest = sessionViewModel::skipRest)
        val routineViewModel = ViewModelProvider(
            this, RoutineViewModelFactory(db.gymDao())
        )[RoutineViewModel::class.java]
        val historyViewModel = ViewModelProvider(
            this, HistoryViewModelFactory(db.gymDao())
        )[HistoryViewModel::class.java]

        setContent {
            val context = LocalContext.current
            val appearance = remember { context.appearancePreferences }
            val mode by appearance.mode.collectAsStateWithLifecycle(initialValue = null)
            val systemDark = isSystemInDarkTheme()
            val scope = rememberCoroutineScope()

            if (mode == null) {
                // Carga inicial: superficie vacía con el modo del sistema; el contenido
                // solo aparece cuando se conoce la preferencia guardada (sin parpadeo del tema).
                HerculesTheme(darkTheme = systemDark) {
                    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {}
                }
            } else {
                val darkTheme = when (mode) {
                    AppearanceMode.DARK -> true
                    AppearanceMode.LIGHT -> false
                    else -> systemDark
                }
                HerculesTheme(darkTheme = darkTheme) {
                    SystemBarAppearance(darkTheme)
                    AppContent(
                        sessionViewModel = sessionViewModel,
                        routineViewModel = routineViewModel,
                        historyViewModel = historyViewModel,
                        appearance = mode!!,
                        onAppearanceChange = { newMode -> scope.launch { appearance.setMode(newMode) } }
                    )
                }
            }
        }
    }
}

@Composable
private fun SystemBarAppearance(darkTheme: Boolean) {
    val view = LocalView.current
    val background = MaterialTheme.colorScheme.background.toArgb()
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            @Suppress("DEPRECATION")
            window.statusBarColor = background
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
        }
    }
}

@Composable
private fun AppContent(
    sessionViewModel: SessionViewModel,
    routineViewModel: RoutineViewModel,
    historyViewModel: HistoryViewModel,
    appearance: AppearanceMode,
    onAppearanceChange: (AppearanceMode) -> Unit
) {
    var currentScreen by rememberSaveable { mutableStateOf("routines") }
    var selectedSessionId by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(currentScreen) {
        if (currentScreen == "session") sessionViewModel.resumeSession(selectedSessionId)
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        if (currentScreen == "session") {
            SessionScreen(viewModel = sessionViewModel, onClose = { currentScreen = "routines" })
        } else {
            Scaffold(
                bottomBar = {
                    NavigationBar {
                        NavigationBarItem(
                            icon = { Icon(Icons.Default.Home, contentDescription = null) },
                            label = { Text("Inicio") },
                            selected = currentScreen == "routines",
                            onClick = { currentScreen = "routines" }
                        )
                        NavigationBarItem(
                            icon = { Icon(Icons.Default.DateRange, contentDescription = null) },
                            label = { Text("Progreso") },
                            selected = currentScreen == "history",
                            onClick = { currentScreen = "history" }
                        )
                        NavigationBarItem(
                            icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                            label = { Text("Ajustes") },
                            selected = currentScreen == "settings",
                            onClick = { currentScreen = "settings" }
                        )
                    }
                }
            ) { innerPadding ->
                Box(modifier = Modifier.padding(innerPadding)) {
                    when (currentScreen) {
                        "history" -> HistoryScreen(
                            viewModel = historyViewModel,
                            onSessionClick = { sessionId ->
                                selectedSessionId = sessionId
                                currentScreen = "session"
                            }
                        )
                        "settings" -> SettingsScreen(
                            appearance = appearance,
                            onAppearanceChange = onAppearanceChange,
                            backupViewModel = historyViewModel,
                            onImported = { sessionViewModel.refreshAfterImport() }
                        )
                        else -> RoutineScreen(
                            viewModel = routineViewModel,
                            onStartSession = { routineId ->
                                selectedSessionId = null
                                sessionViewModel.startSession(routineId) { currentScreen = "session" }
                            },
                            onResumeSession = {
                                selectedSessionId = null
                                currentScreen = "session"
                            }
                        )
                    }
                }
            }
        }
    }
}
