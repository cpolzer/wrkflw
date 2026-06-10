package dev.wrkflw.domain.event

import dev.wrkflw.domain.identity.ActorId
import dev.wrkflw.domain.identity.FlowInstanceId
import dev.wrkflw.domain.identity.TaskId
import dev.wrkflw.domain.task.DecisionOutcome
import java.time.Instant

sealed interface DomainEvent {
    val flowInstanceId: FlowInstanceId
    val occurredAt: Instant
}

data class FlowStarted(
    override val flowInstanceId: FlowInstanceId,
    val definitionKey: String,
    val documentRef: String,
    val submitterId: ActorId,
    val initialState: String,
    override val occurredAt: Instant,
) : DomainEvent

data class TaskCreated(
    override val flowInstanceId: FlowInstanceId,
    val taskId: TaskId,
    val stateName: String,
    val candidateGroupId: String,
    override val occurredAt: Instant,
) : DomainEvent

data class TaskClaimed(
    override val flowInstanceId: FlowInstanceId,
    val taskId: TaskId,
    val ownerId: ActorId,
    override val occurredAt: Instant,
) : DomainEvent

data class TaskReleased(
    override val flowInstanceId: FlowInstanceId,
    val taskId: TaskId,
    override val occurredAt: Instant,
) : DomainEvent

data class DecisionRecorded(
    override val flowInstanceId: FlowInstanceId,
    val taskId: TaskId,
    val outcome: DecisionOutcome,
    val actorId: ActorId,
    val comment: String?,
    override val occurredAt: Instant,
) : DomainEvent

data class FlowCompleted(
    override val flowInstanceId: FlowInstanceId,
    val terminalOutcome: String,
    override val occurredAt: Instant,
) : DomainEvent
