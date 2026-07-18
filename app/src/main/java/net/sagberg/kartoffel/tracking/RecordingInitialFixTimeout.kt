package net.sagberg.kartoffel.tracking

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal const val INITIAL_RECORDING_FIX_TIMEOUT_MILLIS = 30_000L

internal interface RecordingInitialFixTimeout {
    fun start(onTimeout: suspend () -> Unit)

    fun cancel()
}

internal object NoOpRecordingInitialFixTimeout : RecordingInitialFixTimeout {
    override fun start(onTimeout: suspend () -> Unit) = Unit

    override fun cancel() = Unit
}

internal class CoroutineRecordingInitialFixTimeout(
    private val scope: CoroutineScope,
    private val timeoutMillis: Long = INITIAL_RECORDING_FIX_TIMEOUT_MILLIS,
) : RecordingInitialFixTimeout {
    private var job: Job? = null

    override fun start(onTimeout: suspend () -> Unit) {
        job?.cancel()
        job = scope.launch {
            delay(timeoutMillis)
            onTimeout()
        }
    }

    override fun cancel() {
        job?.cancel()
        job = null
    }
}
