package dev.wrkflw.application

import dev.wrkflw.application.query.FlowStatusQuery
import dev.wrkflw.application.query.FlowStatusResult
import dev.wrkflw.application.query.FlowStatusService
import dev.wrkflw.application.query.GroupWorkListQuery
import dev.wrkflw.application.query.GroupWorkListService
import dev.wrkflw.application.query.MyTasksQuery
import dev.wrkflw.application.query.MyTasksService
import dev.wrkflw.domain.audit.AuditEntry
import dev.wrkflw.domain.audit.AuditEventType
import dev.wrkflw.domain.flow.FlowInstance
import dev.wrkflw.domain.flow.FlowStatus
import dev.wrkflw.domain.identity.ActorId
import dev.wrkflw.domain.identity.FlowDefinitionKey
import dev.wrkflw.domain.identity.FlowInstanceId
import dev.wrkflw.domain.identity.GroupId
import dev.wrkflw.domain.identity.TaskId
import dev.wrkflw.domain.port.ActorContext
import dev.wrkflw.domain.port.AuditLog
import dev.wrkflw.domain.port.FlowInstanceRepository
import dev.wrkflw.domain.port.TaskRepository
import dev.wrkflw.domain.task.Task
import dev.wrkflw.domain.task.TaskStatus
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.Instant

class QueriesTest {
    private val now = Instant.parse("2026-01-01T00:00:00Z")

    private val reviewersGroup = GroupId("reviewers")
    private val seniorGroup = GroupId("senior-reviewers")
    private val reviewer = ActorContext(ActorId("reviewer1"), setOf(reviewersGroup))
    private val senior = ActorContext(ActorId("senior1"), setOf(seniorGroup))
    private val multiGroup = ActorContext(ActorId("multi"), setOf(reviewersGroup, seniorGroup))

    private val instanceId = FlowInstanceId.generate()
    private val instance =
        FlowInstance(
            id = instanceId,
            definitionKey = FlowDefinitionKey("doc-approval"),
            definitionVersion = 1,
            documentRef = "doc-001",
            submitterId = ActorId("author1"),
            currentState = "Submitted",
            status = FlowStatus.RUNNING,
            createdAt = now,
            updatedAt = now,
        )

    private fun pendingTask(
        taskId: TaskId = TaskId.generate(),
        instanceId: FlowInstanceId = this.instanceId,
        state: String = "Submitted",
        group: GroupId = reviewersGroup,
    ) = Task(
        id = taskId,
        flowInstanceId = instanceId,
        stateName = state,
        candidateGroupId = group,
        status = TaskStatus.PENDING,
        createdAt = now,
    )

    private fun claimedTask(
        owner: ActorId,
        taskId: TaskId = TaskId.generate(),
        instanceId: FlowInstanceId = this.instanceId,
    ) = Task(
        id = taskId,
        flowInstanceId = instanceId,
        stateName = "Submitted",
        candidateGroupId = reviewersGroup,
        status = TaskStatus.CLAIMED,
        ownerId = owner,
        createdAt = now,
    )

    private fun fakeInstances(vararg instances: FlowInstance) =
        object : FlowInstanceRepository {
            override suspend fun findById(id: FlowInstanceId) = instances.find { it.id == id }

            override suspend fun save(instance: FlowInstance) {}

            override suspend fun update(instance: FlowInstance) {}
        }

    private fun fakeTasks(vararg tasks: Task) =
        object : TaskRepository {
            override suspend fun findById(id: TaskId) = tasks.find { it.id == id }

            override suspend fun findByFlowInstanceId(flowInstanceId: FlowInstanceId) =
                tasks.filter { it.flowInstanceId == flowInstanceId }

            override suspend fun findPendingByCandidateGroup(groupId: String) =
                tasks.filter {
                    it.candidateGroupId.value == groupId && it.status == TaskStatus.PENDING
                }

            override suspend fun findClaimedByOwner(ownerId: String) =
                tasks.filter {
                    it.ownerId?.value == ownerId && it.status == TaskStatus.CLAIMED
                }

            override suspend fun save(task: Task) {}

            override suspend fun update(task: Task) = 1

            override suspend fun updateConditional(
                task: Task,
                expectedStatus: TaskStatus,
                expectedVersion: Int,
            ) = 1
        }

    private fun fakeAuditLog(vararg entries: AuditEntry) =
        object : AuditLog {
            override suspend fun append(entry: AuditEntry) {}

            override suspend fun findByFlowInstanceId(flowInstanceId: FlowInstanceId) =
                entries.filter { it.flowInstanceId == flowInstanceId }
        }

    // GroupWorkList tests

