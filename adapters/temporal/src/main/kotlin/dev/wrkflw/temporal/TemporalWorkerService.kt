package dev.wrkflw.temporal

import io.temporal.client.WorkflowClient
import io.temporal.client.WorkflowClientOptions
import io.temporal.client.WorkflowOptions
import io.temporal.worker.Worker
import io.temporal.worker.WorkerFactory
import io.temporal.worker.WorkerOptions
import java.time.Duration

class TemporalWorkerService(
    private val client: WorkflowClient,
    private val taskQueue: String = "wrkflw-task-queue"
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
        // Activities registered during US1 implementation
    }

    companion object {
        fun createClient(host: String = "localhost", port: Int = 7233): WorkflowClient {
            return WorkflowClient.newInstance(
                io.temporal.serviceclient.WorkflowServiceStubs.newInstance(
                    io.temporal.serviceclient.WorkflowServiceStubsOptions.newBuilder()
                        .setTarget("$host:$port")
                        .build()
                )
            )
        }
    }
}
