package dev.wrkflw.eventing

import dev.wrkflw.domain.event.OutboxEvent
import dev.wrkflw.domain.event.OutboxStatus
import dev.wrkflw.domain.identity.FlowInstanceId
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class OutboxConsistencyTest {
    private val now = Instant.parse("2026-01-01T00:00:00Z")

    @Test
    fun `CloudEventsOutboxPublisher emits CloudEvent with correct type and subject`() {
        val received = mutableListOf<io.cloudevents.CloudEvent>()
        val publisher = CloudEventsOutboxPublisher { ce -> received.add(ce) }

        val flowId = "00000000-0000-0000-0000-000000000001"
        val event =
            OutboxEvent(
                id = UUID.randomUUID(),
                flowInstanceId = FlowInstanceId(UUID.fromString(flowId)),
                type = "dev.wrkflw.flow.started",
                data = """{"flowId":"$flowId","definitionKey":"doc","documentRef":"ref"}""",
                occurredAt = now,
            )

        runBlocking { publisher.publish(event) }

        received.size shouldBe 1
        received.first().type shouldBe "dev.wrkflw.flow.started"
        received.first().subject shouldBe "00000000-0000-0000-0000-000000000001"
        received.first().id shouldBe event.id.toString()
    }

    @Test
    fun `CloudEventsOutboxPublisher uses outbox event id as CloudEvent id for deduplication`() {
        val received = mutableListOf<io.cloudevents.CloudEvent>()
        val publisher = CloudEventsOutboxPublisher { ce -> received.add(ce) }
        val fixedId = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee")

        val event =
            OutboxEvent(
                id = fixedId,
                flowInstanceId = FlowInstanceId(UUID.randomUUID()),
                type = "dev.wrkflw.flow.completed",
                data = """{"flowId":"x","terminalOutcome":"APPROVED"}""",
                occurredAt = now,
            )

        runBlocking { publisher.publish(event) }

        received.first().id shouldBe fixedId.toString()
    }

    @Test
    fun `CloudEventsOutboxPublisher preserves JSON data payload`() {
        val received = mutableListOf<io.cloudevents.CloudEvent>()
        val publisher = CloudEventsOutboxPublisher { ce -> received.add(ce) }

        val data = """{"flowId":"abc","taskId":"def","ownerId":"reviewer1"}"""
        val event =
            OutboxEvent(
                flowInstanceId = FlowInstanceId(UUID.randomUUID()),
                type = "dev.wrkflw.task.claimed",
                data = data,
                occurredAt = now,
            )

        runBlocking { publisher.publish(event) }

        val payload =
            received
                .first()
                .data!!
                .toBytes()
                .decodeToString()
        payload shouldBe data
    }

    @Test
    fun `OutboxEvent has PENDING status by default`() {
        val event =
            OutboxEvent(
                flowInstanceId = FlowInstanceId(UUID.randomUUID()),
                type = "dev.wrkflw.flow.started",
                data = "{}",
                occurredAt = now,
            )
        event.status shouldBe OutboxStatus.PENDING
    }

    @Test
    fun `OutboxEvent has a unique id by default`() {
        val e1 =
            OutboxEvent(
                flowInstanceId = FlowInstanceId(UUID.randomUUID()),
                type = "t",
                data = "{}",
                occurredAt = now,
            )
        val e2 =
            OutboxEvent(
                flowInstanceId = FlowInstanceId(UUID.randomUUID()),
                type = "t",
                data = "{}",
                occurredAt = now,
            )
        e1.id shouldNotBe e2.id
    }
}
