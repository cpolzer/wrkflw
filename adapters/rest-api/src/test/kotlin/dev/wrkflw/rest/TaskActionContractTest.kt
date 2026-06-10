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
import dev.wrkflw.domain.flow.FlowInstance
import dev.wrkflw.domain.flow.FlowStatus
import dev.wrkflw.domain.identity.ActorId
import dev.wrkflw.domain.identity.FlowDefinitionKey
import dev.wrkflw.domain.identity.FlowInstanceId
import dev.wrkflw.domain.identity.GroupId
import dev.wrkflw.domain.identity.TaskId
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
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class TaskActionContractTest {

    private val fixedNow = Instant.parse("2026-01-01T00:00:00Z")

    private val flowInstanceId = FlowInstanceId(UUID.fromString("00000000-0000-0000-0000-000000000001"))
    private val taskId = UUID.fromString("00000000-0000-0000-0000-000000000002")

    private val sampleTask = Task(
        id = TaskId(taskId),
        flowInstanceId = flowInstanceId,
        stateName = "Submitted",
        candidateGroupId = GroupId("reviewers"),
        status = TaskStatus.CLAIMED,
        ownerId = ActorId("reviewer1"),
        version = 1,
        createdAt = fixedNow,
        claimedAt = fixedNow,
    )

    private val sampleInstance = FlowInstance(
        id = flowInstanceId,
        definitionKey = FlowDefinitionKey("document-approval"),
        definitionVersion = 1,
        documentRef = "doc-001",
        submitterId = ActorId("author1"),
        currentState = "Approved",
        status = FlowStatus.COMPLETED,
        terminalOutcome = "APPROVED",
        createdAt = fixedNow,
        updatedAt = fixedNow,
    )

    private fun withApp(
        claimTask: ClaimTaskUseCase = ClaimTaskUseCase { ClaimTaskResult.NotFound },
        releaseTask: ReleaseTaskUseCase = ReleaseTaskUseCase { ReleaseTaskResult.NotFound },
        submitDecision: SubmitDecisionUseCase = SubmitDecisionUseCase { SubmitDecisionResult.NotFound },
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
                route("/api/v1") { taskRoutes(claimTask, releaseTask, submitDecision) }
            }
        }
        val client = createClient {
            install(ClientContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        block(client)
    }

    // ── claim ────────────────────────────────────────────────────

    @Test
    fun `POST claim returns 200 with TaskSummary on success`() = withApp(
        claimTask = ClaimTaskUseCase { ClaimTaskResult.Success(sampleTask) },
    ) { client ->
        val response = client.post("/api/v1/tasks/$taskId/claim") {
            header("X-Actor-Id", "reviewer1")
            header("X-Actor-Groups", "reviewers")
        }
        response.status shouldBe HttpStatusCode.OK
        val body = response.body<TaskSummaryDto>()
        body.taskId shouldBe taskId.toString()
        body.status shouldBe "CLAIMED"
        body.ownerId shouldBe "reviewer1"
    }

    @Test
    fun `POST claim returns 404 when task not found`() = withApp(
        claimTask = ClaimTaskUseCase { ClaimTaskResult.NotFound },
    ) { client ->
        val response = client.post("/api/v1/tasks/$taskId/claim") {
            header("X-Actor-Id", "reviewer1")
            header("X-Actor-Groups", "reviewers")
        }
        response.status shouldBe HttpStatusCode.NotFound
    }

    @Test
    fun `POST claim returns 403 when actor not in candidate group`() = withApp(
        claimTask = ClaimTaskUseCase { ClaimTaskResult.Forbidden },
    ) { client ->
        val response = client.post("/api/v1/tasks/$taskId/claim") {
            header("X-Actor-Id", "author1")
            header("X-Actor-Groups", "authors")
        }
        response.status shouldBe HttpStatusCode.Forbidden
    }

    @Test
    fun `POST claim returns 409 when task not claimable`() = withApp(
        claimTask = ClaimTaskUseCase { ClaimTaskResult.Conflict },
    ) { client ->
        val response = client.post("/api/v1/tasks/$taskId/claim") {
            header("X-Actor-Id", "reviewer1")
            header("X-Actor-Groups", "reviewers")
        }
        response.status shouldBe HttpStatusCode.Conflict
    }

    @Test
    fun `POST claim returns 400 when actor header is missing`() = withApp { client ->
        val response = client.post("/api/v1/tasks/$taskId/claim")
        response.status shouldBe HttpStatusCode.BadRequest
    }

    // ── release ──────────────────────────────────────────────────

    @Test
    fun `POST release returns 200 with TaskSummary on success`() = withApp(
        releaseTask = ReleaseTaskUseCase { ReleaseTaskResult.Success(sampleTask.copy(status = TaskStatus.PENDING, ownerId = null)) },
    ) { client ->
        val response = client.post("/api/v1/tasks/$taskId/release") {
            header("X-Actor-Id", "reviewer1")
            header("X-Actor-Groups", "reviewers")
        }
        response.status shouldBe HttpStatusCode.OK
        val body = response.body<TaskSummaryDto>()
        body.status shouldBe "PENDING"
        body.ownerId shouldBe null
    }

    @Test
    fun `POST release returns 403 when actor is not owner`() = withApp(
        releaseTask = ReleaseTaskUseCase { ReleaseTaskResult.Forbidden },
    ) { client ->
        val response = client.post("/api/v1/tasks/$taskId/release") {
            header("X-Actor-Id", "reviewer2")
            header("X-Actor-Groups", "reviewers")
        }
        response.status shouldBe HttpStatusCode.Forbidden
    }

    @Test
    fun `POST release returns 409 when task not releasable`() = withApp(
        releaseTask = ReleaseTaskUseCase { ReleaseTaskResult.Conflict },
    ) { client ->
        val response = client.post("/api/v1/tasks/$taskId/release") {
            header("X-Actor-Id", "reviewer1")
            header("X-Actor-Groups", "reviewers")
        }
        response.status shouldBe HttpStatusCode.Conflict
    }

    // ── decision ─────────────────────────────────────────────────

    @Test
    fun `POST decision returns 200 with FlowStatus on success`() = withApp(
        submitDecision = SubmitDecisionUseCase { SubmitDecisionResult.Success(sampleInstance) },
    ) { client ->
        val response = client.post("/api/v1/tasks/$taskId/decision") {
            header("X-Actor-Id", "reviewer1")
            header("X-Actor-Groups", "reviewers")
            contentType(ContentType.Application.Json)
            setBody("""{"outcome":"APPROVE"}""")
        }
        response.status shouldBe HttpStatusCode.OK
        val body = response.body<FlowStatusResponseDto>()
        body.status shouldBe "COMPLETED"
        body.terminalOutcome shouldBe "APPROVED"
    }

    @Test
    fun `POST decision returns 403 when actor is not owner`() = withApp(
        submitDecision = SubmitDecisionUseCase { SubmitDecisionResult.Forbidden },
    ) { client ->
        val response = client.post("/api/v1/tasks/$taskId/decision") {
            header("X-Actor-Id", "reviewer2")
            header("X-Actor-Groups", "reviewers")
            contentType(ContentType.Application.Json)
            setBody("""{"outcome":"APPROVE"}""")
        }
        response.status shouldBe HttpStatusCode.Forbidden
    }

    @Test
    fun `POST decision returns 409 when task is not decidable`() = withApp(
        submitDecision = SubmitDecisionUseCase { SubmitDecisionResult.Conflict },
    ) { client ->
        val response = client.post("/api/v1/tasks/$taskId/decision") {
            header("X-Actor-Id", "reviewer1")
            header("X-Actor-Groups", "reviewers")
            contentType(ContentType.Application.Json)
            setBody("""{"outcome":"APPROVE"}""")
        }
        response.status shouldBe HttpStatusCode.Conflict
    }

    @Test
    fun `POST decision returns 422 when outcome is invalid`() = withApp { client ->
        val response = client.post("/api/v1/tasks/$taskId/decision") {
            header("X-Actor-Id", "reviewer1")
            header("X-Actor-Groups", "reviewers")
            contentType(ContentType.Application.Json)
            setBody("""{"outcome":"MAYBE"}""")
        }
        response.status shouldBe HttpStatusCode.UnprocessableEntity
    }

    @Test
    fun `POST decision returns 404 when task not found`() = withApp(
        submitDecision = SubmitDecisionUseCase { SubmitDecisionResult.NotFound },
    ) { client ->
        val response = client.post("/api/v1/tasks/$taskId/decision") {
            header("X-Actor-Id", "reviewer1")
            header("X-Actor-Groups", "reviewers")
            contentType(ContentType.Application.Json)
            setBody("""{"outcome":"APPROVE"}""")
        }
        response.status shouldBe HttpStatusCode.NotFound
    }
}
