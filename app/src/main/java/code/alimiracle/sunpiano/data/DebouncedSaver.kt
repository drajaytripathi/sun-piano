package code.alimiracle.sunpiano.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Saves only the latest value, once changes have stopped for [delayMs], so dragging a slider
 * writes once instead of dozens of times a second. [flush] writes a pending value right away.
 * A write that has started always finishes, even if [scope] is cancelled. Call from one thread.
 */
class DebouncedSaver<T>(
    private val scope: CoroutineScope,
    private val delayMs: Long,
    private val save: suspend (T) -> Unit,
) {
    private var job: Job? = null
    private var pending: T? = null
    private var hasPending = false

    fun submit(value: T) {
        pending = value
        hasPending = true
        job?.cancel() // cancels only the wait; a write in progress is non-cancellable
        job = scope.launch {
            delay(delayMs)
            writePending()
        }
    }

    fun flush() {
        if (!hasPending) return
        job?.cancel()
        job = scope.launch { writePending() }
    }

    private suspend fun writePending() {
        if (!hasPending) return
        @Suppress("UNCHECKED_CAST")
        val value = pending as T
        hasPending = false
        withContext(NonCancellable) { save(value) }
    }
}
