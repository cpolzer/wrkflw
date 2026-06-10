package dev.wrkflw.application.command

import dev.wrkflw.domain.audit.AuditEntry
import dev.wrkflw.domain.audit.AuditEventType
import dev.wrkflw.domain.event.FlowStarted
import dev.wrkflw.domain.event.toOutboxEvent
import dev.wrkflw.domain.flow.FlowInstance
import dev.wrkflw.domain.identity.FlowDefinitionKey
import dev.wrkflw.domain.identity.FlowInstanceId
import dev.wrkflw.domain.port.ActorContext
import dev.wrkflw.domain.port.AuditLog
import dev.wrkflw.domain.port.Clock
import dev.wrkflw.domain.port.FlowDefinitionRepository
import dev.wrkflw.domain.port.FlowInstanceRepository
import dev.wrkflw.domain.port.OutboxEventRepository
import dev.wrkflw.domain.port.WorkflowEngine

data class SubmitDocumentCommand(
    val definitionKey: String,
    val documentRef: String,
    val actor: ActorContext,
)

sealed class SubmitDocumentResult {
    data class Success(
        val instance: FlowInstance,
    ) : SubmitDocumentResult()

    data object DefinitionNotFound : SubmitDocumentResult()

    data object Unauthorized : SubmitDocumentResult()
}

fun interface SubmitDocumentUseCase {
    suspend fun execute(command: SubmitDocumentCommand): SubmitDocumentResult
}

class SubmitDocumentService(
    private val definitions: FlowDefinitionRepository,
    private val instances: FlowInstanceRepository,
    private val engine: WorkflowEngine,
    private val auditLog: AuditLog,
    private val clock: Clock,
    private val outbox: OutboxEventRepository? = null,
) : SubmitDocumentUseCase {
    override suspend fun execute(command: SubmitDocumentCommand): SubmitDocumentResult {
        val definition =
            definitions.findByKey(FlowDefinitionKey(command.definitionKey))
                ?: return SubmitDocumentResult.DefinitionNotFound

        if (!command.actor.isInGroup(definition.initiatorGroupId)) {
            return SubmitDocumentResult.Unauthorized
        }

        val now = clock.now()
        val instance =
            FlowInstance.start(
                id = FlowInstanceId.generate(),
                definitionKey = definition.key,
                definitionVersion = definition.version,
                documentRef = command.documentRef,
                submitterId = command.actor.actorId,
                initialState = definition.initialState,
                now = now,
            )

        instances.save(instance)

        auditLog.append(
            AuditEntry(
                flowInstanceId = instance.id,
                type = AuditEventType.FLOW_STARTED,
                actorId = command.actor.actorId,
                occurredAt = now,
            ),
        )

        outbox?.save(
            FlowStarted(
                flowInstanceId = instance.id,
                definitionKey = definition.key.value,
                documentRef = instance.documentRef,
                submitterId = instance.submitterId,
                initialState = instance.currentState,
                occurredAt = now,
            ).toOutboxEvent(),
        )

        engine.startWorkflow(instance.id, definition.key.value)

        return SubmitDocumentResult.Success(instance)
    }
}
