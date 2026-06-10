package dev.wrkflw

import dev.wrkflw.application.command.ClaimTaskCommand
import dev.wrkflw.application.command.ClaimTaskService
import dev.wrkflw.application.command.SubmitDocumentCommand
import dev.wrkflw.application.command.SubmitDocumentResult
import dev.wrkflw.application.command.SubmitDocumentService
import dev.wrkflw.application.query.FlowStatusQuery
import dev.wrkflw.application.query.FlowStatusResult
import dev.wrkflw.application.query.FlowStatusService
import dev.wrkflw.application.query.GroupWorkListQuery
import dev.wrkflw.application.query.GroupWorkListService
import dev.wrkflw.application.query.MyTasksQuery
import dev.wrkflw.application.query.MyTasksService
import dev.wrkflw.domain.audit.AuditEventType
import dev.wrkflw.domain.identity.ActorId
import dev.wrkflw.domain.identity.GroupId
import dev.wrkflw.domain.port.ActorContext
import dev.wrkflw.domain.port.SystemClock
import dev.wrkflw.domain.task.TaskStatus
import dev.wrkflw.persistence.AuditLogPostgres
import dev.wrkflw.persistence.FlowDefinitionRepositoryPostgres
import dev.wrkflw.persistence.FlowInstanceRepositoryPostgres
import dev.wrkflw.persistence.JooqDslContextProvider
import dev.wrkflw.persistence.TaskRepositoryPostgres
import dev.wrkflw.temporal.DocumentApprovalWorkflowImpl
import dev.wrkflw.temporal.TemporalWorkflowEngine
import dev.wrkflw.temporal.activity.AdvanceFlowActivityImpl
import dev.wrkflw.temporal.activity.CreateHumanTaskActivityImpl
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
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

@Testcontainers
class WorkListAndStatusE2ETest {
    companion object {
        @Container
        @JvmStatic
        val postgres =
            PostgreSQLContainer("postgres:16-alpine")
                .withDatabaseName("wrkflw_test")
                .withUsername("wrkflw")
                .withPassword("wrkflw")
    }

    private lateinit var testEnv: TestWorkflowEnvironment
    private lateinit var worker: Worker

    private lateinit var submitDocument: SubmitDocumentService
    private lateinit var claimTask: ClaimTaskService
    private lateinit var groupWorkList: GroupWorkListService
    private lateinit var myTasks: MyTasksService
    private lateinit var flowStatus: FlowStatusService

    @BeforeEach
    fun setUp() {
        Flyway
            .configure()
            .dataSource(postgres.jdbcUrl, postgres.username, postgres.password)
            .locations("filesystem:${System.getProperty("wrkflw.migrations.dir")}")
            .load()
            .migrate()

        val dsl = JooqDslContextProvider(dataSource()).create()
        val definitions = FlowDefinitionRepositoryPostgres(dsl)
        val instances = FlowInstanceRepositoryPostgres(dsl)
        val tasks = TaskRepositoryPostgres(dsl)
        val auditLog = AuditLogPostgres(dsl)

        testEnv = TestWorkflowEnvironment.newInstance()

        val createTaskActivity = CreateHumanTaskActivityImpl(definitions, instances, tasks, auditLog, SystemClock)
        val advanceFlowActivity = AdvanceFlowActivityImpl(instances)

        val taskQueue = "wrkflw-task-queue"
        worker = testEnv.newWorker(taskQueue)
        worker.registerWorkflowImplementationTypes(DocumentApprovalWorkflowImpl::class.java)
        worker.registerActivitiesImplementations(createTaskActivity, advanceFlowActivity)
        testEnv.start()

        val engine = TemporalWorkflowEngine(testEnv.workflowClient, taskQueue)

        submitDocument = SubmitDocumentService(definitions, instances, engine, auditLog, SystemClock)
        claimTask = ClaimTaskService(tasks, auditLog, SystemClock)
        groupWorkList = GroupWorkListService(tasks)
        myTasks = MyTasksService(tasks)
        flowStatus = FlowStatusService(instances, tasks, auditLog)
    }

