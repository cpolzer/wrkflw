package dev.wrkflw.domain.flow

import dev.wrkflw.domain.identity.ActorId
import dev.wrkflw.domain.identity.FlowDefinitionKey
import dev.wrkflw.domain.identity.FlowInstanceId
import java.time.Instant

enum class FlowStatus {
    RUNNING,
    COMPLETED
}

data class FlowInstance(
    val id: FlowInstanceId,
    val definitionKey: FlowDefinitionKey,
    val definitionVersion: Int,
    val documentRef: String,
    val submitterId: ActorId,
    val currentState: String,
    val status: FlowStatus,
    val terminalOutcome: String? = null,
    val createdAt: Instant,
    val updatedAt: Instant
) {
    companion object {
        fun start(
            id: FlowInstanceId,
            definitionKey: FlowDefinitionKey,
            definitionVersion: Int,
            documentRef: String,
            submitterId: ActorId,
            initialState: String,
            now: Instant
        ): FlowInstance = FlowInstance(
            id = id,
            definitionKey = definitionKey,
            definitionVersion = definitionVersion,
            documentRef = documentRef,
            submitterId = submitterId,
            currentState = initialState,
            status = FlowStatus.RUNNING,
            terminalOutcome = null,
            createdAt = now,
            updatedAt = now
        )
    }

    fun advance(newState: String, now: Instant): FlowInstance =
        copy(currentState = newState, updatedAt = now)

    fun complete(outcome: String, now: Instant): FlowInstance =
        copy(status = FlowStatus.COMPLETED, terminalOutcome = outcome, updatedAt = now)
}
