package dev.wrkflw

import dev.wrkflw.application.command.ClaimTaskCommand
import dev.wrkflw.application.command.ClaimTaskResult
import dev.wrkflw.application.command.ClaimTaskService
import dev.wrkflw.application.command.ReleaseTaskCommand
import dev.wrkflw.application.command.ReleaseTaskResult
import dev.wrkflw.application.command.ReleaseTaskService
import dev.wrkflw.application.command.SubmitDecisionCommand
import dev.wrkflw.application.command.SubmitDecisionResult
import dev.wrkflw.application.command.SubmitDecisionService
import dev.wrkflw.application.command.SubmitDocumentCommand
import dev.wrkflw.application.command.SubmitDocumentResult
import dev.wrkflw.application.command.SubmitDocumentService
import dev.wrkflw.domain.flow.FlowStatus
import dev.wrkflw.domain.identity.ActorId
import dev.wrkflw.domain.identity.GroupId
import dev.wrkflw.domain.port.ActorContext
import dev.wrkflw.domain.port.SystemClock
import dev.wrkflw.domain.task.DecisionOutcome
import dev.wrkflw.domain.task.TaskStatus
import dev.wrkflw.persistence.AuditLogPostgres
import dev.wrkflw.persistence.DecisionRepositoryPostgres
import dev.wrkflw.persistence.FlowDefinitionRepositoryPostgres
import dev.wrkflw.persistence.FlowInstanceRepositoryPostgres
import dev.wrkflw.persistence.JooqDslContextProvider
import dev.wrkflw.persistence.TaskRepositoryPostgres
import dev.wrkflw.temporal.DocumentApprovalWorkflowImpl
import dev.wrkflw.temporal.TemporalWorkflowEngine
import dev.wrkflw.temporal.activity.AdvanceFlowActivityImpl
import dev.wrkflw.temporal.activity.CreateHumanTaskActivityImpl
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
class ClaimDecideE2ETest {

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

    private lateinit var definitions: FlowDefinitionRepositoryPostgres
    private lateinit var instances: FlowInstanceRepositoryPostgres
    private lateinit var tasks: TaskRepositoryPostgres
    private lateinit var decisions: DecisionRepositoryPostgres
    private lateinit var auditLog: AuditLogPostgres

    private lateinit var submitDocument: SubmitDocumentService
    private lateinit var claimTask: ClaimTaskService
    private lateinit var releaseTask: ReleaseTaskService
    private lateinit var submitDecision: SubmitDecisionService

