package dev.wrkflw.rest

import dev.wrkflw.application.query.FlowStatusQuery
import dev.wrkflw.application.query.FlowStatusResult
import dev.wrkflw.application.query.FlowStatusUseCase
import dev.wrkflw.application.query.GroupWorkListQuery
import dev.wrkflw.application.query.GroupWorkListUseCase
import dev.wrkflw.application.query.MyTasksQuery
import dev.wrkflw.application.query.MyTasksUseCase
import dev.wrkflw.domain.identity.FlowInstanceId
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.util.UUID

fun Route.queryRoutes(
    groupWorkList: GroupWorkListUseCase,
    myTasks: MyTasksUseCase,
    flowStatus: FlowStatusUseCase,
) {
    get("/worklists/group") { handleGroupWorklist(call, groupWorkList) }
    get("/worklists/mine") { handleMyTasks(call, myTasks) }
    get("/flows/{flowId}") { handleFlowStatus(call, flowStatus) }
}

private suspend fun handleGroupWorklist(
    call: ApplicationCall,
    groupWorkList: GroupWorkListUseCase,
) {
    val actor =
        try {
            HeaderActorContext.fromCall(call)
        } catch (e: MissingActorHeaderException) {
            call.respond(HttpStatusCode.BadRequest, ErrorDto(e.message ?: "Missing actor headers"))
            return
        }
    val result = groupWorkList.execute(GroupWorkListQuery(actor))
    call.respond(result.tasks.map { it.toSummaryDto() })
}

private suspend fun handleMyTasks(
    call: ApplicationCall,
    myTasks: MyTasksUseCase,
) {
    val actor =
        try {
            HeaderActorContext.fromCall(call)
        } catch (e: MissingActorHeaderException) {
            call.respond(HttpStatusCode.BadRequest, ErrorDto(e.message ?: "Missing actor headers"))
            return
        }
    val result = myTasks.execute(MyTasksQuery(actor))
    call.respond(result.tasks.map { it.toSummaryDto() })
}

private suspend fun handleFlowStatus(
    call: ApplicationCall,
    flowStatus: FlowStatusUseCase,
) {
    val rawId = call.parameters["flowId"] ?: return call.respond(HttpStatusCode.BadRequest, ErrorDto("Missing flowId"))
    val uuid =
        runCatching { UUID.fromString(rawId) }.getOrNull()
            ?: return call.respond(HttpStatusCode.BadRequest, ErrorDto("Invalid flowId"))

    when (val result = flowStatus.execute(FlowStatusQuery(FlowInstanceId(uuid)))) {
        FlowStatusResult.NotFound -> call.respond(HttpStatusCode.NotFound, ErrorDto("Flow not found"))
        is FlowStatusResult.Found ->
            call.respond(result.instance.toStatusDto(result.pendingTasks, result.history))
    }
}
