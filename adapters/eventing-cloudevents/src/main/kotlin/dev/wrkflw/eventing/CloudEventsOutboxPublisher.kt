package dev.wrkflw.eventing

import dev.wrkflw.domain.event.OutboxEvent
import dev.wrkflw.domain.port.OutboxPublisher
import io.cloudevents.CloudEvent
import io.cloudevents.core.builder.CloudEventBuilder
import java.net.URI
import java.nio.charset.StandardCharsets
import java.time.ZoneOffset

class CloudEventsOutboxPublisher(
    private val sink: suspend (CloudEvent) -> Unit,
) : OutboxPublisher {
    override suspend fun publish(event: OutboxEvent) {
        val ce =
            CloudEventBuilder
                .v1()
                .withId(event.id.toString())
                .withSource(URI.create("/wrkflw/document-approval"))
                .withType(event.type)
                .withSubject(event.flowInstanceId.value.toString())
                .withTime(event.occurredAt.atOffset(ZoneOffset.UTC))
                .withDataContentType("application/json")
                .withData(event.data.toByteArray(StandardCharsets.UTF_8))
                .build()
        sink(ce)
    }
}
