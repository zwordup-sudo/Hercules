package com.rafa.hercules.data

import org.junit.Assert.*
import org.junit.Test

class TrainingToolsTest {
    @Test fun platesArePerSideAndSubtractTheBar() {
        val load = calculatePlates(100.0, 20.0, "kg")!!
        assertEquals(100.0, load.actualWeight, 0.0)
        assertEquals(40.0, load.perSide.entries.sumOf { it.key * it.value }, 0.0)
        assertEquals(2, load.perSide.values.sum())
        assertEquals(mapOf(45.0 to 1), calculatePlates(135.0, 45.0, "lb")!!.perSide)
    }

    @Test fun roundsDownAndRejectsImpossibleInputs() {
        assertEquals(100.0, calculatePlates(101.0, 20.0, "kg")!!.actualWeight, 0.0)
        assertEquals(emptyMap<Double, Int>(), calculatePlates(20.0, 20.0, "kg")!!.perSide)
        assertNull(calculatePlates(19.0, 20.0, "kg"))
        assertNull(calculatePlates(Double.POSITIVE_INFINITY, 20.0, "kg"))
    }

    private val planned = (1..3).map {
        Series(sessionId = "current", exerciseId = "press", orderIndex = it, targetReps = 8, targetRepsMax = 12)
    }
    private val completed = planned.map { it.copy(sessionId = "prior", weight = 60.0, reps = 12, isCompleted = true) }

    @Test fun recommendsMoreLoadOnlyAfterEverySeriesHitsTheTop() {
        assertEquals(ProgressionSuggestion(60.0, 62.5, "kg"), suggestProgression(planned, completed))
        assertNull(suggestProgression(planned, completed.dropLast(1)))
        assertNull(suggestProgression(planned, completed.dropLast(1) + completed.last().copy(reps = 11)))
        assertNull(suggestProgression(planned, completed.dropLast(1) + completed.last().copy(isCompleted = false)))
    }

    @Test fun rejectsDifferentRangesMixedLoadsAndDropsets() {
        assertNull(suggestProgression(planned, completed.map { it.copy(targetRepsMax = 10) }))
        assertNull(suggestProgression(planned, completed.dropLast(1) + completed.last().copy(weight = 55.0)))
        assertNull(suggestProgression(planned, completed + completed.last().copy(parentId = completed.last().id)))
        assertNull(suggestProgression(planned.map { it.copy(targetRepsMax = null) }, completed))
    }

    @Test fun acceptsEquivalentLoadsAndSuggestsInTheCurrentUnit() {
        val prior = completed.mapIndexed { i, row ->
            if (i == 0) row.copy(weight = 60.0 / LB_TO_KG, unit = "lb") else row
        }
        val result = suggestProgression(planned.map { it.copy(unit = "lb") }, prior)!!
        assertEquals(60.0 / LB_TO_KG + 5, result.suggested, 1e-8)
    }
}
