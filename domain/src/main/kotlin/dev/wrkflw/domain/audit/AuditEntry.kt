package dev.wrkflw.domain.audit

import dev.wrkflw.domain.identity.ActorId
import dev.wrkflw.domain.identity.FlowInstanceId
import dev.wrkflw.domain.identity.TaskId
import kotlinx.serialization.json.JsonObject
import java.time.Instant

enum class AuditEventType {
    FLOW_STARTED,
    TASK_CREATED,
    TASK_CLAIMED,
    TASK_RELEASED,
    DECISION_RECORDED,
    STATE_TRANSITIONED,
    FLOW_COMPLETED
}

data class AuditEntry(
    val id: Long = 0,
    val flowInstanceId: FlowInstanceId,
    val taskId: TaskId? = null,
    val type: AuditEventType,
    val actorId: ActorId? = null,
    val payload: JsonObject = JsonObject(emptyMap()),
    val occurredAt: Instant
)
