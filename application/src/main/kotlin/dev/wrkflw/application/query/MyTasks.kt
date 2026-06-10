package dev.wrkflw.application.query

import dev.wrkflw.domain.port.ActorContext
import dev.wrkflw.domain.port.TaskRepository
import dev.wrkflw.domain.task.Task

data class MyTasksQuery(
    val actor: ActorContext,
)

data class MyTasksResult(
    val tasks: List<Task>,
)

fun interface MyTasksUseCase {
    suspend fun execute(query: MyTasksQuery): MyTasksResult
}

class MyTasksService(
    private val tasks: TaskRepository,
) : MyTasksUseCase {
    override suspend fun execute(query: MyTasksQuery): MyTasksResult {
        val found = tasks.findClaimedByOwner(query.actor.actorId.value)
        return MyTasksResult(found)
    }
}
