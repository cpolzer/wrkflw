package dev.wrkflw

import dev.wrkflw.application.command.ClaimTaskService
import dev.wrkflw.application.command.ClaimTaskUseCase
import dev.wrkflw.application.command.ReleaseTaskService
import dev.wrkflw.application.command.ReleaseTaskUseCase
import dev.wrkflw.application.command.SubmitDecisionService
import dev.wrkflw.application.command.SubmitDecisionUseCase
import dev.wrkflw.application.command.SubmitDocumentService
import dev.wrkflw.application.command.SubmitDocumentUseCase
import dev.wrkflw.application.query.FlowStatusService
import dev.wrkflw.application.query.FlowStatusUseCase
import dev.wrkflw.application.query.GroupWorkListService
import dev.wrkflw.application.query.GroupWorkListUseCase
import dev.wrkflw.application.query.MyTasksService
import dev.wrkflw.application.query.MyTasksUseCase
import dev.wrkflw.domain.port.AuditLog
import dev.wrkflw.domain.port.Clock
import dev.wrkflw.domain.port.DecisionRepository
import dev.wrkflw.domain.port.FlowDefinitionRepository
import dev.wrkflw.domain.port.FlowInstanceRepository
import dev.wrkflw.domain.port.OutboxEventRepository
import dev.wrkflw.domain.port.OutboxPublisher
import dev.wrkflw.domain.port.SystemClock
import dev.wrkflw.domain.port.TaskRepository
import dev.wrkflw.domain.port.WorkflowEngine
import dev.wrkflw.eventing.CloudEventsOutboxPublisher
import dev.wrkflw.persistence.AuditLogPostgres
import dev.wrkflw.persistence.DecisionRepositoryPostgres
import dev.wrkflw.persistence.FlowDefinitionRepositoryPostgres
import dev.wrkflw.persistence.FlowInstanceRepositoryPostgres
import dev.wrkflw.persistence.JooqDslContextProvider
import dev.wrkflw.persistence.OutboxEventRepositoryPostgres
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
    single<DecisionRepository> { DecisionRepositoryPostgres(get()) }
    single<WorkflowClient> { TemporalWorkerService.createClient(temporalHost, temporalPort) }
    single<WorkflowEngine> { TemporalWorkflowEngine(get(), taskQueue) }
    single<OutboxEventRepository> { OutboxEventRepositoryPostgres(get()) }
    single<OutboxPublisher> { CloudEventsOutboxPublisher { } }
    single<SubmitDocumentUseCase> { SubmitDocumentService(get(), get(), get(), get(), get(), get()) }
    single<ClaimTaskUseCase> { ClaimTaskService(get(), get(), get(), get()) }
    single<ReleaseTaskUseCase> { ReleaseTaskService(get(), get(), get(), get()) }
    single<SubmitDecisionUseCase> { SubmitDecisionService(get(), get(), get(), get(), get(), get(), get(), get()) }
    single<GroupWorkListUseCase> { GroupWorkListService(get()) }
    single<MyTasksUseCase> { MyTasksService(get()) }
    single<FlowStatusUseCase> { FlowStatusService(get(), get(), get()) }
}