    @BeforeEach
    fun setUp() {
        Flyway.configure()
            .dataSource(postgres.jdbcUrl, postgres.username, postgres.password)
            .locations("filesystem:${System.getProperty("wrkflw.migrations.dir")}")
            .load()
            .migrate()

        val dsl = JooqDslContextProvider(dataSource()).create()
        definitions = FlowDefinitionRepositoryPostgres(dsl)
        instances = FlowInstanceRepositoryPostgres(dsl)
        tasks = TaskRepositoryPostgres(dsl)
        decisions = DecisionRepositoryPostgres(dsl)
        auditLog = AuditLogPostgres(dsl)

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
        releaseTask = ReleaseTaskService(tasks, auditLog, SystemClock)
        submitDecision = SubmitDecisionService(tasks, decisions, instances, definitions, auditLog, engine, SystemClock)
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

    private fun dataSource() = PGSimpleDataSource().apply {
        setURL(postgres.jdbcUrl)
        user = postgres.username
        password = postgres.password
    }

    private fun authorActor() = ActorContext(ActorId("author1"), setOf(GroupId("authors")))
    private fun reviewerActor(id: String = "reviewer1") = ActorContext(ActorId(id), setOf(GroupId("reviewers")))
    private fun seniorReviewerActor() = ActorContext(ActorId("senior1"), setOf(GroupId("senior-reviewers")))

    @Test
    fun `claim then approve advances flow to completed`() {
        // The seed flow: Submitted → (APPROVE) → FinalReview → (APPROVE) → Approved[terminal]
        val submitResult = runBlocking {
            submitDocument.execute(SubmitDocumentCommand("document-approval", "doc-001", authorActor()))
        }
        submitResult.shouldBeInstanceOf<SubmitDocumentResult.Success>()
        val instance = (submitResult as SubmitDocumentResult.Success).instance

        // Stage 1: Submitted → FinalReview
        testEnv.sleep(java.time.Duration.ofMillis(500))
        val stage1Tasks = runBlocking { tasks.findByFlowInstanceId(instance.id) }
        stage1Tasks shouldHaveSize 1
        val task1 = stage1Tasks.first()

        runBlocking { claimTask.execute(ClaimTaskCommand(task1.id, reviewerActor())) }
            .shouldBeInstanceOf<ClaimTaskResult.Success>()

        val decision1 = runBlocking {
            submitDecision.execute(SubmitDecisionCommand(task1.id, DecisionOutcome.APPROVE, "Looks good", reviewerActor()))
        }
        decision1.shouldBeInstanceOf<SubmitDecisionResult.Success>()
        (decision1 as SubmitDecisionResult.Success).flowInstance.currentState shouldBe "FinalReview"

        // Stage 2: FinalReview → Approved (terminal) — continueAsNew creates a new task
        testEnv.sleep(java.time.Duration.ofMillis(500))
        val allTasks = runBlocking { tasks.findByFlowInstanceId(instance.id) }
        val task2 = allTasks.first { it.status == TaskStatus.PENDING }

        runBlocking { claimTask.execute(ClaimTaskCommand(task2.id, seniorReviewerActor())) }
            .shouldBeInstanceOf<ClaimTaskResult.Success>()

        val decision2 = runBlocking {
            submitDecision.execute(SubmitDecisionCommand(task2.id, DecisionOutcome.APPROVE, "Final approval", seniorReviewerActor()))
        }
        decision2.shouldBeInstanceOf<SubmitDecisionResult.Success>()
        val finalInstance = (decision2 as SubmitDecisionResult.Success).flowInstance
        finalInstance.status shouldBe FlowStatus.COMPLETED
        finalInstance.terminalOutcome shouldBe "APPROVED"

        val finalTask1 = runBlocking { tasks.findById(task1.id)!! }
        finalTask1.status shouldBe TaskStatus.COMPLETED

        val savedDecision = runBlocking { decisions.findByTaskId(task1.id)!! }
        savedDecision.outcome shouldBe DecisionOutcome.APPROVE
        savedDecision.comment shouldBe "Looks good"
    }

    @Test
    fun `non-owner attempting to decide returns Forbidden`() {
        val submitResult = runBlocking {
            submitDocument.execute(SubmitDocumentCommand("document-approval", "doc-002", authorActor()))
        }
        val instance = (submitResult as SubmitDocumentResult.Success).instance
        testEnv.sleep(java.time.Duration.ofMillis(500))

        val task = runBlocking { tasks.findByFlowInstanceId(instance.id) }.first()

        // reviewer1 claims it
        runBlocking { claimTask.execute(ClaimTaskCommand(task.id, reviewerActor("reviewer1"))) }

        // reviewer2 tries to decide
        val result = runBlocking {
            submitDecision.execute(SubmitDecisionCommand(task.id, DecisionOutcome.APPROVE, null, reviewerActor("reviewer2")))
        }
        result shouldBe SubmitDecisionResult.Forbidden
    }

    @Test
    fun `deciding on already-completed task returns Conflict`() {
        val submitResult = runBlocking {
            submitDocument.execute(SubmitDocumentCommand("document-approval", "doc-003", authorActor()))
        }
        val instance = (submitResult as SubmitDocumentResult.Success).instance
        testEnv.sleep(java.time.Duration.ofMillis(500))

        val task = runBlocking { tasks.findByFlowInstanceId(instance.id) }.first()
        runBlocking { claimTask.execute(ClaimTaskCommand(task.id, reviewerActor())) }
        runBlocking { submitDecision.execute(SubmitDecisionCommand(task.id, DecisionOutcome.APPROVE, null, reviewerActor())) }

        val secondDecision = runBlocking {
            submitDecision.execute(SubmitDecisionCommand(task.id, DecisionOutcome.REJECT, null, reviewerActor()))
        }
        secondDecision shouldBe SubmitDecisionResult.Conflict
    }

    @Test
    fun `release returns task to PENDING and allows another reviewer to claim`() {
        val submitResult = runBlocking {
            submitDocument.execute(SubmitDocumentCommand("document-approval", "doc-004", authorActor()))
        }
        val instance = (submitResult as SubmitDocumentResult.Success).instance
        testEnv.sleep(java.time.Duration.ofMillis(500))

        val task = runBlocking { tasks.findByFlowInstanceId(instance.id) }.first()
        runBlocking { claimTask.execute(ClaimTaskCommand(task.id, reviewerActor("reviewer1"))) }

        val releaseResult = runBlocking { releaseTask.execute(ReleaseTaskCommand(task.id, reviewerActor("reviewer1"))) }
        releaseResult.shouldBeInstanceOf<ReleaseTaskResult.Success>()

        val claimResult2 = runBlocking { claimTask.execute(ClaimTaskCommand(task.id, reviewerActor("reviewer2"))) }
        claimResult2.shouldBeInstanceOf<ClaimTaskResult.Success>()
        (claimResult2 as ClaimTaskResult.Success).task.ownerId shouldBe ActorId("reviewer2")
    }
}
