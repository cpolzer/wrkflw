package dev.wrkflw.eventing

import dev.wrkflw.domain.event.DecisionRecorded
import dev.wrkflw.domain.event.DomainEvent
import dev.wrkflw.domain.event.FlowCompleted
import dev.wrkflw.domain.event.FlowStarted
import dev.wrkflw.domain.event.TaskClaimed
import dev.wrkflw.domain.event.TaskCreated
import dev.wrkflw.domain.event.TaskReleased
import dev.wrkflw.domain.port.DomainEventPublisher
import io.cloudevents.CloudEvent
import io.cloudevents.core.builder.CloudEventBuilder
import java.net.URI
import java.nio.charset.StandardCharsets

class CloudEventsPublisher(
    private val sink: suspend (CloudEvent) -> Unit,
) : DomainEventPublisher {
    override suspend fun publish(event: DomainEvent) {
        val ce = event.toCloudEvent()
        sink(ce)
    }
}

private val SOURCE = URI.create("/wrkflw/document-approval")

fun DomainEvent.toCloudEvent(): CloudEvent =
    CloudEventBuilder
        .v1()
        .withId(
            java.util.UUID
                .randomUUID()
                .toString(),
        ).withSource(SOURCE)
        .withType(eventType())
        .withSubject(flowInstanceId.value.toString())
        .withTime(java.time.OffsetDateTime.ofInstant(occurredAt, java.time.ZoneOffset.UTC))
        .withDataContentType("application/json")
        .withData(toJsonPayload().toByteArray(StandardCharsets.UTF_8))
        .build()

private fun DomainEvent.eventType(): String =
    when (this) {
        is FlowStarted -> "dev.wrkflw.flow.started"
        is TaskCreated -> "dev.wrkflw.task.created"
        is TaskClaimed -> "dev.wrkflw.task.claimed"
        is TaskReleased -> "dev.wrkflw.task.released"
        is DecisionRecorded -> "dev.wrkflw.decision.recorded"
        is FlowCompleted -> "dev.wrkflw.flow.completed"
    }

private fun DomainEvent.toJsonPayload(): String =
    when (this) {
        is FlowStarted ->
            """{"flowId":"${flowInstanceId.value}","definitionKey":"$definitionKey",""" +
                """"documentRef":"$documentRef","submitterId":"${submitterId.value}","initialState":"$initialState"}"""

        is TaskCreated ->
            """{"flowId":"${flowInstanceId.value}","taskId":"${taskId.value}",""" +
                """"stateName":"$stateName","candidateGroupId":"$candidateGroupId"}"""

        is TaskClaimed ->
            """{"flowId":"${flowInstanceId.value}","taskId":"${taskId.value}","ownerId":"${ownerId.value}"}"""

        is TaskReleased ->
            """{"flowId":"${flowInstanceId.value}","taskId":"${taskId.value}"}"""

        is DecisionRecorded -> {
            val commentJson = if (comment != null) """"$comment"""" else "null"
            """{"flowId":"${flowInstanceId.value}","taskId":"${taskId.value}",""" +
                """"outcome":"${outcome.name}","actorId":"${actorId.value}","comment":$commentJson}"""
        }

        is FlowCompleted ->
            """{"flowId":"${flowInstanceId.value}","terminalOutcome":"$terminalOutcome"}"""
    }
