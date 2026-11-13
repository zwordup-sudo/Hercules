package com.rafa.hercules.viewmodel

import com.rafa.hercules.data.FakeGymDao
import com.rafa.hercules.data.GoalChange
import com.rafa.hercules.data.Session
import com.rafa.hercules.data.Series
import com.rafa.hercules.ui.HistoryViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.DayOfWeek
import java.time.temporal.TemporalAdjusters

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var dao: FakeGymDao
    private lateinit var viewModel: HistoryViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        dao = FakeGymDao()
        // viewModel se inicializará después de poblar en la prueba
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun dateToMillis(date: LocalDate): Long {
        return date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    @Test
    fun streak_calculates_correctly_with_changing_goals() = runTest {
        // Setup past dates
        val fixedToday = LocalDate.of(2026, 10, 7)
        val mondayThisWeek = fixedToday.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val mondayLastWeek = mondayThisWeek.minusWeeks(1)
        val mondayTwoWeeksAgo = mondayThisWeek.minusWeeks(2)
        val mondayThreeWeeksAgo = mondayThisWeek.minusWeeks(3)

        // Add goal change 3 weeks ago (goal = 2)
        dao.insertGoalChange(GoalChange(timestamp = dateToMillis(mondayThreeWeeksAgo), targetDays = 2))
        
        // Add goal change 1 week ago (goal = 4), well AFTER week -2 ends
        dao.insertGoalChange(GoalChange(timestamp = dateToMillis(mondayLastWeek.plusDays(1)), targetDays = 4))

        // Create sessions
        // Week -3: 2 sessions (meets goal of 2) -> Streak = 1
        val s1 = Session(id = "s1", routineId = null, startTime = dateToMillis(mondayThreeWeeksAgo), state = "FINISHED")
        val s2 = Session(id = "s2", routineId = null, startTime = dateToMillis(mondayThreeWeeksAgo.plusDays(1)), state = "FINISHED")
        dao.sessions.addAll(listOf(s1, s2))
        dao.series.addAll(listOf(
            Series(sessionId = "s1", exerciseId = "e1", isCompleted = true),
            Series(sessionId = "s2", exerciseId = "e1", isCompleted = true)
        ))

        // Week -2: 3 sessions (meets goal of 2) -> Streak = 2
        val s3 = Session(id = "s3", routineId = null, startTime = dateToMillis(mondayTwoWeeksAgo), state = "FINISHED")
        val s4 = Session(id = "s4", routineId = null, startTime = dateToMillis(mondayTwoWeeksAgo.plusDays(1)), state = "FINISHED")
        val s5 = Session(id = "s5", routineId = null, startTime = dateToMillis(mondayTwoWeeksAgo.plusDays(2)), state = "FINISHED")
        dao.sessions.addAll(listOf(s3, s4, s5))
        dao.series.addAll(listOf(
            Series(sessionId = "s3", exerciseId = "e1", isCompleted = true),
            Series(sessionId = "s4", exerciseId = "e1", isCompleted = true),
            Series(sessionId = "s5", exerciseId = "e1", isCompleted = true)
        ))

        // Week -1: 3 sessions (fails goal of 4) -> Streak broken
        val s6 = Session(id = "s6", routineId = null, startTime = dateToMillis(mondayLastWeek), state = "FINISHED")
        val s7 = Session(id = "s7", routineId = null, startTime = dateToMillis(mondayLastWeek.plusDays(1)), state = "FINISHED")
        val s8 = Session(id = "s8", routineId = null, startTime = dateToMillis(mondayLastWeek.plusDays(2)), state = "FINISHED")
        dao.sessions.addAll(listOf(s6, s7, s8))
        dao.series.addAll(listOf(
            Series(sessionId = "s6", exerciseId = "e1", isCompleted = true),
            Series(sessionId = "s7", exerciseId = "e1", isCompleted = true),
            Series(sessionId = "s8", exerciseId = "e1", isCompleted = true)
        ))

        // Create viewmodel AFTER populating dao
        viewModel = HistoryViewModel(dao, java.time.Clock.fixed(dateToMillis(fixedToday).let(java.time.Instant::ofEpochMilli), ZoneId.systemDefault()))
        testDispatcher.scheduler.advanceUntilIdle()

        val streak = viewModel.streakInfo.value
        println("Sessions in dao: ${dao.sessions.size}, Goal changes: ${dao.goalChanges.size}")
        println("Best streak is ${streak.bestStreak}, current is ${streak.currentStreak}")
        assertEquals(2, streak.bestStreak)
        assertEquals(0, streak.currentStreak)
    }
}
