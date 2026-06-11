package dev.wrkflw.domain.event

fun DomainEvent.toOutboxEvent(): OutboxEvent =
    OutboxEvent(
        flowInstanceId = flowInstanceId,
        type = eventTypeName(),
        data = toDataJson(),
        occurredAt = occurredAt,
    )

private fun DomainEvent.eventTypeName(): String =
    when (this) {
        is FlowStarted -> "dev.wrkflw.flow.started"
        is TaskCreated -> "dev.wrkflw.task.created"
        is TaskClaimed -> "dev.wrkflw.task.claimed"
        is TaskReleased -> "dev.wrkflw.task.released"
        is DecisionRecorded -> "dev.wrkflw.decision.recorded"
        is FlowCompleted -> "dev.wrkflw.flow.completed"
    }

private fun DomainEvent.toDataJson(): String =
    when (this) {
        is FlowStarted -> {
            val escapedRef = documentRef.replace("\\", "\\\\").replace("\"", "\\\"")
            """{"flowId":"${flowInstanceId.value}","definitionKey":"$definitionKey",""" +
                """"documentRef":"$escapedRef","submitterId":"${submitterId.value}","initialState":"$initialState"}"""
        }

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
