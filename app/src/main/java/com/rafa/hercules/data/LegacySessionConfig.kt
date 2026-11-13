package com.rafa.hercules.data

/** Respaldos v1–v3 no incluyen config por serie. La fijamos una vez al importar. */
fun restoreLegacyConfig(data: BackupData): BackupData {
    val sessions = data.sessions.associateBy { it.id }
    val config = data.routineExercises.groupBy { it.routineId to it.exerciseId }
    val rows = data.series.map { row ->
        val re = config[sessions[row.sessionId]?.routineId to row.exerciseId]?.minByOrNull { it.orderIndex }
        val warmups = data.series.count { it.sessionId == row.sessionId && it.exerciseId == row.exerciseId && it.isWarmup && it.parentId == null && it.orderIndex < row.orderIndex }
        row.copy(occurrenceId = row.occurrenceId ?: row.exerciseId,
            exerciseOrder = row.exerciseOrder ?: re?.orderIndex ?: 0,
            roundIndex = row.roundIndex ?: if (row.isWarmup) row.orderIndex else (row.orderIndex - warmups).coerceAtLeast(0),
            stageIndex = row.stageIndex ?: if (row.parentId == null) 0 else data.series.filter { it.parentId == row.parentId }.indexOfFirst { it.id == row.id } + 1,
            targetReps = row.targetReps ?: re?.targetReps, targetRestMs = row.targetRestMs ?: re?.targetRestMs ?: 90000L,
            targetDurationMs = row.targetDurationMs ?: re?.targetDurationMs, targetDropsets = row.targetDropsets ?: re?.targetDropsets)
    }
    val byId = rows.associateBy { it.id }
    return data.copy(series = rows.map { row -> row.parentId?.let { id ->
        val parent = byId.getValue(id)
        row.copy(roundIndex = parent.roundIndex, groupId = parent.groupId, occurrenceId = parent.occurrenceId, exerciseOrder = parent.exerciseOrder)
    } ?: row })
}
