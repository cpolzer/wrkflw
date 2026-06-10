package dev.wrkflw.rest

import dev.wrkflw.domain.flow.FlowInstance
import dev.wrkflw.domain.task.Task
import kotlinx.serialization.Serializable

@Serializable
data class SubmitDocumentRequestDto(
    val definitionKey: String,
    val documentRef: String,
)

@Serializable
data class TaskSummaryDto(
    val taskId: String,
    val flowId: String,
    val stateName: String,
    val candidateGroupId: String,
    val status: String,
    val ownerId: String?,
)

@Serializable
data class FlowStatusResponseDto(
    val flowId: String,
    val definitionKey: String,
    val currentState: String,
    val status: String,
    val terminalOutcome: String?,
    val pendingTasks: List<TaskSummaryDto>,
)

@Serializable
data class DecisionRequestDto(
    val outcome: String,
    val comment: String? = null,
)

@Serializable
data class ErrorDto(
    val error: String,
)

fun FlowInstance.toStatusDto(tasks: List<Task> = emptyList()) =
    FlowStatusResponseDto(
        flowId = id.value.toString(),
        definitionKey = definitionKey.value,
        currentState = currentState,
        status = status.name,
        terminalOutcome = terminalOutcome,
        pendingTasks = tasks.map { it.toSummaryDto() },
    )

fun Task.toSummaryDto() =
    TaskSummaryDto(
        taskId = id.value.toString(),
        flowId = flowInstanceId.value.toString(),
        stateName = stateName,
        candidateGroupId = candidateGroupId.value,
        status = status.name,
        ownerId = ownerId?.value,
    )
