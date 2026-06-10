package dev.wrkflw.temporal

import dev.wrkflw.temporal.activity.AdvanceFlowActivity
import dev.wrkflw.temporal.activity.CreateHumanTaskActivity
import io.temporal.activity.ActivityOptions
import io.temporal.workflow.SignalMethod
import io.temporal.workflow.Workflow
import io.temporal.workflow.WorkflowInterface
import io.temporal.workflow.WorkflowMethod
import java.time.Duration

@WorkflowInterface
interface DocumentApprovalWorkflow {

    @WorkflowMethod
    fun execute(flowInstanceId: String, definitionKey: String)

    @SignalMethod
    fun onDecisionSignal(outcome: String, taskId: String, actorId: String)
}

class DocumentApprovalWorkflowImpl : DocumentApprovalWorkflow {

    private val activityOptions = ActivityOptions.newBuilder()
        .setStartToCloseTimeout(Duration.ofSeconds(30))
        .build()

    private val createTask = Workflow.newActivityStub(CreateHumanTaskActivity::class.java, activityOptions)
    private val advanceFlow = Workflow.newActivityStub(AdvanceFlowActivity::class.java, activityOptions)

    private var pendingDecision: DecisionSignalData? = null

    override fun execute(flowInstanceId: String, definitionKey: String) {
        createTask.createForCurrentState(flowInstanceId)

        Workflow.await { pendingDecision != null }
        val signal = pendingDecision!!

        val isTerminal = advanceFlow.advanceState(flowInstanceId, signal.outcome)
        if (!isTerminal) {
            // Recurse: start a new execution for the next HUMAN_TASK state
            Workflow.continueAsNew(flowInstanceId, definitionKey)
        }
    }

    override fun onDecisionSignal(outcome: String, taskId: String, actorId: String) {
        pendingDecision = DecisionSignalData(outcome, taskId, actorId)
    }
}

data class DecisionSignalData(val outcome: String, val taskId: String, val actorId: String)
