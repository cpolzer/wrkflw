package dev.wrkflw.application.query

import dev.wrkflw.domain.audit.AuditEntry
import dev.wrkflw.domain.flow.FlowInstance
import dev.wrkflw.domain.identity.FlowInstanceId
import dev.wrkflw.domain.port.AuditLog
import dev.wrkflw.domain.port.FlowInstanceRepository
import dev.wrkflw.domain.port.TaskRepository
import dev.wrkflw.domain.task.Task
import dev.wrkflw.domain.task.TaskStatus

data class FlowStatusQuery(
    val flowInstanceId: FlowInstanceId,
)

sealed class FlowStatusResult {
    data class Found(
        val instance: FlowInstance,
        val pendingTasks: List<Task>,
        val history: List<AuditEntry>,
    ) : FlowStatusResult()

    data object NotFound : FlowStatusResult()
}

fun interface FlowStatusUseCase {
    suspend fun execute(query: FlowStatusQuery): FlowStatusResult
}

class FlowStatusService(
    private val instances: FlowInstanceRepository,
    private val tasks: TaskRepository,
    private val auditLog: AuditLog,
) : FlowStatusUseCase {
    override suspend fun execute(query: FlowStatusQuery): FlowStatusResult {
        val instance = instances.findById(query.flowInstanceId) ?: return FlowStatusResult.NotFound
        val pendingTasks =
            tasks
                .findByFlowInstanceId(query.flowInstanceId)
                .filter { it.status == TaskStatus.PENDING }
        val history = auditLog.findByFlowInstanceId(query.flowInstanceId).sortedBy { it.occurredAt }
        return FlowStatusResult.Found(instance, pendingTasks, history)
    }
}
