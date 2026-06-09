package dev.wrkflw.worker

import dev.wrkflw.domain.port.AuditLog
import dev.wrkflw.domain.port.Clock
import dev.wrkflw.domain.port.SystemClock
import dev.wrkflw.domain.port.WorkflowEngine
import dev.wrkflw.persistence.AuditLogPostgres
import dev.wrkflw.persistence.JooqDslContextProvider
import dev.wrkflw.temporal.TemporalWorkerService
import dev.wrkflw.temporal.TemporalWorkflowEngine
import io.temporal.client.WorkflowClient
import org.jooq.DSLContext
import org.koin.dsl.module
import org.postgresql.ds.PGSimpleDataSource

fun workerModule(
    dataSource: PGSimpleDataSource,
    temporalHost: String,
    temporalPort: Int,
    taskQueue: String,
) = module {
    single<Clock> { SystemClock }
    single { dataSource }
    single<DSLContext> { JooqDslContextProvider(dataSource).create() }
    single<AuditLog> { AuditLogPostgres(get()) }
    single<WorkflowClient> { TemporalWorkerService.createClient(temporalHost, temporalPort) }
    single<WorkflowEngine> { TemporalWorkflowEngine(get(), taskQueue) }
    single { TemporalWorkerService(get(), taskQueue) }
}
