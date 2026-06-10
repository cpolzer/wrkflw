package dev.wrkflw.application.command

import dev.wrkflw.domain.audit.AuditEntry
import dev.wrkflw.domain.audit.AuditEventType
import dev.wrkflw.domain.flow.FlowInstance
import dev.wrkflw.domain.flow.FlowInterpreterResult
import dev.wrkflw.domain.flow.Trigger
import dev.wrkflw.domain.identity.TaskId
import dev.wrkflw.domain.port.ActorContext
import dev.wrkflw.domain.port.AuditLog
import dev.wrkflw.domain.port.Clock
import dev.wrkflw.domain.port.DecisionRepository
import dev.wrkflw.domain.port.FlowDefinitionRepository
import dev.wrkflw.domain.port.FlowInstanceRepository
import dev.wrkflw.domain.port.TaskRepository
import dev.wrkflw.domain.port.WorkflowEngine
import dev.wrkflw.domain.task.DecisionOutcome
import dev.wrkflw.domain.task.TaskStatus

data class SubmitDecisionCommand(
    val taskId: TaskId,
    val outcome: DecisionOutcome,
    val comment: String?,
    val actor: ActorContext,
)

sealed class SubmitDecisionResult {
    data class Success(val flowInstance: FlowInstance) : SubmitDecisionResult()
    data object NotFound : SubmitDecisionResult()
    data object Forbidden : SubmitDecisionResult()
    data object Conflict : SubmitDecisionResult()
}

fun interface SubmitDecisionUseCase {
    suspend fun execute(command: SubmitDecisionCommand): SubmitDecisionResult
}

class SubmitDecisionService(
    private val tasks: TaskRepository,
    private val decisions: DecisionRepository,
    private val instances: FlowInstanceRepository,
    private val definitions: FlowDefinitionRepository,
    private val auditLog: AuditLog,
    private val engine: WorkflowEngine,
    private val clock: Clock,
) : SubmitDecisionUseCase {

    override suspend fun execute(command: SubmitDecisionCommand): SubmitDecisionResult {
        val task = tasks.findById(command.taskId) ?: return SubmitDecisionResult.NotFound

        if (task.status != TaskStatus.CLAIMED) return SubmitDecisionResult.Conflict

        if (task.ownerId != command.actor.actorId) return SubmitDecisionResult.Forbidden

        val instance = instances.findById(task.flowInstanceId)
            ?: error("FlowInstance not found for task ${command.taskId.value}")
        val definition = definitions.findByKeyAndVersion(instance.definitionKey, instance.definitionVersion)
            ?: error("FlowDefinition not found: ${instance.definitionKey.value} v${instance.definitionVersion}")

        val trigger = when (command.outcome) {
            DecisionOutcome.APPROVE -> Trigger.APPROVE
            DecisionOutcome.REJECT -> Trigger.REJECT
        }
        val interpreterResult = FlowInterpreterResult.advance(instance.currentState, trigger, definition)

        val now = clock.now()
        val (completedTask, decision) = task.completeDecision(command.outcome, command.actor.actorId, command.comment, now)

        if (tasks.updateConditional(completedTask, TaskStatus.CLAIMED, task.version) == 0)
            return SubmitDecisionResult.Conflict

        decisions.save(decision)

        val updatedInstance = if (interpreterResult.isTerminal) {
            instance.complete(interpreterResult.terminalOutcome!!, now)
        } else {
            instance.advance(interpreterResult.nextState!!, now)
        }
        instances.update(updatedInstance)

        auditLog.append(
            AuditEntry(
                flowInstanceId = instance.id,
                taskId = task.id,
                type = AuditEventType.DECISION_RECORDED,
                actorId = command.actor.actorId,
                occurredAt = now,
            )
        )
        auditLog.append(
            AuditEntry(
                flowInstanceId = instance.id,
                type = AuditEventType.STATE_TRANSITIONED,
                actorId = command.actor.actorId,
                occurredAt = now,
            )
        )

        engine.signalWorkflow(
            instance.id,
            "decision",
            mapOf(
                "outcome" to command.outcome.name,
                "taskId" to task.id.value.toString(),
                "actorId" to command.actor.actorId.value,
            )
        )

        return SubmitDecisionResult.Success(updatedInstance)
    }
}
