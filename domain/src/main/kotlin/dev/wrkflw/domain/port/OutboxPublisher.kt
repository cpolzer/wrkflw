package dev.wrkflw.domain.port

import dev.wrkflw.domain.event.OutboxEvent

fun interface OutboxPublisher {
    suspend fun publish(event: OutboxEvent)
}
