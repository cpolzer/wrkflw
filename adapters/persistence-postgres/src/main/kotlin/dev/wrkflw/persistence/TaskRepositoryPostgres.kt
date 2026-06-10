package dev.wrkflw.persistence

import dev.wrkflw.domain.identity.ActorId
import dev.wrkflw.domain.identity.FlowInstanceId
import dev.wrkflw.domain.identity.GroupId
import dev.wrkflw.domain.identity.TaskId
import dev.wrkflw.domain.port.TaskRepository
import dev.wrkflw.domain.task.Task
import dev.wrkflw.domain.task.TaskStatus
import dev.wrkflw.persistence.generated.tables.references.TASK
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jooq.DSLContext
import java.time.ZoneOffset

class TaskRepositoryPostgres(
    private val dsl: DSLContext,
) : TaskRepository {
    override suspend fun findById(id: TaskId): Task? =
        withContext(Dispatchers.IO) {
            dsl
                .selectFrom(TASK)
                .where(TASK.ID.eq(id.value))
                .fetchOne()
                ?.toDomain()
        }

    override suspend fun findByFlowInstanceId(flowInstanceId: FlowInstanceId): List<Task> =
        withContext(Dispatchers.IO) {
            dsl
                .selectFrom(TASK)
                .where(TASK.FLOW_INSTANCE_ID.eq(flowInstanceId.value))
                .fetch()
                .map { it.toDomain() }
        }

    override suspend fun findPendingByCandidateGroup(groupId: String): List<Task> =
        withContext(Dispatchers.IO) {
            dsl
                .selectFrom(TASK)
                .where(TASK.CANDIDATE_GROUP_ID.eq(groupId))
                .and(TASK.STATUS.eq(TaskStatus.PENDING.name))
                .fetch()
                .map { it.toDomain() }
        }

    override suspend fun findClaimedByOwner(ownerId: String): List<Task> =
        withContext(Dispatchers.IO) {
            dsl
                .selectFrom(TASK)
                .where(TASK.OWNER_ID.eq(ownerId))
                .and(TASK.STATUS.eq(TaskStatus.CLAIMED.name))
                .fetch()
                .map { it.toDomain() }
        }

    override suspend fun save(task: Task) {
        withContext(Dispatchers.IO) {
            dsl
                .insertInto(TASK)
                .set(TASK.ID, task.id.value)
                .set(TASK.FLOW_INSTANCE_ID, task.flowInstanceId.value)
                .set(TASK.STATE_NAME, task.stateName)
                .set(TASK.CANDIDATE_GROUP_ID, task.candidateGroupId.value)
                .set(TASK.STATUS, task.status.name)
                .set(TASK.OWNER_ID, task.ownerId?.value)
                .set(TASK.VERSION, task.version)
                .set(TASK.CREATED_AT, task.createdAt.atOffset(ZoneOffset.UTC))
                .set(TASK.CLAIMED_AT, task.claimedAt?.atOffset(ZoneOffset.UTC))
                .set(TASK.COMPLETED_AT, task.completedAt?.atOffset(ZoneOffset.UTC))
                .execute()
        }
    }

    override suspend fun update(task: Task): Int =
        withContext(Dispatchers.IO) {
            dsl
                .update(TASK)
                .set(TASK.STATUS, task.status.name)
                .set(TASK.OWNER_ID, task.ownerId?.value)
                .set(TASK.VERSION, task.version)
                .set(TASK.CLAIMED_AT, task.claimedAt?.atOffset(ZoneOffset.UTC))
                .set(TASK.COMPLETED_AT, task.completedAt?.atOffset(ZoneOffset.UTC))
                .where(TASK.ID.eq(task.id.value))
                .execute()
        }

    override suspend fun updateConditional(
        task: Task,
        expectedStatus: TaskStatus,
        expectedVersion: Int,
    ): Int =
        withContext(Dispatchers.IO) {
            dsl
                .update(TASK)
                .set(TASK.STATUS, task.status.name)
                .set(TASK.OWNER_ID, task.ownerId?.value)
                .set(TASK.VERSION, task.version)
                .set(TASK.CLAIMED_AT, task.claimedAt?.atOffset(ZoneOffset.UTC))
                .set(TASK.COMPLETED_AT, task.completedAt?.atOffset(ZoneOffset.UTC))
                .where(TASK.ID.eq(task.id.value))
                .and(TASK.STATUS.eq(expectedStatus.name))
                .and(TASK.VERSION.eq(expectedVersion))
                .execute()
        }

    private fun dev.wrkflw.persistence.generated.tables.records.TaskRecord.toDomain() =
        Task(
            id = TaskId(id!!),
            flowInstanceId = FlowInstanceId(flowInstanceId!!),
            stateName = stateName!!,
            candidateGroupId = GroupId(candidateGroupId!!),
            status = TaskStatus.valueOf(status!!),
            ownerId = ownerId?.let { ActorId(it) },
            version = version!!,
            createdAt = createdAt!!.toInstant(),
            claimedAt = claimedAt?.toInstant(),
            completedAt = completedAt?.toInstant(),
        )
}
