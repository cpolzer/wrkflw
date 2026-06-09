package dev.wrkflw.domain.identity

import java.util.UUID

@JvmInline
value class ActorId(val value: String)

@JvmInline
value class GroupId(val value: String)

@JvmInline
value class FlowDefinitionKey(val value: String)

@JvmInline
value class FlowInstanceId(val value: UUID) {
    companion object {
        fun generate(): FlowInstanceId = FlowInstanceId(UUID.randomUUID())
    }
}

@JvmInline
value class TaskId(val value: UUID) {
    companion object {
        fun generate(): TaskId = TaskId(UUID.randomUUID())
    }
}
