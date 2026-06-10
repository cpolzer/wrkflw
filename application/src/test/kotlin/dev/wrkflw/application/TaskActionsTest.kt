package dev.wrkflw.application

import dev.wrkflw.application.command.ClaimTaskCommand
import dev.wrkflw.application.command.ClaimTaskResult
import dev.wrkflw.application.command.ClaimTaskService
import dev.wrkflw.application.command.ReleaseTaskCommand
import dev.wrkflw.application.command.ReleaseTaskResult
import dev.wrkflw.application.command.ReleaseTaskService
import dev.wrkflw.application.command.SubmitDecisionCommand
import dev.wrkflw.application.command.SubmitDecisionResult
import dev.wrkflw.application.command.SubmitDecisionService
import dev.wrkflw.domain.audit.AuditEntry
import dev.wrkflw.domain.audit.AuditEventType
import dev.wrkflw.domain.flow.FlowDefinition
import dev.wrkflw.domain.flow.FlowInstance
import dev.wrkflw.domain.flow.FlowStatus
import dev.wrkflw.domain.flow.StateDefinition
import dev.wrkflw.domain.flow.StateType
import dev.wrkflw.domain.flow.TransitionDefinition
import dev.wrkflw.domain.flow.Trigger
import dev.wrkflw.domain.identity.ActorId
import dev.wrkflw.domain.identity.FlowDefinitionKey
import dev.wrkflw.domain.identity.FlowInstanceId
import dev.wrkflw.domain.identity.GroupId
import dev.wrkflw.domain.identity.TaskId
import dev.wrkflw.domain.port.ActorContext
import dev.wrkflw.domain.port.AuditLog
import dev.wrkflw.domain.port.Clock
import dev.wrkflw.domain.port.DecisionRepository
import dev.wrkflw.domain.port.FlowDefinitionRepository
import dev.wrkflw.domain.port.FlowInstanceRepository
import dev.wrkflw.domain.port.TaskRepository
import dev.wrkflw.domain.port.WorkflowEngine
import dev.wrkflw.domain.task.Decision
import dev.wrkflw.domain.task.DecisionOutcome
import dev.wrkflw.domain.task.Task
import dev.wrkflw.domain.task.TaskStatus
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class TaskActionsTest {
    private val fixedNow = Instant.parse("2026-01-01T00:00:00Z")
    private val clock =
        object : Clock {
            override fun now() = fixedNow
        }

    private val reviewer1 = ActorId("reviewer1")
    private val reviewer2 = ActorId("reviewer2")
    private val reviewerGroup = GroupId("reviewers")
    private val authorGroup = GroupId("authors")

    private val flowInstanceId = FlowInstanceId(UUID.fromString("00000000-0000-0000-0000-000000000001"))
    private val taskId = TaskId(UUID.fromString("00000000-0000-0000-0000-000000000002"))

    private val baseTask =
        Task(
            id = taskId,
            flowInstanceId = flowInstanceId,
            stateName = "Submitted",
            candidateGroupId = reviewerGroup,
            status = TaskStatus.PENDING,
            ownerId = null,
            version = 0,
            createdAt = fixedNow,
        )

    private val claimedTask =
        baseTask.copy(
            status = TaskStatus.CLAIMED,
            ownerId = reviewer1,
            claimedAt = fixedNow,
            version = 1,
        )

    private val flowInstance =
        FlowInstance(
            id = flowInstanceId,
            definitionKey = FlowDefinitionKey("document-approval"),
            definitionVersion = 1,
            documentRef = "doc-001",
            submitterId = ActorId("author1"),
            currentState = "Submitted",
            status = FlowStatus.RUNNING,
            createdAt = fixedNow,
            updatedAt = fixedNow,
        )

    private val definition =
        FlowDefinition(
            key = FlowDefinitionKey("document-approval"),
            version = 1,
            initialState = "Submitted",
            initiatorGroupId = authorGroup,
            states =
                listOf(
                    StateDefinition("Submitted", StateType.HUMAN_TASK, candidateGroupId = reviewerGroup),
                    StateDefinition("Approved", StateType.TERMINAL, terminalOutcome = "APPROVED"),
                    StateDefinition("Rejected", StateType.TERMINAL, terminalOutcome = "REJECTED"),
                ),
            transitions =
                listOf(
                    TransitionDefinition("Submitted", Trigger.APPROVE, "Approved"),
                    TransitionDefinition("Submitted", Trigger.REJECT, "Rejected"),
                ),
        )

    // --- fakes ---

    private val taskStore = mutableMapOf<TaskId, Task>()
    private val decisionStore = mutableMapOf<TaskId, Decision>()
    private var instanceStore: FlowInstance = flowInstance
    private val auditEntries = mutableListOf<AuditEntry>()
    private val signals = mutableListOf<Triple<FlowInstanceId, String, Any?>>()

    private val fakeTasks =
        object : TaskRepository {
            override suspend fun findById(id: TaskId) = taskStore[id]

            override suspend fun findByFlowInstanceId(fid: FlowInstanceId) =
                taskStore.values.filter { it.flowInstanceId == fid }

            override suspend fun findPendingByCandidateGroup(groupId: String) = emptyList<Task>()

            override suspend fun findClaimedByOwner(ownerId: String) = emptyList<Task>()

            override suspend fun save(task: Task) {
                taskStore[task.id] = task
            }

            override suspend fun update(task: Task): Int {
                taskStore[task.id] = task
                return 1
            }

            override suspend fun updateConditional(
                task: Task,
                expectedStatus: TaskStatus,
                expectedVersion: Int,
            ): Int {
                val current = taskStore[task.id] ?: return 0
                if (current.status != expectedStatus || current.version != expectedVersion) return 0
                taskStore[task.id] = task
                return 1
            }
        }

    private val fakeDecisions =
        object : DecisionRepository {
            override suspend fun save(decision: Decision) {
                decisionStore[decision.taskId] = decision
            }

            override suspend fun findByTaskId(id: TaskId) = decisionStore[id]
        }

    private val fakeInstances =
        object : FlowInstanceRepository {
            override suspend fun findById(id: FlowInstanceId) = instanceStore.takeIf { it.id == id }

            override suspend fun save(instance: FlowInstance) {
                instanceStore = instance
            }

            override suspend fun update(instance: FlowInstance) {
                instanceStore = instance
            }
        }

    private val fakeDefinitions =
        object : FlowDefinitionRepository {
            override suspend fun findByKey(key: FlowDefinitionKey) = definition.takeIf { it.key == key }

            override suspend fun findByKeyAndVersion(
                key: FlowDefinitionKey,
                version: Int,
            ) = definition.takeIf { it.key == key && it.version == version }
        }

    private val fakeAudit =
        object : AuditLog {
            override suspend fun append(entry: AuditEntry) {
                auditEntries += entry
            }

            override suspend fun findByFlowInstanceId(fid: FlowInstanceId) =
                auditEntries.filter { it.flowInstanceId == fid }
        }

    private val fakeEngine =
        object : WorkflowEngine {
            override suspend fun startWorkflow(
                flowInstanceId: FlowInstanceId,
                definitionKey: String,
            ) {}

            override suspend fun signalWorkflow(
                flowInstanceId: FlowInstanceId,
                signalName: String,
                data: Any?,
            ) {
                signals += Triple(flowInstanceId, signalName, data)
            }
        }

    @BeforeEach
    fun setUp() {
        taskStore.clear()
        decisionStore.clear()
        instanceStore = flowInstance
        auditEntries.clear()
        signals.clear()
    }

    // ============================================================
    // ClaimTask
    // ============================================================

    @Nested
    inner class ClaimTaskTests {
        private val service = ClaimTaskService(fakeTasks, fakeAudit, clock)
        private val reviewerActor = ActorContext(reviewer1, setOf(reviewerGroup))
        private val authorActor = ActorContext(ActorId("author1"), setOf(authorGroup))

        @Test
        fun `claim succeeds when actor is in candidate group and task is PENDING`() =
            runTest {
                taskStore[taskId] = baseTask

                val result = service.execute(ClaimTaskCommand(taskId, reviewerActor))

                result.shouldBeInstanceOf<ClaimTaskResult.Success>()
                val claimed = (result as ClaimTaskResult.Success).task
                claimed.status shouldBe TaskStatus.CLAIMED
                claimed.ownerId shouldBe reviewer1
            }

        @Test
        fun `claim appends TASK_CLAIMED audit entry`() =
            runTest {
                taskStore[taskId] = baseTask

                service.execute(ClaimTaskCommand(taskId, reviewerActor))

                auditEntries shouldHaveSize 1
                auditEntries[0].type shouldBe AuditEventType.TASK_CLAIMED
                auditEntries[0].actorId shouldBe reviewer1
                auditEntries[0].taskId shouldBe taskId
            }

        @Test
        fun `claim returns NotFound when task does not exist`() =
            runTest {
                val result = service.execute(ClaimTaskCommand(taskId, reviewerActor))
                result shouldBe ClaimTaskResult.NotFound
            }

        @Test
        fun `claim returns Forbidden when actor is not in candidate group`() =
            runTest {
                taskStore[taskId] = baseTask

                val result = service.execute(ClaimTaskCommand(taskId, authorActor))
                result shouldBe ClaimTaskResult.Forbidden
            }

        @Test
        fun `claim returns Conflict when task is already CLAIMED`() =
            runTest {
                taskStore[taskId] = claimedTask

                val result = service.execute(ClaimTaskCommand(taskId, reviewerActor))
                result shouldBe ClaimTaskResult.Conflict
            }

        @Test
        fun `claim returns Conflict on optimistic lock failure`() =
            runTest {
                // Simulate stale read: store has version 1, but we read version 0
                taskStore[taskId] = baseTask.copy(version = 1)

                // Re-inject a fake that always returns version 0 but store has version 1
                val staleTasks =
                    object : TaskRepository by fakeTasks {
                        override suspend fun findById(id: TaskId) = baseTask // returns version 0
                    }
                val svc = ClaimTaskService(staleTasks, fakeAudit, clock)
                val result = svc.execute(ClaimTaskCommand(taskId, reviewerActor))
                result shouldBe ClaimTaskResult.Conflict
            }
    }

    // ============================================================
    // ReleaseTask
    // ============================================================

    @Nested
    inner class ReleaseTaskTests {
        private val service = ReleaseTaskService(fakeTasks, fakeAudit, clock)
        private val ownerActor = ActorContext(reviewer1, setOf(reviewerGroup))
        private val otherActor = ActorContext(reviewer2, setOf(reviewerGroup))

        @Test
        fun `release succeeds when actor is owner and task is CLAIMED`() =
            runTest {
                taskStore[taskId] = claimedTask

                val result = service.execute(ReleaseTaskCommand(taskId, ownerActor))

                result.shouldBeInstanceOf<ReleaseTaskResult.Success>()
                val released = (result as ReleaseTaskResult.Success).task
                released.status shouldBe TaskStatus.PENDING
                released.ownerId shouldBe null
            }

        @Test
        fun `release appends TASK_RELEASED audit entry`() =
            runTest {
                taskStore[taskId] = claimedTask

                service.execute(ReleaseTaskCommand(taskId, ownerActor))

                auditEntries shouldHaveSize 1
                auditEntries[0].type shouldBe AuditEventType.TASK_RELEASED
                auditEntries[0].actorId shouldBe reviewer1
            }

        @Test
        fun `release returns NotFound when task does not exist`() =
            runTest {
                val result = service.execute(ReleaseTaskCommand(taskId, ownerActor))
                result shouldBe ReleaseTaskResult.NotFound
            }

        @Test
        fun `release returns Forbidden when actor is not the owner`() =
            runTest {
                taskStore[taskId] = claimedTask

                val result = service.execute(ReleaseTaskCommand(taskId, otherActor))
                result shouldBe ReleaseTaskResult.Forbidden
            }

        @Test
        fun `release returns Conflict when task is PENDING`() =
            runTest {
                taskStore[taskId] = baseTask

                val result = service.execute(ReleaseTaskCommand(taskId, ownerActor))
                result shouldBe ReleaseTaskResult.Conflict
            }
    }

    // ============================================================
    // SubmitDecision
    // ============================================================

    @Nested
    inner class SubmitDecisionTests {
        private val service =
            SubmitDecisionService(
                fakeTasks,
                fakeDecisions,
                fakeInstances,
                fakeDefinitions,
                fakeAudit,
                fakeEngine,
                clock,
            )
        private val ownerActor = ActorContext(reviewer1, setOf(reviewerGroup))
        private val otherActor = ActorContext(reviewer2, setOf(reviewerGroup))

        @Test
        fun `approve advances flow instance to next state`() =
            runTest {
                taskStore[taskId] = claimedTask

                val result = service.execute(SubmitDecisionCommand(taskId, DecisionOutcome.APPROVE, null, ownerActor))

                result.shouldBeInstanceOf<SubmitDecisionResult.Success>()
                val instance = (result as SubmitDecisionResult.Success).flowInstance
                instance.status shouldBe FlowStatus.COMPLETED
                instance.terminalOutcome shouldBe "APPROVED"
            }

        @Test
        fun `reject advances flow instance to rejected terminal state`() =
            runTest {
                taskStore[taskId] = claimedTask

                val result = service.execute(SubmitDecisionCommand(taskId, DecisionOutcome.REJECT, null, ownerActor))

                result.shouldBeInstanceOf<SubmitDecisionResult.Success>()
                val instance = (result as SubmitDecisionResult.Success).flowInstance
                instance.status shouldBe FlowStatus.COMPLETED
                instance.terminalOutcome shouldBe "REJECTED"
            }

        @Test
        fun `decision saves the Decision record`() =
            runTest {
                taskStore[taskId] = claimedTask

                service.execute(SubmitDecisionCommand(taskId, DecisionOutcome.APPROVE, "LGTM", ownerActor))

                val decision = decisionStore[taskId]
                decision?.outcome shouldBe DecisionOutcome.APPROVE
                decision?.comment shouldBe "LGTM"
                decision?.actorId shouldBe reviewer1
            }

        @Test
        fun `decision writes DECISION_RECORDED and STATE_TRANSITIONED audit entries`() =
            runTest {
                taskStore[taskId] = claimedTask

                service.execute(SubmitDecisionCommand(taskId, DecisionOutcome.APPROVE, null, ownerActor))

                val types = auditEntries.map { it.type }
                types.contains(AuditEventType.DECISION_RECORDED) shouldBe true
                types.contains(AuditEventType.STATE_TRANSITIONED) shouldBe true
            }

        @Test
        fun `decision sends Temporal signal`() =
            runTest {
                taskStore[taskId] = claimedTask

                service.execute(SubmitDecisionCommand(taskId, DecisionOutcome.APPROVE, null, ownerActor))

                signals shouldHaveSize 1
                signals[0].first shouldBe flowInstanceId
                signals[0].second shouldBe "decision"
            }

        @Test
        fun `decision returns NotFound when task does not exist`() =
            runTest {
                val result = service.execute(SubmitDecisionCommand(taskId, DecisionOutcome.APPROVE, null, ownerActor))
                result shouldBe SubmitDecisionResult.NotFound
            }

        @Test
        fun `decision returns Forbidden when actor is not owner`() =
            runTest {
                taskStore[taskId] = claimedTask

                val result = service.execute(SubmitDecisionCommand(taskId, DecisionOutcome.APPROVE, null, otherActor))
                result shouldBe SubmitDecisionResult.Forbidden
            }

        @Test
        fun `decision returns Conflict when task is not CLAIMED`() =
            runTest {
                taskStore[taskId] = baseTask

                val result = service.execute(SubmitDecisionCommand(taskId, DecisionOutcome.APPROVE, null, ownerActor))
                result shouldBe SubmitDecisionResult.Conflict
            }

        @Test
        fun `decision marks task as COMPLETED`() =
            runTest {
                taskStore[taskId] = claimedTask

                service.execute(SubmitDecisionCommand(taskId, DecisionOutcome.APPROVE, null, ownerActor))

                taskStore[taskId]?.status shouldBe TaskStatus.COMPLETED
            }
    }
}
