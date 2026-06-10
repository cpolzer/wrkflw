package dev.wrkflw.temporal

import dev.wrkflw.domain.identity.FlowInstanceId
import dev.wrkflw.domain.port.WorkflowEngine
import io.temporal.client.WorkflowClient
import io.temporal.client.WorkflowOptions

class TemporalWorkflowEngine(
    private val client: WorkflowClient,
    private val taskQueue: String = "wrkflw-task-queue",
) : WorkflowEngine {
    override suspend fun startWorkflow(
        flowInstanceId: FlowInstanceId,
        definitionKey: String,
    ) {
        val options =
            WorkflowOptions
                .newBuilder()
                .setWorkflowId(flowInstanceId.value.toString())
                .setTaskQueue(taskQueue)
                .build()

        val workflow =
            client.newWorkflowStub(
                DocumentApprovalWorkflow::class.java,
                options,
            )

        WorkflowClient.start(workflow::execute, flowInstanceId.value.toString(), definitionKey)
    }

    override suspend fun signalWorkflow(
        flowInstanceId: FlowInstanceId,
        signalName: String,
        data: Any?,
    ) {
        val workflow =
            client.newWorkflowStub(
                DocumentApprovalWorkflow::class.java,
                flowInstanceId.value.toString(),
            )

        when (signalName) {
            "decision" -> {
                @Suppress("UNCHECKED_CAST")
                val signalData = data as? Map<*, *>
                workflow.onDecisionSignal(
                    signalData?.get("outcome") as String,
                    signalData?.get("taskId") as String,
                    signalData?.get("actorId") as String,
                )
            }
            else -> throw IllegalArgumentException("Unknown signal: $signalName")
        }
    }
}
