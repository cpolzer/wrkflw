package dev.wrkflw.temporal.activity

import dev.wrkflw.domain.audit.AuditEntry
import dev.wrkflw.domain.audit.AuditEventType
import dev.wrkflw.domain.identity.FlowInstanceId
import dev.wrkflw.domain.port.AuditLog
import dev.wrkflw.domain.port.Clock
import dev.wrkflw.domain.port.FlowDefinitionRepository
import dev.wrkflw.domain.port.FlowInstanceRepository
import dev.wrkflw.domain.port.TaskRepository
import dev.wrkflw.domain.task.Task
import io.temporal.activity.ActivityInterface
import io.temporal.activity.ActivityMethod
import kotlinx.coroutines.runBlocking
import java.util.UUID

@ActivityInterface
interface CreateHumanTaskActivity {
    @ActivityMethod
    fun createForCurrentState(flowInstanceId: String): String
}

class CreateHumanTaskActivityImpl(
    private val definitions: FlowDefinitionRepository,
    private val instances: FlowInstanceRepository,
    private val tasks: TaskRepository,
    private val auditLog: AuditLog,
    private val clock: Clock,
) : CreateHumanTaskActivity {

    override fun createForCurrentState(flowInstanceId: String): String = runBlocking {
        val id = FlowInstanceId(UUID.fromString(flowInstanceId))

        val instance = instances.findById(id)
            ?: error("FlowInstance not found: $flowInstanceId")

        val definition = definitions.findByKeyAndVersion(instance.definitionKey, instance.definitionVersion)
            ?: error("FlowDefinition not found: ${instance.definitionKey.value} v${instance.definitionVersion}")

        val candidateGroup = definition.candidateGroup(instance.currentState)
            ?: error("No candidate group for state '${instance.currentState}' — is it a HUMAN_TASK state?")

        val now = clock.now()
        val task = Task.create(
            flowInstanceId = id,
            stateName = instance.currentState,
            candidateGroupId = candidateGroup,
            now = now,
        )

        tasks.save(task)

        auditLog.append(
            AuditEntry(
                flowInstanceId = id,
                taskId = task.id,
                type = AuditEventType.TASK_CREATED,
                occurredAt = now,
            )
        )

        task.id.value.toString()
    }
}
