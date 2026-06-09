package dev.wrkflw.domain.port

import dev.wrkflw.domain.identity.FlowInstanceId
import dev.wrkflw.domain.identity.TaskId
import dev.wrkflw.domain.task.Task

interface TaskRepository {
    suspend fun findById(id: TaskId): Task?
    suspend fun findByFlowInstanceId(flowInstanceId: FlowInstanceId): List<Task>
    suspend fun findPendingByCandidateGroup(groupId: String): List<Task>
    suspend fun findClaimedByOwner(ownerId: String): List<Task>
    suspend fun save(task: Task)
    suspend fun update(task: Task): Int
    suspend fun updateConditional(task: Task, expectedStatus: dev.wrkflw.domain.task.TaskStatus, expectedVersion: Int): Int
}
