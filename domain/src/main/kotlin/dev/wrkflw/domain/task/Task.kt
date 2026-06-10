package dev.wrkflw.domain.task

import dev.wrkflw.domain.identity.ActorId
import dev.wrkflw.domain.identity.GroupId
import dev.wrkflw.domain.identity.TaskId
import java.time.Instant

enum class TaskStatus {
    PENDING,
    CLAIMED,
    COMPLETED,
}

enum class DecisionOutcome {
    APPROVE,
    REJECT,
}

data class Decision(
    val taskId: TaskId,
    val outcome: DecisionOutcome,
    val actorId: ActorId,
    val comment: String?,
    val decidedAt: Instant,
)

data class Task(
    val id: TaskId,
    val flowInstanceId: dev.wrkflw.domain.identity.FlowInstanceId,
    val stateName: String,
    val candidateGroupId: GroupId,
    val status: TaskStatus,
    val ownerId: ActorId? = null,
    val version: Int = 0,
    val createdAt: Instant,
    val claimedAt: Instant? = null,
    val completedAt: Instant? = null,
) {
    companion object {
        fun create(
            flowInstanceId: dev.wrkflw.domain.identity.FlowInstanceId,
            stateName: String,
            candidateGroupId: GroupId,
            now: Instant,
        ): Task =
            Task(
                id = TaskId.generate(),
                flowInstanceId = flowInstanceId,
                stateName = stateName,
                candidateGroupId = candidateGroupId,
                status = TaskStatus.PENDING,
                createdAt = now,
            )
    }

    fun claim(
        actorId: ActorId,
        now: Instant,
    ): Task {
        require(status == TaskStatus.PENDING) { "Task must be PENDING to claim, was $status" }
        return copy(
            status = TaskStatus.CLAIMED,
            ownerId = actorId,
            claimedAt = now,
            version = version + 1,
        )
    }

    fun release(actorId: ActorId): Task {
        require(status == TaskStatus.CLAIMED) { "Task must be CLAIMED to release, was $status" }
        require(ownerId == actorId) { "Only the owner can release this task" }
        return copy(
            status = TaskStatus.PENDING,
            ownerId = null,
            claimedAt = null,
            version = version + 1,
        )
    }

    fun completeDecision(
        outcome: DecisionOutcome,
        actorId: ActorId,
        comment: String?,
        now: Instant,
    ): Pair<Task, Decision> {
        require(status == TaskStatus.CLAIMED) { "Task must be CLAIMED to decide, was $status" }
        require(ownerId == actorId) { "Only the owner can decide on this task" }

        val decision =
            Decision(
                taskId = id,
                outcome = outcome,
                actorId = actorId,
                comment = comment,
                decidedAt = now,
            )

        val completedTask =
            copy(
                status = TaskStatus.COMPLETED,
                completedAt = now,
                version = version + 1,
            )

        return completedTask to decision
    }
}
