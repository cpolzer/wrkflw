package dev.wrkflw.temporal

import dev.wrkflw.temporal.activity.AdvanceFlowActivity
import dev.wrkflw.temporal.activity.CreateHumanTaskActivity
import io.temporal.client.WorkflowClient
import io.temporal.serviceclient.WorkflowServiceStubs
import io.temporal.serviceclient.WorkflowServiceStubsOptions
import io.temporal.worker.Worker
import io.temporal.worker.WorkerFactory

class TemporalWorkerService(
    private val client: WorkflowClient,
    private val taskQueue: String = "wrkflw-task-queue",
    private val createHumanTaskActivity: CreateHumanTaskActivity,
    private val advanceFlowActivity: AdvanceFlowActivity,
) {
    fun start(): WorkerFactory {
        val factory = WorkerFactory.newInstance(client)
        val worker = factory.newWorker(taskQueue)
        registerWorkflows(worker)
        registerActivities(worker)
        factory.start()
        return factory
    }

    private fun registerWorkflows(worker: Worker) {
        worker.registerWorkflowImplementationTypes(DocumentApprovalWorkflowImpl::class.java)
    }

    private fun registerActivities(worker: Worker) {
        worker.registerActivitiesImplementations(createHumanTaskActivity, advanceFlowActivity)
    }

    companion object {
        fun createClient(host: String = "localhost", port: Int = 7233): WorkflowClient =
            WorkflowClient.newInstance(
                WorkflowServiceStubs.newInstance(
                    WorkflowServiceStubsOptions.newBuilder()
                        .setTarget("$host:$port")
                        .build()
                )
            )
    }
}
