package dev.wrkflw.temporal

import dev.wrkflw.temporal.activity.CreateHumanTaskActivity
import io.temporal.activity.ActivityOptions
import io.temporal.workflow.SignalMethod
import io.temporal.workflow.Workflow
import io.temporal.workflow.WorkflowMethod
import java.time.Duration

interface DocumentApprovalWorkflow {

    @WorkflowMethod
    fun execute(flowInstanceId: String, definitionKey: String)

    @SignalMethod
    fun onDecisionSignal(outcome: String, taskId: String, actorId: String)
}

class DocumentApprovalWorkflowImpl : DocumentApprovalWorkflow {

    private val activity = Workflow.newActivityStub(
        CreateHumanTaskActivity::class.java,
        ActivityOptions.newBuilder()
            .setStartToCloseTimeout(Duration.ofSeconds(30))
            .build()
    )

    private var decisionReceived = false

    override fun execute(flowInstanceId: String, definitionKey: String) {
        activity.createForCurrentState(flowInstanceId)
        Workflow.await { decisionReceived }
    }

    override fun onDecisionSignal(outcome: String, taskId: String, actorId: String) {
        decisionReceived = true
    }
}
