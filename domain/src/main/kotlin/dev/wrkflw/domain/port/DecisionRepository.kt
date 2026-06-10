package dev.wrkflw.domain.port

import dev.wrkflw.domain.identity.TaskId
import dev.wrkflw.domain.task.Decision

interface DecisionRepository {
    suspend fun save(decision: Decision)

    suspend fun findByTaskId(taskId: TaskId): Decision?
}
