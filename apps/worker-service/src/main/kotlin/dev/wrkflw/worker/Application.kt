package dev.wrkflw.worker

import dev.wrkflw.temporal.TemporalWorkerService
import org.koin.core.context.startKoin
import org.postgresql.ds.PGSimpleDataSource
import java.util.concurrent.TimeUnit

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
        modules(workerModule(dataSource, temporalHost, temporalPort, taskQueue))
    }.koin

    val workerService = koin.get<TemporalWorkerService>()
    val factory = workerService.start()

    println("Worker service started. Press Ctrl+C to stop.")
    Runtime.getRuntime().addShutdownHook(Thread {
        factory.shutdown()
    })
    factory.awaitTermination(Long.MAX_VALUE, TimeUnit.MILLISECONDS)
}
