package dev.wrkflw.application.command

import dev.wrkflw.domain.audit.AuditEntry
import dev.wrkflw.domain.audit.AuditEventType
import dev.wrkflw.domain.identity.TaskId
import dev.wrkflw.domain.port.ActorContext
import dev.wrkflw.domain.port.AuditLog
import dev.wrkflw.domain.port.Clock
import dev.wrkflw.domain.port.TaskRepository
import dev.wrkflw.domain.task.Task
import dev.wrkflw.domain.task.TaskStatus

data class ReleaseTaskCommand(
    val taskId: TaskId,
    val actor: ActorContext,
)

sealed class ReleaseTaskResult {
    data class Success(val task: Task) : ReleaseTaskResult()
    data object NotFound : ReleaseTaskResult()
    data object Forbidden : ReleaseTaskResult()
    data object Conflict : ReleaseTaskResult()
}

fun interface ReleaseTaskUseCase {
    suspend fun execute(command: ReleaseTaskCommand): ReleaseTaskResult
}

class ReleaseTaskService(
    private val tasks: TaskRepository,
    private val auditLog: AuditLog,
    private val clock: Clock,
) : ReleaseTaskUseCase {

    override suspend fun execute(command: ReleaseTaskCommand): ReleaseTaskResult {
        val task = tasks.findById(command.taskId) ?: return ReleaseTaskResult.NotFound

        if (task.status != TaskStatus.CLAIMED) return ReleaseTaskResult.Conflict

        if (task.ownerId != command.actor.actorId) return ReleaseTaskResult.Forbidden

        val now = clock.now()
        val releasedTask = task.release(command.actor.actorId, now)

        if (tasks.updateConditional(releasedTask, TaskStatus.CLAIMED, task.version) == 0)
            return ReleaseTaskResult.Conflict

        auditLog.append(
            AuditEntry(
                flowInstanceId = task.flowInstanceId,
                taskId = task.id,
                type = AuditEventType.TASK_RELEASED,
                actorId = command.actor.actorId,
                occurredAt = now,
            )
        )

        return ReleaseTaskResult.Success(releasedTask)
    }
}
