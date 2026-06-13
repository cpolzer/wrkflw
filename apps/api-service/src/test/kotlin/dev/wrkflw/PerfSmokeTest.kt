package dev.wrkflw

import dev.wrkflw.application.query.FlowStatusQuery
import dev.wrkflw.application.query.FlowStatusResult
import dev.wrkflw.application.query.FlowStatusService
import dev.wrkflw.application.query.GroupWorkListQuery
import dev.wrkflw.application.query.GroupWorkListService
import dev.wrkflw.domain.flow.FlowInstance
import dev.wrkflw.domain.identity.ActorId
import dev.wrkflw.domain.identity.FlowDefinitionKey
import dev.wrkflw.domain.identity.FlowInstanceId
import dev.wrkflw.domain.identity.GroupId
import dev.wrkflw.domain.port.ActorContext
import dev.wrkflw.domain.task.Task
import dev.wrkflw.persistence.AuditLogPostgres
import dev.wrkflw.persistence.FlowInstanceRepositoryPostgres
import dev.wrkflw.persistence.JooqDslContextProvider
import dev.wrkflw.persistence.TaskRepositoryPostgres
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.runBlocking
import org.flywaydb.core.Flyway
import org.jooq.DSLContext
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.postgresql.ds.PGSimpleDataSource
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import java.time.Instant
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.measureTime

@Tag("perf")
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PerfSmokeTest {
    companion object {
        @Container
        @JvmStatic
        val postgres =
            PostgreSQLContainer("postgres:16-alpine")
                .withDatabaseName("wrkflw_perf")
                .withUsername("wrkflw")
                .withPassword("wrkflw")

        const val FLOW_COUNT = 1_000
        const val SAMPLE_COUNT = 50
        const val P95_THRESHOLD_MS = 1_000L
    }

    private lateinit var dsl: DSLContext
    private lateinit var groupWorkList: GroupWorkListService
    private lateinit var flowStatus: FlowStatusService
    private var sampleFlowId: FlowInstanceId? = null

    @BeforeAll
    fun seedData() {
        Flyway
            .configure()
            .dataSource(postgres.jdbcUrl, postgres.username, postgres.password)
            .locations("filesystem:${System.getProperty("wrkflw.migrations.dir")}")
            .load()
            .migrate()

        val dataSource =
            PGSimpleDataSource().apply {
                setURL(postgres.jdbcUrl)
                user = postgres.username
                password = postgres.password
            }
        dsl = JooqDslContextProvider(dataSource).create()

        val instances = FlowInstanceRepositoryPostgres(dsl)
        val tasks = TaskRepositoryPostgres(dsl)
        val auditLog = AuditLogPostgres(dsl)
        groupWorkList = GroupWorkListService(tasks)
        flowStatus = FlowStatusService(instances, tasks, auditLog)

        val now = Instant.now()
        val firstId = FlowInstanceId(UUID.randomUUID())
        sampleFlowId = firstId

        repeat(FLOW_COUNT) { i ->
            val instanceId = if (i == 0) firstId else FlowInstanceId(UUID.randomUUID())
            val instance =
                FlowInstance.start(
                    id = instanceId,
                    definitionKey = FlowDefinitionKey("document-approval"),
                    definitionVersion = 1,
                    documentRef = "perf-doc-$i",
                    submitterId = ActorId("author1"),
                    initialState = "InitialReview",
                    now = now,
                )
            runBlocking { instances.save(instance) }

            val task =
                Task.create(
                    flowInstanceId = instanceId,
                    stateName = "InitialReview",
                    candidateGroupId = GroupId("reviewers"),
                    now = now,
                )
            runBlocking { tasks.save(task) }
        }
    }

    @Test
    fun `worklist query p95 is under threshold`() {
        val actor = ActorContext(ActorId("reviewer1"), setOf(GroupId("reviewers")))
        val query = GroupWorkListQuery(actor)

        val durations =
            (1..SAMPLE_COUNT).map {
                measureTime { runBlocking { groupWorkList.execute(query) } }
            }

        val p95 = durations.sorted()[((SAMPLE_COUNT - 1) * 0.95).toInt()]
        println("Worklist p95: ${p95.inWholeMilliseconds}ms (threshold: ${P95_THRESHOLD_MS}ms)")

        p95.inWholeMilliseconds shouldNotBe null
        (p95 < P95_THRESHOLD_MS.milliseconds) shouldBe true
    }

    @Test
    fun `flow status query p95 is under threshold`() {
        val query = FlowStatusQuery(sampleFlowId!!)

        val durations =
            (1..SAMPLE_COUNT).map {
                measureTime {
                    val result = runBlocking { flowStatus.execute(query) }
                    (result is FlowStatusResult.Found) shouldBe true
                }
            }

        val p95 = durations.sorted()[((SAMPLE_COUNT - 1) * 0.95).toInt()]
        println("Flow-status p95: ${p95.inWholeMilliseconds}ms (threshold: ${P95_THRESHOLD_MS}ms)")

        (p95 < P95_THRESHOLD_MS.milliseconds) shouldBe true
    }
}
