package dev.wrkflw.worker

import dev.wrkflw.domain.port.AuditLog
import dev.wrkflw.domain.port.Clock
import dev.wrkflw.domain.port.SystemClock
import dev.wrkflw.domain.port.WorkflowEngine
import dev.wrkflw.persistence.AuditLogPostgres
import dev.wrkflw.persistence.JooqDslContextProvider
import dev.wrkflw.temporal.TemporalWorkerService
import dev.wrkflw.temporal.TemporalWorkflowEngine
import org.jooq.DSLContext
import org.koin.core.context.startKoin
import org.koin.dsl.module
import org.postgresql.ds.PGSimpleDataSource

fun main() {
    val dataSource = PGSimpleDataSource().apply {
        serverNames = arrayOf(System.getenv("DB_HOST") ?: "localhost")
        portNumbers = intArrayOf(System.getenv("DB_PORT")?.toIntOrNull() ?: 5432)
        databaseName = System.getenv("DB_NAME") ?: "wrkflw"
        user = System.getenv("DB_USER") ?: "wrkflw"
        password = System.getenv("DB_PASSWORD") ?: "wrkflw"
    }

    val temporalHost = System.getenv("TEMPORAL_HOST") ?: "localhost"
    val temporalPort = System.getenv("TEMPORAL_PORT")?.toIntOrNull() ?: 7233
    val taskQueue = "wrkflw-task-queue"

    val koin = startKoin {
        modules(
            module {
                single<Clock> { SystemClock }
                single { dataSource }
                single<DSLContext> { JooqDslContextProvider(dataSource).create() }
                single<AuditLog> { AuditLogPostgres(get()) }
                single { TemporalWorkerService.createClient(temporalHost, temporalPort) }
                single<WorkflowEngine> { TemporalWorkflowEngine(get(), taskQueue) }
                single { TemporalWorkerService(get(), taskQueue) }
            }
        )
    }.koin

    val workerService = koin.get<TemporalWorkerService>()
    val factory = workerService.start()

    println("Worker service started. Press Ctrl+C to stop.")
    Runtime.getRuntime().addShutdownHook(Thread {
        factory.shutdown()
    })
    factory.awaitTermination()
}
