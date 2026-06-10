package dev.wrkflw.domain.port

import dev.wrkflw.domain.identity.FlowInstanceId

interface WorkflowEngine {
    suspend fun startWorkflow(
        flowInstanceId: FlowInstanceId,
        definitionKey: String,
    )

    suspend fun signalWorkflow(
        flowInstanceId: FlowInstanceId,
        signalName: String,
        data: Any?,
    )
}
