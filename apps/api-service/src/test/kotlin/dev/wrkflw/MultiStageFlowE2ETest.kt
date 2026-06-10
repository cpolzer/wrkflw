package dev.wrkflw

import dev.wrkflw.application.command.ClaimTaskCommand
import dev.wrkflw.application.command.ClaimTaskResult
import dev.wrkflw.application.command.ClaimTaskService
import dev.wrkflw.application.command.SubmitDecisionCommand
import dev.wrkflw.application.command.SubmitDecisionResult
import dev.wrkflw.application.command.SubmitDecisionService
import dev.wrkflw.application.command.SubmitDocumentCommand
import dev.wrkflw.application.command.SubmitDocumentResult
import dev.wrkflw.application.command.SubmitDocumentService
import dev.wrkflw.domain.audit.AuditEventType
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
class MultiStageFlowE2ETest {
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

    private lateinit var definitions: FlowDefinitionRepositoryPostgres
    private lateinit var instances: FlowInstanceRepositoryPostgres
    private lateinit var tasks: TaskRepositoryPostgres
    private lateinit var decisions: DecisionRepositoryPostgres
    private lateinit var auditLog: AuditLogPostgres

    private lateinit var submitDocument: SubmitDocumentService
    private lateinit var claimTask: ClaimTaskService
    private lateinit var submitDecision: SubmitDecisionService