    @AfterEach
    fun tearDown() {
        testEnv.close()
        dataSource().connection.use { conn ->
            conn.createStatement().use { stmt ->
                stmt.execute("TRUNCATE decision, audit_entry, task, flow_instance RESTART IDENTITY CASCADE")
            }
        }
    }

    private fun dataSource() =
        PGSimpleDataSource().apply {
            setURL(postgres.jdbcUrl)
            user = postgres.username
            password = postgres.password
        }

    private fun authorActor() = ActorContext(ActorId("author1"), setOf(GroupId("authors")))

    private fun reviewerActor() = ActorContext(ActorId("reviewer1"), setOf(GroupId("reviewers")))

    private fun seniorActor() = ActorContext(ActorId("senior1"), setOf(GroupId("senior-reviewers")))

    @Test
    fun `group worklist returns only pending tasks for the caller's group`() {
        runBlocking {
            submitDocument.execute(SubmitDocumentCommand("document-approval", "doc-wl-001", authorActor()))
        }
        runBlocking {
            submitDocument.execute(SubmitDocumentCommand("document-approval", "doc-wl-002", authorActor()))
        }

        testEnv.sleep(java.time.Duration.ofMillis(500))

        val reviewerResult = runBlocking { groupWorkList.execute(GroupWorkListQuery(reviewerActor())) }
        reviewerResult.tasks.size shouldBe 2
        reviewerResult.tasks.all { it.status == TaskStatus.PENDING } shouldBe true
        reviewerResult.tasks.all { it.candidateGroupId.value == "reviewers" } shouldBe true

        val seniorResult = runBlocking { groupWorkList.execute(GroupWorkListQuery(seniorActor())) }
        seniorResult.tasks shouldHaveSize 0
    }

    @Test
    fun `my tasks returns only tasks claimed by the actor`() {
        runBlocking {
            submitDocument.execute(SubmitDocumentCommand("document-approval", "doc-mt-001", authorActor()))
        }

        testEnv.sleep(java.time.Duration.ofMillis(500))

        val pendingTask =
            runBlocking {
                groupWorkList
                    .execute(GroupWorkListQuery(reviewerActor()))
                    .tasks
                    .first()
            }

        runBlocking { claimTask.execute(ClaimTaskCommand(pendingTask.id, reviewerActor())) }

        val mine = runBlocking { myTasks.execute(MyTasksQuery(reviewerActor())) }
        mine.tasks shouldHaveSize 1
        mine.tasks.first().status shouldBe TaskStatus.CLAIMED
        mine.tasks
            .first()
            .ownerId
            ?.value shouldBe "reviewer1"

        val othersQueue = runBlocking { myTasks.execute(MyTasksQuery(seniorActor())) }
        othersQueue.tasks shouldHaveSize 0
    }

    @Test
    fun `flow status returns instance with pending task and ordered history`() {
        val instance =
            (
                runBlocking {
                    submitDocument.execute(SubmitDocumentCommand("document-approval", "doc-fs-001", authorActor()))
                } as SubmitDocumentResult.Success
            ).instance

        testEnv.sleep(java.time.Duration.ofMillis(500))

        val result = runBlocking { flowStatus.execute(FlowStatusQuery(instance.id)) }

        result.shouldBeInstanceOf<FlowStatusResult.Found>()
        result.instance.id shouldBe instance.id
        result.instance.currentState shouldBe "Submitted"
        result.pendingTasks shouldHaveSize 1
        result.pendingTasks.first().status shouldBe TaskStatus.PENDING
        result.history.map { it.type } shouldContain AuditEventType.FLOW_STARTED
        result.history.map { it.type } shouldContain AuditEventType.TASK_CREATED

        val types = result.history.map { it.type }
        val flowStartedIdx = types.indexOf(AuditEventType.FLOW_STARTED)
        val taskCreatedIdx = types.indexOf(AuditEventType.TASK_CREATED)
        (flowStartedIdx < taskCreatedIdx) shouldBe true
    }
}
