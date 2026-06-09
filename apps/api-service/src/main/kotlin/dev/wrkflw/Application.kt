package dev.wrkflw

import dev.wrkflw.domain.port.AuditLog
import dev.wrkflw.domain.port.Clock
import dev.wrkflw.domain.port.SystemClock
import dev.wrkflw.domain.port.WorkflowEngine
import dev.wrkflw.persistence.AuditLogPostgres
import dev.wrkflw.persistence.JooqDslContextProvider
import dev.wrkflw.temporal.TemporalWorkerService
import dev.wrkflw.temporal.TemporalWorkflowEngine
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import kotlinx.serialization.json.Json
import org.jooq.DSLContext
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import org.postgresql.ds.PGSimpleDataSource

fun main() {
    embeddedServer(Netty, port = 8080, module = Application::module).start(wait = true)
}

fun Application.module() {
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

    install(Koin) {
        modules(
            module {
                single<Clock> { SystemClock }
                single { dataSource }
                single<DSLContext> { JooqDslContextProvider(dataSource).create() }
                single<AuditLog> { AuditLogPostgres(get()) }
                single { TemporalWorkerService.createClient(temporalHost, temporalPort) }
                single<WorkflowEngine> { TemporalWorkflowEngine(get(), taskQueue) }
            }
        )
    }

    install(CallLogging)

    install(ContentNegotiation) {
        json(Json {
            ignoreUnknownKeys = true
            prettyPrint = false
        })
    }

    install(StatusPages) {
        exception<Throwable> { call, cause ->
            call.respond(
                HttpStatusCode.InternalServerError,
                mapOf("error" to (cause.message ?: "Internal server error"))
            )
        }
        status(HttpStatusCode.NotFound) { call, _ ->
            call.respond(HttpStatusCode.NotFound, mapOf("error" to "Not found"))
        }
        status(HttpStatusCode.MethodNotAllowed) { call, _ ->
            call.respond(HttpStatusCode.MethodNotAllowed, mapOf("error" to "Method not allowed"))
        }
    }

    // Routes wired during US1-US5 implementation
}
