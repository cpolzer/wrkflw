package dev.wrkflw.domain.port

import dev.wrkflw.domain.event.DomainEvent

interface DomainEventPublisher {
    suspend fun publish(event: DomainEvent)
}