    @BeforeEach
    fun setUp() {
        Flyway
            .configure()
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

    private fun dataSource() =
        PGSimpleDataSource().apply {
            setURL(postgres.jdbcUrl)
            user = postgres.username
            password = postgres.password
        }

    private fun authorActor() = ActorContext(ActorId("author1"), setOf(GroupId("authors")))

    private fun reviewerActor() = ActorContext(ActorId("reviewer1"), setOf(GroupId("reviewers")))

    private fun seniorReviewerActor() = ActorContext(ActorId("senior1"), setOf(GroupId("senior-reviewers")))

    /**
     * Full two-stage happy path:
     * Submitted →(approve)→ FinalReview →(approve)→ Approved[terminal]
     */
    @Test
    fun `two-stage approval path reaches Approved terminal with FLOW_COMPLETED audit`() {
        val instance =
            (
                runBlocking {
                    submitDocument.execute(SubmitDocumentCommand("document-approval", "doc-ms-001", authorActor()))
                } as SubmitDocumentResult.Success
            ).instance

        // Stage 1 task (Submitted → FinalReview)
        testEnv.sleep(java.time.Duration.ofMillis(500))
        val task1 = runBlocking { tasks.findByFlowInstanceId(instance.id) }.first { it.status == TaskStatus.PENDING }
        task1.stateName shouldBe "Submitted"

        runBlocking { claimTask.execute(ClaimTaskCommand(task1.id, reviewerActor())) }
            .shouldBeInstanceOf<ClaimTaskResult.Success>()

        val decision1 =
            runBlocking {
                submitDecision.execute(SubmitDecisionCommand(task1.id, DecisionOutcome.APPROVE, null, reviewerActor()))
            } as SubmitDecisionResult.Success
        decision1.flowInstance.currentState shouldBe "FinalReview"
        decision1.flowInstance.status shouldBe FlowStatus.RUNNING

        // Stage 2 task (FinalReview → Approved)
        testEnv.sleep(java.time.Duration.ofMillis(500))
        val task2 = runBlocking { tasks.findByFlowInstanceId(instance.id) }.first { it.status == TaskStatus.PENDING }
        task2.stateName shouldBe "FinalReview"

        runBlocking { claimTask.execute(ClaimTaskCommand(task2.id, seniorReviewerActor())) }
            .shouldBeInstanceOf<ClaimTaskResult.Success>()

        val decision2 =
            runBlocking {
                submitDecision.execute(
                    SubmitDecisionCommand(task2.id, DecisionOutcome.APPROVE, null, seniorReviewerActor()),
                )
            } as SubmitDecisionResult.Success
        decision2.flowInstance.status shouldBe FlowStatus.COMPLETED
        decision2.flowInstance.terminalOutcome shouldBe "APPROVED"

        // FLOW_COMPLETED audit entry must exist
        val auditTypes =
            runBlocking { auditLog.findByFlowInstanceId(instance.id) }.map { it.type }
        auditTypes.contains(AuditEventType.FLOW_COMPLETED) shouldBe true
    }

    /**
     * Rework cycle:
     * Submitted →(reject)→ ReworkRequested →(submit)→ Submitted →(approve)→ FinalReview →(approve)→ Approved
     */
    @Test
    fun `reject-rework-resubmit cycle eventually reaches Approved terminal`() {
        val instance =
            (
                runBlocking {
                    submitDocument.execute(SubmitDocumentCommand("document-approval", "doc-ms-002", authorActor()))
                } as SubmitDocumentResult.Success
            ).instance

        // Stage 1: reviewer rejects → ReworkRequested
        testEnv.sleep(java.time.Duration.ofMillis(500))
        val task1 = runBlocking { tasks.findByFlowInstanceId(instance.id) }.first { it.status == TaskStatus.PENDING }
        task1.stateName shouldBe "Submitted"
        runBlocking { claimTask.execute(ClaimTaskCommand(task1.id, reviewerActor())) }

        val reject =
            runBlocking {
                submitDecision.execute(
                    SubmitDecisionCommand(task1.id, DecisionOutcome.REJECT, "needs work", reviewerActor()),
                )
            } as SubmitDecisionResult.Success
        reject.flowInstance.currentState shouldBe "ReworkRequested"
        reject.flowInstance.status shouldBe FlowStatus.RUNNING

        // Rework: author claims and resubmits
        testEnv.sleep(java.time.Duration.ofMillis(500))
        val reworkTask =
            runBlocking { tasks.findByFlowInstanceId(instance.id) }.first { it.status == TaskStatus.PENDING }
        reworkTask.stateName shouldBe "ReworkRequested"
        runBlocking { claimTask.execute(ClaimTaskCommand(reworkTask.id, authorActor())) }

        val resubmit =
            runBlocking {
                submitDecision.execute(
                    SubmitDecisionCommand(reworkTask.id, DecisionOutcome.SUBMIT, null, authorActor()),
                )
            } as SubmitDecisionResult.Success
        resubmit.flowInstance.currentState shouldBe "Submitted"
        resubmit.flowInstance.status shouldBe FlowStatus.RUNNING

        // Back to stage 1: reviewer approves
        testEnv.sleep(java.time.Duration.ofMillis(500))
        val task3 = runBlocking { tasks.findByFlowInstanceId(instance.id) }.first { it.status == TaskStatus.PENDING }
        task3.stateName shouldBe "Submitted"
        runBlocking { claimTask.execute(ClaimTaskCommand(task3.id, reviewerActor())) }
        runBlocking {
            submitDecision.execute(SubmitDecisionCommand(task3.id, DecisionOutcome.APPROVE, null, reviewerActor()))
        }

        // Stage 2: senior reviewer approves → Approved
        testEnv.sleep(java.time.Duration.ofMillis(500))
        val task4 = runBlocking { tasks.findByFlowInstanceId(instance.id) }.first { it.status == TaskStatus.PENDING }
        task4.stateName shouldBe "FinalReview"
        runBlocking { claimTask.execute(ClaimTaskCommand(task4.id, seniorReviewerActor())) }

        val finalDecision =
            runBlocking {
                submitDecision.execute(
                    SubmitDecisionCommand(task4.id, DecisionOutcome.APPROVE, null, seniorReviewerActor()),
                )
            } as SubmitDecisionResult.Success
        finalDecision.flowInstance.status shouldBe FlowStatus.COMPLETED
        finalDecision.flowInstance.terminalOutcome shouldBe "APPROVED"
    }
}
