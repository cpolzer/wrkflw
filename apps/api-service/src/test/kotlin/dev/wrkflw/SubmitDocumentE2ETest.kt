package dev.wrkflw

import dev.wrkflw.application.command.SubmitDocumentCommand
import dev.wrkflw.application.command.SubmitDocumentResult
import dev.wrkflw.application.command.SubmitDocumentService
import dev.wrkflw.domain.port.SystemClock
import dev.wrkflw.domain.task.TaskStatus
import dev.wrkflw.persistence.AuditLogPostgres
import dev.wrkflw.persistence.FlowDefinitionRepositoryPostgres
import dev.wrkflw.persistence.FlowInstanceRepositoryPostgres
import dev.wrkflw.persistence.JooqDslContextProvider
import dev.wrkflw.persistence.TaskRepositoryPostgres
import dev.wrkflw.temporal.DocumentApprovalWorkflow
import dev.wrkflw.temporal.DocumentApprovalWorkflowImpl
import dev.wrkflw.temporal.TemporalWorkflowEngine
import dev.wrkflw.temporal.activity.AdvanceFlowActivityImpl
import dev.wrkflw.temporal.activity.CreateHumanTaskActivityImpl
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.temporal.testing.TestWorkflowEnvironment
import io.temporal.worker.Worker
import kotlinx.coroutines.runBlocking
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.postgresql.ds.PGSimpleDataSource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import dev.wrkflw.domain.identity.ActorId
import dev.wrkflw.domain.identity.GroupId
import dev.wrkflw.domain.port.ActorContext as DomainActorContext

@Testcontainers
class SubmitDocumentE2ETest {

    companion object {
        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("wrkflw_test")
            .withUsername("wrkflw")
            .withPassword("wrkflw")
    }

    private lateinit var testEnv: TestWorkflowEnvironment
    private lateinit var worker: Worker

    @BeforeEach
    fun setUp() {
        Flyway.configure()
            .dataSource(postgres.jdbcUrl, postgres.username, postgres.password)
            .locations("filesystem:${System.getProperty("wrkflw.migrations.dir")}")
            .load()
            .migrate()

        testEnv = TestWorkflowEnvironment.newInstance()
    }

    @AfterEach
    fun tearDown() {
        testEnv.close()

        val ds = dataSource()
        ds.connection.use { conn ->
            conn.createStatement().use { stmt ->
                stmt.execute(
                    """
                    TRUNCATE audit_entry, task, flow_instance RESTART IDENTITY CASCADE;
                    """.trimIndent()
                )
            }
        }
    }

    @Test
    fun `submit document creates flow instance, pending task for reviewer group, and audit trail`() {
        val ds = dataSource()
        val dslContext = JooqDslContextProvider(ds).create()

        val definitions = FlowDefinitionRepositoryPostgres(dslContext)
        val instances = FlowInstanceRepositoryPostgres(dslContext)
        val tasks = TaskRepositoryPostgres(dslContext)
        val auditLog = AuditLogPostgres(dslContext)

        val createTaskActivity = CreateHumanTaskActivityImpl(definitions, instances, tasks, auditLog, SystemClock)
        val advanceFlowActivity = AdvanceFlowActivityImpl(instances)

        val taskQueue = "wrkflw-task-queue"
        worker = testEnv.newWorker(taskQueue)
        worker.registerWorkflowImplementationTypes(DocumentApprovalWorkflowImpl::class.java)
        worker.registerActivitiesImplementations(createTaskActivity, advanceFlowActivity)
        testEnv.start()

        val engine = TemporalWorkflowEngine(testEnv.workflowClient, taskQueue)

        val service = SubmitDocumentService(definitions, instances, engine, auditLog, SystemClock)

        val actor = DomainActorContext(
            actorId = ActorId("author1"),
            groupIds = setOf(GroupId("authors")),
        )

        val result = runBlocking {
            service.execute(
                SubmitDocumentCommand(
                    definitionKey = "document-approval",
                    documentRef = "doc-ref-001",
                    actor = actor,
                )
            )
        }

        result shouldBe result.also { it is SubmitDocumentResult.Success }
        val instance = (result as SubmitDocumentResult.Success).instance

        // Let the workflow activity run synchronously inside the test env
        testEnv.sleep(java.time.Duration.ofMillis(500))

        // Flow instance was saved
        instance.definitionKey.value shouldBe "document-approval"
        instance.currentState shouldBe "Submitted"

        // Task was created by the activity
        val pendingTasks = runBlocking { tasks.findByFlowInstanceId(instance.id) }
        pendingTasks shouldHaveSize 1
        val task = pendingTasks.first()
        task.status shouldBe TaskStatus.PENDING
        task.candidateGroupId shouldBe GroupId("reviewers")
        task.stateName shouldBe "Submitted"

        // Audit trail has FLOW_STARTED + TASK_CREATED
        val auditEntries = runBlocking { auditLog.findByFlowInstanceId(instance.id) }
        auditEntries shouldHaveSize 2
        auditEntries.map { it.type.name } shouldBe listOf("FLOW_STARTED", "TASK_CREATED")
        auditEntries[1].taskId shouldNotBe null
    }

    private fun dataSource() = PGSimpleDataSource().apply {
        setURL(postgres.jdbcUrl)
        user = postgres.username
        password = postgres.password
    }
}
