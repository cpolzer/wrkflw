package dev.wrkflw.domain.event

import dev.wrkflw.domain.identity.FlowInstanceId
import java.time.Instant
import java.util.UUID

enum class OutboxStatus { PENDING, DISPATCHED }

data class OutboxEvent(
    val id: UUID = UUID.randomUUID(),
    val flowInstanceId: FlowInstanceId,
    val type: String,
    val data: String,
    val occurredAt: Instant,
    val status: OutboxStatus = OutboxStatus.PENDING,
)
