package dev.wrkflw

import dev.wrkflw.application.command.SubmitDocumentService
import dev.wrkflw.application.command.SubmitDocumentUseCase
import dev.wrkflw.domain.port.AuditLog
import dev.wrkflw.domain.port.Clock
import dev.wrkflw.domain.port.FlowDefinitionRepository
import dev.wrkflw.domain.port.FlowInstanceRepository
import dev.wrkflw.domain.port.SystemClock
import dev.wrkflw.domain.port.TaskRepository
import dev.wrkflw.domain.port.WorkflowEngine
import dev.wrkflw.persistence.AuditLogPostgres
import dev.wrkflw.persistence.FlowDefinitionRepositoryPostgres
import dev.wrkflw.persistence.FlowInstanceRepositoryPostgres
import dev.wrkflw.persistence.JooqDslContextProvider
import dev.wrkflw.persistence.TaskRepositoryPostgres
import dev.wrkflw.temporal.TemporalWorkerService
import dev.wrkflw.temporal.TemporalWorkflowEngine
import io.temporal.client.WorkflowClient
import org.jooq.DSLContext
import org.koin.dsl.module
import org.postgresql.ds.PGSimpleDataSource

fun infraModule(
    dataSource: PGSimpleDataSource,
    temporalHost: String,
    temporalPort: Int,
    taskQueue: String,
) = module {
    single<Clock> { SystemClock }
    single { dataSource }
    single<DSLContext> { JooqDslContextProvider(dataSource).create() }
    single<AuditLog> { AuditLogPostgres(get()) }
    single<FlowDefinitionRepository> { FlowDefinitionRepositoryPostgres(get()) }
    single<FlowInstanceRepository> { FlowInstanceRepositoryPostgres(get()) }
    single<TaskRepository> { TaskRepositoryPostgres(get()) }
    single<WorkflowClient> { TemporalWorkerService.createClient(temporalHost, temporalPort) }
    single<WorkflowEngine> { TemporalWorkflowEngine(get(), taskQueue) }
    single<SubmitDocumentUseCase> { SubmitDocumentService(get(), get(), get(), get(), get()) }
}
