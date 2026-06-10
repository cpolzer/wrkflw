package dev.wrkflw.application.command

import dev.wrkflw.domain.audit.AuditEntry
import dev.wrkflw.domain.audit.AuditEventType
import dev.wrkflw.domain.event.TaskClaimed
import dev.wrkflw.domain.event.toOutboxEvent
import dev.wrkflw.domain.identity.TaskId
import dev.wrkflw.domain.port.ActorContext
import dev.wrkflw.domain.port.AuditLog
import dev.wrkflw.domain.port.Clock
import dev.wrkflw.domain.port.OutboxEventRepository
import dev.wrkflw.domain.port.TaskRepository
import dev.wrkflw.domain.task.Task
import dev.wrkflw.domain.task.TaskStatus

data class ClaimTaskCommand(
    val taskId: TaskId,
    val actor: ActorContext,
)

sealed class ClaimTaskResult {
    data class Success(
        val task: Task,
    ) : ClaimTaskResult()

    data object NotFound : ClaimTaskResult()

    data object Forbidden : ClaimTaskResult()

    data object Conflict : ClaimTaskResult()
}

fun interface ClaimTaskUseCase {
    suspend fun execute(command: ClaimTaskCommand): ClaimTaskResult
}

class ClaimTaskService(
    private val tasks: TaskRepository,
    private val auditLog: AuditLog,
    private val clock: Clock,
    private val outbox: OutboxEventRepository? = null,
) : ClaimTaskUseCase {
    override suspend fun execute(command: ClaimTaskCommand): ClaimTaskResult {
        val task = tasks.findById(command.taskId) ?: return ClaimTaskResult.NotFound

        if (!command.actor.isInGroup(task.candidateGroupId)) return ClaimTaskResult.Forbidden

        if (task.status != TaskStatus.PENDING) return ClaimTaskResult.Conflict

        val now = clock.now()
        val claimedTask = task.claim(command.actor.actorId, now)

        if (tasks.updateConditional(claimedTask, TaskStatus.PENDING, task.version) == 0) {
            return ClaimTaskResult.Conflict
        }

        auditLog.append(
            AuditEntry(
                flowInstanceId = task.flowInstanceId,
                taskId = task.id,
                type = AuditEventType.TASK_CLAIMED,
                actorId = command.actor.actorId,
                occurredAt = now,
            ),
        )

        outbox?.save(
            TaskClaimed(
                flowInstanceId = task.flowInstanceId,
                taskId = task.id,
                ownerId = command.actor.actorId,
                occurredAt = now,
            ).toOutboxEvent(),
        )

        return ClaimTaskResult.Success(claimedTask)
    }
}
