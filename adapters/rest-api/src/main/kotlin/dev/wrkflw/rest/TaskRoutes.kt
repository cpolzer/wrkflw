package dev.wrkflw.rest

import dev.wrkflw.application.command.ClaimTaskCommand
import dev.wrkflw.application.command.ClaimTaskResult
import dev.wrkflw.application.command.ClaimTaskUseCase
import dev.wrkflw.application.command.ReleaseTaskCommand
import dev.wrkflw.application.command.ReleaseTaskResult
import dev.wrkflw.application.command.ReleaseTaskUseCase
import dev.wrkflw.application.command.SubmitDecisionCommand
import dev.wrkflw.application.command.SubmitDecisionResult
import dev.wrkflw.application.command.SubmitDecisionUseCase
import dev.wrkflw.domain.identity.TaskId
import dev.wrkflw.domain.task.DecisionOutcome
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.util.UUID

fun Route.taskRoutes(
    claimTask: ClaimTaskUseCase,
    releaseTask: ReleaseTaskUseCase,
    submitDecision: SubmitDecisionUseCase,
) {
    post("/tasks/{taskId}/claim") {
        val actor = try {
            HeaderActorContext.fromCall(call)
        } catch (e: MissingActorHeaderException) {
            call.respond(HttpStatusCode.BadRequest, ErrorDto(e.message ?: "Missing actor headers"))
            return@post
        }

        val taskId = parseTaskId(call) ?: return@post

        when (val result = claimTask.execute(ClaimTaskCommand(taskId, actor))) {
            ClaimTaskResult.NotFound -> call.respond(HttpStatusCode.NotFound, ErrorDto("Task not found"))
            ClaimTaskResult.Forbidden -> call.respond(HttpStatusCode.Forbidden, ErrorDto("Not in candidate group"))
            ClaimTaskResult.Conflict -> call.respond(HttpStatusCode.Conflict, ErrorDto("Task is not claimable"))
            is ClaimTaskResult.Success -> call.respond(HttpStatusCode.OK, result.task.toSummaryDto())
        }
    }

    post("/tasks/{taskId}/release") {
        val actor = try {
            HeaderActorContext.fromCall(call)
        } catch (e: MissingActorHeaderException) {
            call.respond(HttpStatusCode.BadRequest, ErrorDto(e.message ?: "Missing actor headers"))
            return@post
        }

        val taskId = parseTaskId(call) ?: return@post

        when (val result = releaseTask.execute(ReleaseTaskCommand(taskId, actor))) {
            ReleaseTaskResult.NotFound -> call.respond(HttpStatusCode.NotFound, ErrorDto("Task not found"))
            ReleaseTaskResult.Forbidden -> call.respond(HttpStatusCode.Forbidden, ErrorDto("Not the task owner"))
            ReleaseTaskResult.Conflict -> call.respond(HttpStatusCode.Conflict, ErrorDto("Task is not releasable"))
            is ReleaseTaskResult.Success -> call.respond(HttpStatusCode.OK, result.task.toSummaryDto())
        }
    }

    post("/tasks/{taskId}/decision") {
        val actor = try {
            HeaderActorContext.fromCall(call)
        } catch (e: MissingActorHeaderException) {
            call.respond(HttpStatusCode.BadRequest, ErrorDto(e.message ?: "Missing actor headers"))
            return@post
        }

        val taskId = parseTaskId(call) ?: return@post

        val body = call.receive<DecisionRequestDto>()
        val outcome = runCatching { DecisionOutcome.valueOf(body.outcome) }.getOrNull()
            ?: run {
                call.respond(HttpStatusCode.UnprocessableEntity, ErrorDto("Invalid outcome: ${body.outcome}"))
                return@post
            }

        when (val result = submitDecision.execute(SubmitDecisionCommand(taskId, outcome, body.comment, actor))) {
            SubmitDecisionResult.NotFound -> call.respond(HttpStatusCode.NotFound, ErrorDto("Task not found"))
            SubmitDecisionResult.Forbidden -> call.respond(HttpStatusCode.Forbidden, ErrorDto("Not the task owner"))
            SubmitDecisionResult.Conflict -> call.respond(HttpStatusCode.Conflict, ErrorDto("Task is not in a decidable state"))
            is SubmitDecisionResult.Success -> call.respond(HttpStatusCode.OK, result.flowInstance.toStatusDto())
        }
    }
}

private suspend fun parseTaskId(call: ApplicationCall): TaskId? {
    val raw = call.parameters["taskId"]
    val uuid = raw?.let { runCatching { UUID.fromString(it) }.getOrNull() }
    if (uuid == null) {
        call.respond(HttpStatusCode.BadRequest, ErrorDto("Invalid taskId"))
        return null
    }
    return TaskId(uuid)
}
