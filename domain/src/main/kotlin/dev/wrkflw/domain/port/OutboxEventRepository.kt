package dev.wrkflw.domain.port

import dev.wrkflw.domain.event.OutboxEvent

interface OutboxEventRepository {
    suspend fun save(event: OutboxEvent)

    suspend fun findPending(limit: Int = 100): List<OutboxEvent>

    suspend fun markDispatched(id: java.util.UUID): Int
}
