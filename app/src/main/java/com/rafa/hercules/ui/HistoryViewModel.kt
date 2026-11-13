package com.rafa.hercules.ui

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rafa.hercules.data.*
import com.rafa.hercules.utils.TimerManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.Clock
import java.time.LocalDate

class HistoryViewModel(private val dao: GymDao, private val clock: Clock = Clock.systemDefaultZone()) : ViewModel() {
    val finishedSessions = dao.getFinishedSessionsWithSeries().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val goalChanges = dao.getGoalChanges().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val exercises = dao.observeExercises().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val routines = dao.getAllRoutines().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private val today = MutableStateFlow(LocalDate.now(clock))
    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    val streakInfo = combine(finishedSessions, goalChanges, today) { sessions, goals, date ->
        calculateStreak(sessions, goals, date, clock.zone, clock.millis())
    }.stateIn(viewModelScope, SharingStarted.Eagerly, StreakInfo())
    fun refreshToday() { today.value = LocalDate.now(clock) }
    fun updateGoal(goal: Int) {
        val generation = LocalMutations.generation
        viewModelScope.launch { LocalMutations.run(generation) { require(goal in 1..7); dao.insertGoalChange(GoalChange(targetDays = goal, timestamp = clock.millis())) } }
    }
    fun restore(context: Context, uri: Uri, onImported: () -> Unit) = backupOperation {
        val app = context.applicationContext
        withContext(Dispatchers.IO) {
            val text = app.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                ?: error("No se pudo abrir el respaldo.")
            BackupManager(AppDatabase.getDatabase(app)).importData(text)
            TimerManager(app).cancelRestAlarm()
        }
        onImported()
        "Datos importados con éxito"
    }
    fun export(context: Context, uri: Uri) = backupOperation {
        val app = context.applicationContext
        withContext(Dispatchers.IO) {
            val text = BackupManager(AppDatabase.getDatabase(app)).exportData()
            val stream = app.contentResolver.openOutputStream(uri) ?: error("No se pudo crear el archivo de respaldo.")
            stream.bufferedWriter().use { it.write(text) }
        }
        "Respaldo exportado con éxito"
    }
    private fun backupOperation(action: suspend () -> String) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            try { _message.value = action() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { _message.value = e.message ?: "No se pudo procesar el respaldo." }
            finally { _busy.value = false }
        }
    }
}

class HistoryViewModelFactory(private val dao: GymDao) : androidx.lifecycle.ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(HistoryViewModel::class.java))
        @Suppress("UNCHECKED_CAST") return HistoryViewModel(dao) as T
    }
}
