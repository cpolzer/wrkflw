package dev.wrkflw.temporal

import io.temporal.workflow.Workflow
import io.temporal.workflow.WorkflowMethod
import io.temporal.workflow.SignalMethod

interface DocumentApprovalWorkflow {

    @WorkflowMethod
    fun execute(flowInstanceId: String, definitionKey: String)

    @SignalMethod
    fun onDecisionSignal(outcome: String, taskId: String, actorId: String)
}

class DocumentApprovalWorkflowImpl : DocumentApprovalWorkflow {

    override fun execute(flowInstanceId: String, definitionKey: String) {
        Workflow.getLogger(DocumentApprovalWorkflowImpl::class.java)
            .info("Workflow started for flow=$flowInstanceId definition=$definitionKey")
    }

    override fun onDecisionSignal(outcome: String, taskId: String, actorId: String) {
        Workflow.getLogger(DocumentApprovalWorkflowImpl::class.java)
            .info("Decision signal received: outcome=$outcome taskId=$taskId actorId=$actorId")
    }
}
