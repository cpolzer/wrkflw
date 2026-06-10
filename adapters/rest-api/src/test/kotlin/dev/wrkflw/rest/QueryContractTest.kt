package dev.wrkflw.rest

import dev.wrkflw.application.query.FlowStatusResult
import dev.wrkflw.application.query.FlowStatusUseCase
import dev.wrkflw.application.query.GroupWorkListResult
import dev.wrkflw.application.query.GroupWorkListUseCase
import dev.wrkflw.application.query.MyTasksResult
import dev.wrkflw.application.query.MyTasksUseCase
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
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation

class QueryContractTest {
    private val now = Instant.parse("2026-01-01T00:00:00Z")
    private val instanceId = FlowInstanceId(UUID.fromString("00000000-0000-0000-0000-000000000042"))

    private val sampleInstance =
        FlowInstance(
            id = instanceId,
            definitionKey = FlowDefinitionKey("document-approval"),
            definitionVersion = 1,
            documentRef = "doc-001",
            submitterId = ActorId("author1"),
            currentState = "Submitted",
            status = FlowStatus.RUNNING,
            terminalOutcome = null,
            createdAt = now,
            updatedAt = now,
        )

    private val sampleTask =
        Task(
            id = TaskId(UUID.fromString("00000000-0000-0000-0000-000000000099")),
            flowInstanceId = instanceId,
            stateName = "Submitted",
            candidateGroupId = GroupId("reviewers"),
            status = TaskStatus.PENDING,
            createdAt = now,
        )

    private fun withApp(
        groupWorkList: GroupWorkListUseCase = GroupWorkListUseCase { GroupWorkListResult(emptyList()) },
        myTasks: MyTasksUseCase = MyTasksUseCase { MyTasksResult(emptyList()) },
        flowStatus: FlowStatusUseCase = FlowStatusUseCase { FlowStatusResult.NotFound },
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
                route("/api/v1") { queryRoutes(groupWorkList, myTasks, flowStatus) }
            }
        }
        val client =
            createClient {
                install(ClientContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            }
        block(client)
    }

    // GET /worklists/group

    @Test
    fun `GET worklists-group returns 200 with task list`() =
        withApp(
            groupWorkList = GroupWorkListUseCase { GroupWorkListResult(listOf(sampleTask)) },
        ) { client ->
            val response =
                client.get("/api/v1/worklists/group") {
                    header("X-Actor-Id", "reviewer1")
                    header("X-Actor-Groups", "reviewers")
                }
            response.status shouldBe HttpStatusCode.OK
            val body = response.body<List<TaskSummaryDto>>()
            body.size shouldBe 1
            body.first().taskId shouldBe sampleTask.id.value.toString()
            body.first().status shouldBe "PENDING"
        }

    @Test
    fun `GET worklists-group returns 400 when actor header is missing`() =
        withApp { client ->
            val response = client.get("/api/v1/worklists/group")
            response.status shouldBe HttpStatusCode.BadRequest
        }

    @Test
    fun `GET worklists-group returns empty list when no tasks`() =
        withApp { client ->
            val response =
                client.get("/api/v1/worklists/group") {
                    header("X-Actor-Id", "reviewer1")
                    header("X-Actor-Groups", "reviewers")
                }
            response.status shouldBe HttpStatusCode.OK
            response.body<List<TaskSummaryDto>>() shouldBe emptyList()
        }

    // GET /worklists/mine

    @Test
    fun `GET worklists-mine returns 200 with claimed tasks`() =
        withApp(
            myTasks = MyTasksUseCase { MyTasksResult(listOf(sampleTask.copy(status = TaskStatus.CLAIMED))) },
        ) { client ->
            val response =
                client.get("/api/v1/worklists/mine") {
                    header("X-Actor-Id", "reviewer1")
                    header("X-Actor-Groups", "reviewers")
                }
            response.status shouldBe HttpStatusCode.OK
            val body = response.body<List<TaskSummaryDto>>()
            body.size shouldBe 1
            body.first().status shouldBe "CLAIMED"
        }

    @Test
    fun `GET worklists-mine returns 400 when actor header is missing`() =
        withApp { client ->
            val response = client.get("/api/v1/worklists/mine")
            response.status shouldBe HttpStatusCode.BadRequest
        }

    // GET /flows/{flowId}

    @Test
    fun `GET flows by id returns 200 with flow status`() =
        withApp(
            flowStatus =
                FlowStatusUseCase {
                    FlowStatusResult.Found(sampleInstance, listOf(sampleTask), emptyList())
                },
        ) { client ->
            val response =
                client.get("/api/v1/flows/${instanceId.value}") {
                    header("X-Actor-Id", "any")
                    header("X-Actor-Groups", "any")
                }
            response.status shouldBe HttpStatusCode.OK
            val body = response.body<FlowStatusResponseDto>()
            body.flowId shouldBe instanceId.value.toString()
            body.currentState shouldBe "Submitted"
            body.status shouldBe "RUNNING"
            body.pendingTasks.size shouldBe 1
        }

    @Test
    fun `GET flows by id returns 404 for unknown id`() =
        withApp { client ->
            val response =
                client.get("/api/v1/flows/${UUID.randomUUID()}") {
                    header("X-Actor-Id", "any")
                    header("X-Actor-Groups", "any")
                }
            response.status shouldBe HttpStatusCode.NotFound
        }

    @Test
    fun `GET flows by id returns 400 for invalid UUID`() =
        withApp { client ->
            val response =
                client.get("/api/v1/flows/not-a-uuid") {
                    header("X-Actor-Id", "any")
                    header("X-Actor-Groups", "any")
                }
            response.status shouldBe HttpStatusCode.BadRequest
        }
}
