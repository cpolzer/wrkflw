package dev.wrkflw.rest

import dev.wrkflw.application.command.SubmitDocumentResult
import dev.wrkflw.application.command.SubmitDocumentUseCase
import dev.wrkflw.application.query.SubmitterFlowsResult
import dev.wrkflw.application.query.SubmitterFlowsUseCase
import dev.wrkflw.domain.flow.FlowInstance
import dev.wrkflw.domain.flow.FlowStatus
import dev.wrkflw.domain.identity.ActorId
import dev.wrkflw.domain.identity.FlowDefinitionKey
import dev.wrkflw.domain.identity.FlowInstanceId
import dev.wrkflw.domain.identity.TaskId
import dev.wrkflw.domain.port.TaskRepository
import dev.wrkflw.domain.task.Task
import dev.wrkflw.domain.task.TaskStatus
import io.kotest.matchers.shouldBe
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation

class SubmitFlowContractTest {
    private val fixedNow = Instant.parse("2026-01-01T00:00:00Z")

    private val sampleInstance =
        FlowInstance(
            id = FlowInstanceId(UUID.fromString("00000000-0000-0000-0000-000000000001")),
            definitionKey = FlowDefinitionKey("document-approval"),
            definitionVersion = 1,
            documentRef = "doc-ref-001",
            submitterId = ActorId("author1"),
            currentState = "Submitted",
            status = FlowStatus.RUNNING,
            terminalOutcome = null,
            createdAt = fixedNow,
            updatedAt = fixedNow,
        )

    private fun withApp(
        useCase: SubmitDocumentUseCase,
        taskRepo: TaskRepository = noOpTaskRepo(),
        submitterFlows: SubmitterFlowsUseCase = SubmitterFlowsUseCase { SubmitterFlowsResult(emptyList()) },
        block: suspend ApplicationTestBuilder.(HttpClient) -> Unit,
    ) = testApplication {
        application {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            install(StatusPages) {
                exception<Throwable> { call, cause ->
                    call.respond(HttpStatusCode.InternalServerError, ErrorDto(cause.message ?: "error"))
                }
            }
            routing {
                route("/api/v1") { flowRoutes(useCase, taskRepo, submitterFlows) }
            }
        }
        val client =
            createClient {
                install(ClientContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            }
        block(client)
    }

    @Test
    fun `POST flows returns 201 with flow status on success`() =
        withApp(
            useCase = SubmitDocumentUseCase { _ -> SubmitDocumentResult.Success(sampleInstance) },
        ) { client ->
            val response =
                client.post("/api/v1/flows") {
                    header("X-Actor-Id", "author1")
                    header("X-Actor-Groups", "authors")
                    contentType(ContentType.Application.Json)
                    setBody("""{"definitionKey":"document-approval","documentRef":"doc-ref-001"}""")
                }
            response.status shouldBe HttpStatusCode.Created
            val body = response.body<FlowStatusResponseDto>()
            body.flowId shouldBe "00000000-0000-0000-0000-000000000001"
            body.status shouldBe "RUNNING"
            body.currentState shouldBe "Submitted"
            body.pendingTasks shouldBe emptyList()
        }

    @Test
    fun `POST flows returns 403 when actor is not in initiator group`() =
        withApp(
            useCase = SubmitDocumentUseCase { _ -> SubmitDocumentResult.Unauthorized },
        ) { client ->
            val response =
                client.post("/api/v1/flows") {
                    header("X-Actor-Id", "reviewer1")
                    header("X-Actor-Groups", "reviewers")
                    contentType(ContentType.Application.Json)
                    setBody("""{"definitionKey":"document-approval","documentRef":"doc-ref-002"}""")
                }
            response.status shouldBe HttpStatusCode.Forbidden
        }

    @Test
    fun `POST flows returns 404 when definition not found`() =
        withApp(
            useCase = SubmitDocumentUseCase { _ -> SubmitDocumentResult.DefinitionNotFound },
        ) { client ->
            val response =
                client.post("/api/v1/flows") {
                    header("X-Actor-Id", "author1")
                    header("X-Actor-Groups", "authors")
                    contentType(ContentType.Application.Json)
                    setBody("""{"definitionKey":"unknown-flow","documentRef":"doc-ref-003"}""")
                }
            response.status shouldBe HttpStatusCode.NotFound
        }

    @Test
    fun `POST flows returns 422 when documentRef is blank`() =
        withApp(
            useCase = SubmitDocumentUseCase { _ -> SubmitDocumentResult.DefinitionNotFound },
        ) { client ->
            val response =
                client.post("/api/v1/flows") {
                    header("X-Actor-Id", "author1")
                    header("X-Actor-Groups", "authors")
                    contentType(ContentType.Application.Json)
                    setBody("""{"definitionKey":"document-approval","documentRef":""}""")
                }
            response.status shouldBe HttpStatusCode.UnprocessableEntity
        }

    @Test
    fun `POST flows returns 400 when X-Actor-Id header is missing`() =
        withApp(
            useCase = SubmitDocumentUseCase { _ -> SubmitDocumentResult.DefinitionNotFound },
        ) { client ->
            val response =
                client.post("/api/v1/flows") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"definitionKey":"document-approval","documentRef":"doc-ref-004"}""")
                }
            response.status shouldBe HttpStatusCode.BadRequest
        }

    private fun noOpTaskRepo(): TaskRepository =
        object : TaskRepository {
            override suspend fun findById(id: TaskId): Task? = null

            override suspend fun findByFlowInstanceId(flowInstanceId: FlowInstanceId) = emptyList<Task>()

            override suspend fun findPendingByCandidateGroup(groupId: String) = emptyList<Task>()

            override suspend fun findClaimedByOwner(ownerId: String) = emptyList<Task>()

            override suspend fun save(task: Task) {}

            override suspend fun update(task: Task) = 0

            override suspend fun updateConditional(
                task: Task,
                expectedStatus: TaskStatus,
                expectedVersion: Int,
            ) = 0
        }
}
