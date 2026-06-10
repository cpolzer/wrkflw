package dev.wrkflw.rest

import dev.wrkflw.application.command.SubmitDocumentCommand
import dev.wrkflw.application.command.SubmitDocumentResult
import dev.wrkflw.application.command.SubmitDocumentUseCase
import dev.wrkflw.domain.port.TaskRepository
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.flowRoutes(
    submitDocument: SubmitDocumentUseCase,
    tasks: TaskRepository,
) {
    post("/flows") {
        val actor =
            try {
                HeaderActorContext.fromCall(call)
            } catch (e: MissingActorHeaderException) {
                call.respond(HttpStatusCode.BadRequest, ErrorDto(e.message ?: "Missing actor headers"))
                return@post
            }

        val body = call.receive<SubmitDocumentRequestDto>()

        if (body.documentRef.isBlank()) {
            call.respond(HttpStatusCode.UnprocessableEntity, ErrorDto("documentRef must not be blank"))
            return@post
        }

        when (val result = submitDocument.execute(SubmitDocumentCommand(body.definitionKey, body.documentRef, actor))) {
            SubmitDocumentResult.DefinitionNotFound ->
                call.respond(HttpStatusCode.NotFound, ErrorDto("Flow definition '${body.definitionKey}' not found"))

            SubmitDocumentResult.Unauthorized ->
                call.respond(HttpStatusCode.Forbidden, ErrorDto("Actor is not a member of the initiator group"))

            is SubmitDocumentResult.Success -> {
                val pendingTasks = tasks.findByFlowInstanceId(result.instance.id)
                call.respond(HttpStatusCode.Created, result.instance.toStatusDto(pendingTasks))
            }
        }
    }
}
