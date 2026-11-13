package com.rafa.hercules.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonalRecordsTest {
    private fun row(id: String, weight: Double = 80.0, reps: Int = 8, at: Long = 1) =
        Series(id = id, sessionId = "test", exerciseId = "press", weight = weight,
            reps = reps, isCompleted = true, completedAt = at)

    private fun records(history: List<Series>, vararg rows: Series) =
        personalRecordIds(history, rows.toList(), setOf("press", "variation"))

    @Test fun firstMarkIsBaselineAndOnlyStrictImprovementsAreRecords() {
        val first = row("first", 70.0, at = 1)
        val best = row("best", 80.0, at = 2)
        val lighter = row("lighter", 75.0, at = 3)
        val tied = row("tied", 80.0, at = 4)
        assertEquals(setOf("best"), records(emptyList(), tied, lighter, best, first))
    }

    @Test fun comparesEquivalentWeightsAcrossUnits() {
        val prior = row("prior", 80.0 / LB_TO_KG).copy(unit = "lb")
        assertEquals(setOf("new"), records(listOf(prior), row("same"), row("new", 81.0, at = 2)))
    }

    @Test fun keepsRepsAndExerciseVariantsSeparate() {
        assertTrue(records(listOf(row("prior", 70.0)),
            row("otherReps", reps = 9), row("otherVariant").copy(exerciseId = "variation")).isEmpty())
    }

    @Test fun ignoresWarmupsIncompleteInvalidCardioAndBothPartsOfDropset() {
        val prior = row("prior", 70.0)
        val drop = row("root")
        assertTrue(records(listOf(prior), drop, row("child").copy(parentId = drop.id),
            row("warmup").copy(isWarmup = true), row("open").copy(isCompleted = false),
            row("invalid").copy(weight = Double.NaN), row("missing").copy(weight = null),
            row("cardio").copy(exerciseId = "bike", durationMs = 60_000)).isEmpty())
    }

    @Test fun recalculatesAfterEditingOrUncheckingASet() {
        val prior = row("prior", 70.0)
        val attempt = row("attempt")
        assertEquals(setOf("attempt"), records(listOf(prior), attempt))
        assertTrue(records(listOf(prior), attempt.copy(isCompleted = false)).isEmpty())
        assertTrue(records(listOf(prior.copy(weight = 85.0)), attempt).isEmpty())
    }
}