    @Test
    fun `group worklist returns pending tasks for actor group`() =
        runTest {
            val task = pendingTask(group = reviewersGroup)
            val service = GroupWorkListService(fakeTasks(task))

            val result = service.execute(GroupWorkListQuery(reviewer))

            result.tasks shouldHaveSize 1
            result.tasks.first().id shouldBe task.id
        }

    @Test
    fun `group worklist excludes tasks for other groups`() =
        runTest {
            val reviewerTask = pendingTask(group = reviewersGroup)
            val seniorTask = pendingTask(group = seniorGroup)
            val service = GroupWorkListService(fakeTasks(reviewerTask, seniorTask))

            val result = service.execute(GroupWorkListQuery(reviewer))

            result.tasks shouldHaveSize 1
            result.tasks.first().id shouldBe reviewerTask.id
        }

    @Test
    fun `group worklist merges tasks from multiple groups without duplicates`() =
        runTest {
            val t1 = pendingTask(group = reviewersGroup)
            val t2 = pendingTask(group = seniorGroup)
            val service = GroupWorkListService(fakeTasks(t1, t2))

            val result = service.execute(GroupWorkListQuery(multiGroup))

            result.tasks.map { it.id } shouldContainExactlyInAnyOrder listOf(t1.id, t2.id)
        }

    @Test
    fun `group worklist excludes claimed tasks`() =
        runTest {
            val claimed = claimedTask(ActorId("someone"))
            val service = GroupWorkListService(fakeTasks(claimed))

            val result = service.execute(GroupWorkListQuery(reviewer))

            result.tasks shouldHaveSize 0
        }

    // MyTasks tests

    @Test
    fun `my tasks returns claimed tasks for the actor`() =
        runTest {
            val mine = claimedTask(reviewer.actorId)
            val othersTask = claimedTask(senior.actorId)
            val service = MyTasksService(fakeTasks(mine, othersTask))

            val result = service.execute(MyTasksQuery(reviewer))

            result.tasks shouldHaveSize 1
            result.tasks.first().id shouldBe mine.id
        }

    @Test
    fun `my tasks returns empty when actor has no claimed tasks`() =
        runTest {
            val service = MyTasksService(fakeTasks())

            val result = service.execute(MyTasksQuery(reviewer))

            result.tasks shouldHaveSize 0
        }

    // FlowStatus tests

    @Test
    fun `flow status returns instance with pending tasks and history`() =
        runTest {
            val task = pendingTask()
            val entry =
                AuditEntry(
                    flowInstanceId = instanceId,
                    type = AuditEventType.FLOW_STARTED,
                    occurredAt = now,
                )
            val service =
                FlowStatusService(
                    fakeInstances(instance),
                    fakeTasks(task),
                    fakeAuditLog(entry),
                )

            val result = service.execute(FlowStatusQuery(instanceId))

            result.shouldBeInstanceOf<FlowStatusResult.Found>()
            result.instance.id shouldBe instanceId
            result.pendingTasks shouldHaveSize 1
            result.history shouldHaveSize 1
        }

    @Test
    fun `flow status returns NotFound for unknown id`() =
        runTest {
            val service = FlowStatusService(fakeInstances(), fakeTasks(), fakeAuditLog())

            val result = service.execute(FlowStatusQuery(FlowInstanceId.generate()))

            result shouldBe FlowStatusResult.NotFound
        }

    @Test
    fun `flow status history is sorted by occurredAt`() =
        runTest {
            val t2 = now.plusSeconds(1)
            val entry1 = AuditEntry(flowInstanceId = instanceId, type = AuditEventType.FLOW_STARTED, occurredAt = now)
            val entry2 = AuditEntry(flowInstanceId = instanceId, type = AuditEventType.TASK_CREATED, occurredAt = t2)
            val service =
                FlowStatusService(
                    fakeInstances(instance),
                    fakeTasks(),
                    fakeAuditLog(entry2, entry1),
                )

            val result = service.execute(FlowStatusQuery(instanceId)) as FlowStatusResult.Found

            result.history.first().type shouldBe AuditEventType.FLOW_STARTED
            result.history.last().type shouldBe AuditEventType.TASK_CREATED
        }

    @Test
    fun `flow status excludes completed tasks from pendingTasks`() =
        runTest {
            val pending = pendingTask()
            val completed =
                pending.copy(
                    id = TaskId.generate(),
                    status = TaskStatus.COMPLETED,
                )
            val service =
                FlowStatusService(
                    fakeInstances(instance),
                    fakeTasks(pending, completed),
                    fakeAuditLog(),
                )

            val result = service.execute(FlowStatusQuery(instanceId)) as FlowStatusResult.Found

            result.pendingTasks shouldHaveSize 1
            result.pendingTasks.first().id shouldBe pending.id
        }
}
