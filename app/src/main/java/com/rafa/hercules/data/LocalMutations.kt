package com.rafa.hercules.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Una barrera común: una restauración invalida órdenes emitidas con datos antiguos. */
object LocalMutations {
    private val mutex = Mutex()
    private val _replacing = MutableStateFlow(false)
    val replacing = _replacing.asStateFlow()
    @Volatile var generation: Long = 0L
        private set

    suspend fun run(expected: Long, action: suspend () -> Unit): Boolean = mutex.withLock {
        if (expected != generation) false else { action(); true }
    }

    suspend fun replace(action: suspend () -> Unit) = mutex.withLock {
        _replacing.value = true
        generation++
        try { action() } finally { _replacing.value = false }
    }
}
