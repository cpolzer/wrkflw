package dev.wrkflw.application.query

import dev.wrkflw.domain.port.ActorContext
import dev.wrkflw.domain.port.TaskRepository
import dev.wrkflw.domain.task.Task

data class GroupWorkListQuery(
    val actor: ActorContext,
)

data class GroupWorkListResult(
    val tasks: List<Task>,
)

fun interface GroupWorkListUseCase {
    suspend fun execute(query: GroupWorkListQuery): GroupWorkListResult
}

class GroupWorkListService(
    private val tasks: TaskRepository,
) : GroupWorkListUseCase {
    override suspend fun execute(query: GroupWorkListQuery): GroupWorkListResult {
        val found =
            query.actor.groupIds
                .flatMap { group -> tasks.findPendingByCandidateGroup(group.value) }
                .distinctBy { it.id }
        return GroupWorkListResult(found)
    }
}
