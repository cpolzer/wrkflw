package dev.wrkflw

import dev.wrkflw.domain.port.OutboxEventRepository
import dev.wrkflw.domain.port.OutboxPublisher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class OutboxPublisherRunner(
    private val outbox: OutboxEventRepository,
    private val publisher: OutboxPublisher,
    private val pollIntervalMs: Long = 1_000,
) {
    fun start(scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            while (isActive) {
                try {
                    publishPending()
                } catch (_: Exception) {
                    // swallow to keep poller alive; individual event errors are caught per-event
                }
                delay(pollIntervalMs)
            }
        }
    }

    private suspend fun publishPending() {
        val pending = outbox.findPending()
        for (event in pending) {
            runCatching { publisher.publish(event) }
            outbox.markDispatched(event.id)
        }
    }
}
