package dev.wrkflw.eventing

import dev.wrkflw.domain.event.DecisionRecorded
import dev.wrkflw.domain.event.FlowCompleted
import dev.wrkflw.domain.event.FlowStarted
import dev.wrkflw.domain.event.TaskClaimed
import dev.wrkflw.domain.event.TaskCreated
import dev.wrkflw.domain.event.TaskReleased
import dev.wrkflw.domain.identity.ActorId
import dev.wrkflw.domain.identity.FlowInstanceId
import dev.wrkflw.domain.identity.TaskId
import dev.wrkflw.domain.task.DecisionOutcome
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test
import java.time.Instant

class CloudEventMappingTest {
    private val now = Instant.parse("2026-01-01T00:00:00Z")
    private val flowId = FlowInstanceId.generate()
    private val taskId = TaskId.generate()
    private val actor = ActorId("actor1")

    @Test
    fun `FlowStarted maps to dev-wrkflw-flow-started CloudEvent`() {
        val event =
            FlowStarted(
                flowInstanceId = flowId,
                definitionKey = "document-approval",
                documentRef = "doc-001",
                submitterId = actor,
                initialState = "Submitted",
                occurredAt = now,
            )
        val ce = event.toCloudEvent()

        ce.type shouldBe "dev.wrkflw.flow.started"
        ce.subject shouldBe flowId.value.toString()
        ce.source.toString() shouldBe "/wrkflw/document-approval"
        ce.id shouldNotBe null
        ce.dataContentType shouldBe "application/json"
        val data = ce.data!!.toBytes().decodeToString()
        data.contains("document-approval") shouldBe true
        data.contains("doc-001") shouldBe true
    }

    @Test
    fun `TaskCreated maps to dev-wrkflw-task-created CloudEvent`() {
        val event =
            TaskCreated(
                flowInstanceId = flowId,
                taskId = taskId,
                stateName = "Submitted",
                candidateGroupId = "reviewers",
                occurredAt = now,
            )
        val ce = event.toCloudEvent()

        ce.type shouldBe "dev.wrkflw.task.created"
        val data = ce.data!!.toBytes().decodeToString()
        data.contains("reviewers") shouldBe true
        data.contains("Submitted") shouldBe true
    }

    @Test
    fun `TaskClaimed maps to dev-wrkflw-task-claimed CloudEvent`() {
        val event = TaskClaimed(flowInstanceId = flowId, taskId = taskId, ownerId = actor, occurredAt = now)
        val ce = event.toCloudEvent()

        ce.type shouldBe "dev.wrkflw.task.claimed"
        val data = ce.data!!.toBytes().decodeToString()
        data.contains("actor1") shouldBe true
    }

    @Test
    fun `TaskReleased maps to dev-wrkflw-task-released CloudEvent`() {
        val event = TaskReleased(flowInstanceId = flowId, taskId = taskId, occurredAt = now)
        val ce = event.toCloudEvent()

        ce.type shouldBe "dev.wrkflw.task.released"
    }

    @Test
    fun `DecisionRecorded maps to dev-wrkflw-decision-recorded CloudEvent`() {
        val event =
            DecisionRecorded(
                flowInstanceId = flowId,
                taskId = taskId,
                outcome = DecisionOutcome.APPROVE,
                actorId = actor,
                comment = "looks good",
                occurredAt = now,
            )
        val ce = event.toCloudEvent()

        ce.type shouldBe "dev.wrkflw.decision.recorded"
        val data = ce.data!!.toBytes().decodeToString()
        data.contains("APPROVE") shouldBe true
        data.contains("looks good") shouldBe true
    }

    @Test
    fun `DecisionRecorded with null comment serializes comment as null`() {
        val event =
            DecisionRecorded(
                flowInstanceId = flowId,
                taskId = taskId,
                outcome = DecisionOutcome.REJECT,
                actorId = actor,
                comment = null,
                occurredAt = now,
            )
        val ce = event.toCloudEvent()

        val data = ce.data!!.toBytes().decodeToString()
        data.contains("\"comment\":null") shouldBe true
    }

    @Test
    fun `FlowCompleted maps to dev-wrkflw-flow-completed CloudEvent`() {
        val event = FlowCompleted(flowInstanceId = flowId, terminalOutcome = "APPROVED", occurredAt = now)
        val ce = event.toCloudEvent()

        ce.type shouldBe "dev.wrkflw.flow.completed"
        val data = ce.data!!.toBytes().decodeToString()
        data.contains("APPROVED") shouldBe true
    }

    @Test
    fun `each call to toCloudEvent produces a unique id`() {
        val event = FlowCompleted(flowInstanceId = flowId, terminalOutcome = "APPROVED", occurredAt = now)
        val id1 = event.toCloudEvent().id
        val id2 = event.toCloudEvent().id
        id1 shouldNotBe id2
    }

    @Test
    fun `CloudEventsPublisher calls sink with mapped event`() {
        var received: io.cloudevents.CloudEvent? = null
        val publisher =
            CloudEventsPublisher { ce ->
                received = ce
            }
        val event = FlowCompleted(flowInstanceId = flowId, terminalOutcome = "APPROVED", occurredAt = now)

        kotlinx.coroutines.runBlocking { publisher.publish(event) }

        received shouldNotBe null
        received!!.type shouldBe "dev.wrkflw.flow.completed"
    }
}
